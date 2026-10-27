package com.joutio.bff.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.request.ReinstateUserRequest;
import com.joutio.bff.infrastructure.in.api.request.SuspendUserRequest;
import com.joutio.bff.infrastructure.in.api.response.AdminReportedUserSummaryView;
import com.joutio.bff.infrastructure.in.api.response.AdminUserAccountView;
import com.joutio.bff.infrastructure.in.api.response.AdminUserCatalogueView;
import com.joutio.bff.infrastructure.in.api.response.AdminUserDetailView;
import com.joutio.bff.infrastructure.in.api.response.AdminUserModerationSummaryView;
import com.joutio.bff.infrastructure.in.api.response.AdminUserModerationView;
import com.joutio.bff.infrastructure.in.api.response.AdminUserProfileView;
import com.joutio.bff.infrastructure.in.api.response.AdminUserSearchResultView;
import com.joutio.bff.infrastructure.in.api.response.AdminUserReportResolutionView;
import com.joutio.bff.infrastructure.in.api.response.PageResponse;
import com.joutio.identity.domain.command.UserCommand;
import com.joutio.identity.domain.model.AccountStatus;
import com.joutio.identity.domain.model.SuspensionReinstatementReason;
import com.joutio.identity.domain.model.User;
import com.joutio.identity.domain.model.UserReportCount;
import com.joutio.identity.domain.model.UserReportSort;
import com.joutio.identity.domain.model.UserReportStats;
import com.joutio.identity.domain.model.UserReportSummary;
import com.joutio.identity.domain.model.UserReportSummaryFilter;
import com.joutio.identity.domain.model.UserReportSummaryPage;
import com.joutio.identity.domain.model.UserSuspension;
import com.joutio.identity.domain.query.UserQuery;
import com.joutio.identity.domain.query.UserReportQuery;
import com.joutio.microservice.core.domain.constant.JoutioConstants;
import com.joutio.microservice.core.domain.model.search.FilterOperator;
import com.joutio.microservice.core.domain.model.search.SortDirection;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.microservice.core.infrastructure.in.api.request.FilterRequest;
import com.joutio.microservice.core.infrastructure.in.api.request.PageRequest;
import com.joutio.microservice.core.infrastructure.in.api.request.SearchRequest;
import com.joutio.microservice.core.infrastructure.in.api.request.SortRequest;
import com.joutio.microservice.core.infrastructure.in.api.response.SearchResponse;
import com.joutio.profile.domain.model.Profile;
import com.joutio.profile.domain.query.ProfileQuery;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Modération des comptes : file de signalements, recherche, dossier, suspension et réintégration.
 */
@Service
public class UserModerationAdminService {

    private static final int MAX_SEARCH = 20;
    private static final int MAX_CATALOGUE_SEARCH = 200;
    private static final Duration MAX_SUSPENSION = Duration.ofDays(3650);

    private final QueryGateway queryGateway;
    private final CommandGateway commandGateway;
    private final IdentityLookup identityLookup;
    private final ProfileLookup profileLookup;
    private final UserReportService userReportService;
    private final ObjectMapper objectMapper;

    public UserModerationAdminService(QueryGateway queryGateway,
                                      CommandGateway commandGateway,
                                      IdentityLookup identityLookup,
                                      ProfileLookup profileLookup,
                                      UserReportService userReportService,
                                      ObjectMapper objectMapper) {
        this.queryGateway = queryGateway;
        this.commandGateway = commandGateway;
        this.identityLookup = identityLookup;
        this.profileLookup = profileLookup;
        this.userReportService = userReportService;
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<PageResponse<AdminReportedUserSummaryView>> queue(UserReportSummaryFilter filter,
                                                                               UserReportSort sort,
                                                                               int page,
                                                                               int size) {
        return queryGateway.query(
                        new UserReportQuery.GetReportedUserPageQuery(filter, sort, page, size),
                        QueryResponseTypes.instanceOf(UserReportSummaryPage.class))
                .thenCompose(pageResult -> enrichQueue(pageResult, page, size));
    }

    public CompletableFuture<AdminUserModerationSummaryView> summary() {
        return queryGateway.query(
                        new UserReportQuery.GetUserReportStatsQuery(),
                        QueryResponseTypes.instanceOf(UserReportStats.class))
                .thenApply(stats -> new AdminUserModerationSummaryView(
                        stats.openReports(), stats.reportedUsers(), stats.suspendedUsers()));
    }

    public CompletableFuture<AdminUserDetailView> detail(String userId) {
        CompletableFuture<User> userFuture = requireUser(userId);
        CompletableFuture<Profile> profileFuture = profileLookup.get(userId).exceptionally(_ -> null);
        CompletableFuture<List<com.joutio.bff.infrastructure.in.api.response.AdminUserReportView>> reportsFuture =
                userReportService.adminReports(userId);
        return CompletableFuture.allOf(userFuture, profileFuture, reportsFuture)
                .thenApply(_ -> new AdminUserDetailView(
                        accountView(userFuture.join()),
                        profileView(profileFuture.join()),
                        reportsFuture.join()));
    }

    /**
     * Catalogue paginé des joueurs. La recherche libre suit la même résolution que la recherche
     * admin : identifiant exact (UUID), email (présence de {@code @}) ou pseudonyme via le
     * read model de {@code joutio-profile}.
     */
    public CompletableFuture<PageResponse<AdminUserCatalogueView>> catalogue(String query,
                                                                              AccountStatus status,
                                                                              boolean reportedOnly,
                                                                              int page,
                                                                              int size) {
        String term = normalizeSearch(query);
        return pseudonymMatches(term).thenCompose(pseudonymIds -> {
            List<FilterRequest> filters = new ArrayList<>();
            if (term != null) {
                if (looksLikeUuid(term)) {
                    filters.add(new FilterRequest("userId", FilterOperator.EQUALS, term, null, null));
                } else if (term.contains("@")) {
                    filters.add(new FilterRequest("email", FilterOperator.CONTAINS,
                            term.toLowerCase(Locale.ROOT), null, null));
                } else {
                    if (pseudonymIds.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                PageResponse.of(List.of(), page, size, 0));
                    }
                    filters.add(new FilterRequest("userId", FilterOperator.IN,
                            null, null, new ArrayList<>(pseudonymIds)));
                }
            }
            if (status != null) {
                filters.add(new FilterRequest("status", FilterOperator.EQUALS,
                        status.name(), null, null));
            }
            if (reportedOnly) {
                filters.add(new FilterRequest("reported", FilterOperator.EQUALS, true, null, null));
            }
            SearchRequest request = new SearchRequest(
                    filters,
                    List.of(
                            new SortRequest("createdAt", SortDirection.DESC),
                            new SortRequest("userId", SortDirection.ASC)),
                    new PageRequest(page, size));
            return queryGateway.query(
                            new UserQuery.UserSearchQuery(request),
                            QueryResponseTypes.searchResponseOf(User.class))
                    .thenCompose(result -> enrichCatalogue(toUserSearchResponse(result), page, size));
        });
    }

    public CompletableFuture<List<AdminUserSearchResultView>> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return CompletableFuture.completedFuture(List.of());
        }
        String term = query.trim();
        int safeLimit = Math.min(Math.max(limit, 1), MAX_SEARCH);

        if (term.contains("@")) {
            return queryGateway.query(new UserQuery.FindUserByEmailQuery(term.toLowerCase()),
                            QueryResponseTypes.optionalInstanceOf(User.class))
                    .thenCompose(user -> user.map(value -> toSearchResults(List.of(value), safeLimit))
                            .orElseGet(() -> CompletableFuture.completedFuture(List.of())));
        }
        if (looksLikeUuid(term)) {
            return queryGateway.query(new UserQuery.FindUserQuery(term),
                            QueryResponseTypes.optionalInstanceOf(User.class))
                    .thenCompose(user -> user.map(value -> toSearchResults(List.of(value), safeLimit))
                            .orElseGet(() -> CompletableFuture.completedFuture(List.of())));
        }
        return queryGateway.query(new ProfileQuery.GetProfileSuggestionsQuery(term, safeLimit),
                        QueryResponseTypes.multipleInstancesOf(Profile.class))
                .thenCompose(profiles -> {
                    List<String> ids = profiles.stream().map(Profile::userId).distinct().toList();
                    if (ids.isEmpty()) {
                        return CompletableFuture.completedFuture(List.of());
                    }
                    return queryGateway.query(new UserQuery.GetUsersByIdsQuery(ids),
                                    QueryResponseTypes.multipleInstancesOf(User.class))
                            .thenApply(users -> {
                                Map<String, Profile> profileById = profiles.stream()
                                        .collect(Collectors.toMap(Profile::userId, p -> p, (a, _) -> a));
                                return users.stream()
                                        .sorted(Comparator.comparing(
                                                user -> {
                                                    Profile profile = profileById.get(user.userId());
                                                    return profile == null || profile.pseudonym() == null
                                                            ? user.email()
                                                            : profile.pseudonym();
                                                },
                                                String.CASE_INSENSITIVE_ORDER))
                                        .limit(safeLimit)
                                        .map(user -> searchResult(user, profileById.get(user.userId())))
                                        .toList();
                            });
                });
    }

    public CompletableFuture<AdminUserModerationView> suspend(String userId,
                                                              String adminId,
                                                              SuspendUserRequest request) {
        return requireUser(userId).thenCompose(user -> {
            requireNotProtected(user);
            Instant now = Instant.now();
            if (user.suspended(now)) {
                throw new BffProblems.UserAlreadySuspendedProblem(userId);
            }
            Instant until = suspensionEnd(now, request);
            return commandGateway.send(new UserCommand.SuspendUserCommand(
                            userId,
                            request.reason(),
                            normalizeNote(request.note()),
                            adminId,
                            until,
                            now))
                    .thenCompose(_ -> {
                        if (!request.resolveOpenReports()) {
                            return CompletableFuture.completedFuture(0);
                        }
                        return userReportService.resolveOpenReports(userId, adminId, "Suspension du compte")
                                .thenApply(AdminUserReportResolutionView::resolvedCount)
                                .exceptionally(_ -> 0);
                    })
                    .thenApply(resolved -> new AdminUserModerationView(
                            userId,
                            AccountStatus.SUSPENDED,
                            until,
                            request.reason(),
                            resolved,
                            false));
        });
    }

    public CompletableFuture<AdminUserModerationView> reinstate(String userId,
                                                                String adminId,
                                                                ReinstateUserRequest request) {
        return requireUser(userId).thenCompose(user -> {
            requireNotProtected(user);
            return commandGateway.send(new UserCommand.ReinstateUserCommand(
                            userId,
                            SuspensionReinstatementReason.MANUAL,
                            request == null ? null : normalizeNote(request.note()),
                            adminId,
                            Instant.now()))
                    .thenApply(_ -> new AdminUserModerationView(
                            userId,
                            AccountStatus.ACTIVE,
                            null,
                            null,
                            0,
                            false));
        });
    }

    /**
     * Le query bus distribué transporte {@link SearchResponse} avec un contenu brut
     * ({@code LinkedHashMap}) : le type générique est perdu par le descripteur de transport.
     * On re-matérialise les éléments avant l'enrichissement.
     */
    @SuppressWarnings("unchecked")
    private SearchResponse<User> toUserSearchResponse(SearchResponse<?> source) {
        List<User> content = source.content().stream()
                .map(element -> element instanceof User user
                        ? user
                        : objectMapper.convertValue(element, User.class))
                .toList();
        return new SearchResponse<>(
                content,
                source.pageNumber(),
                source.pageSize(),
                source.totalElements(),
                source.totalPages(),
                source.sorts(),
                source.first(),
                source.last(),
                source.empty());
    }

    private CompletableFuture<List<String>> pseudonymMatches(String term) {
        if (term == null || term.contains("@") || looksLikeUuid(term)) {
            return CompletableFuture.completedFuture(List.of());
        }
        return queryGateway.query(
                        new ProfileQuery.GetProfileSuggestionsQuery(term, MAX_CATALOGUE_SEARCH),
                        QueryResponseTypes.multipleInstancesOf(Profile.class))
                .thenApply(profiles -> profiles.stream()
                        .map(Profile::userId)
                        .distinct()
                        .toList());
    }

    private CompletableFuture<PageResponse<AdminUserCatalogueView>> enrichCatalogue(
            SearchResponse<User> pageResult, int page, int size) {
        List<User> users = pageResult.content();
        if (users.isEmpty()) {
            return CompletableFuture.completedFuture(
                    PageResponse.of(List.of(), page, size, pageResult.totalElements()));
        }
        List<String> ids = users.stream().map(User::userId).toList();
        CompletableFuture<List<Profile>> profilesFuture = profileLookup.getAll(ids)
                .exceptionally(_ -> List.of());
        CompletableFuture<List<UserReportCount>> countsFuture = queryGateway.query(
                        new UserReportQuery.GetUserReportCountsQuery(ids),
                        QueryResponseTypes.multipleInstancesOf(UserReportCount.class))
                .exceptionally(_ -> List.of());

        return CompletableFuture.allOf(profilesFuture, countsFuture).thenApply(_ -> {
            Map<String, Profile> profileById = profilesFuture.join().stream()
                    .collect(Collectors.toMap(Profile::userId, profile -> profile, (a, _) -> a));
            Map<String, UserReportCount> countById = countsFuture.join().stream()
                    .collect(Collectors.toMap(UserReportCount::targetUserId, count -> count, (a, _) -> a));
            Instant now = Instant.now();
            List<AdminUserCatalogueView> content = users.stream()
                    .map(user -> {
                        Profile profile = profileById.get(user.userId());
                        UserReportCount count = countById.get(user.userId());
                        UserSuspension suspension = user.suspension();
                        return new AdminUserCatalogueView(
                                user.userId(),
                                user.email(),
                                profile == null ? null : profile.pseudonym(),
                                profile == null ? null : profile.avatarOptions(),
                                user.status(),
                                user.suspended(now),
                                suspension == null ? null : suspension.suspendedUntil(),
                                user.createdAt(),
                                isProtected(user.userId()),
                                count == null ? 0 : count.openCount(),
                                count == null ? 0 : count.totalCount(),
                                count == null ? null : count.lastReportedAt());
                    })
                    .toList();
            return PageResponse.of(content, page, size, pageResult.totalElements());
        });
    }

    private CompletableFuture<PageResponse<AdminReportedUserSummaryView>> enrichQueue(
            UserReportSummaryPage pageResult, int page, int size) {
        List<UserReportSummary> rows = pageResult.content();
        if (rows.isEmpty()) {
            return CompletableFuture.completedFuture(PageResponse.of(List.of(), page, size, pageResult.totalElements()));
        }
        List<String> ids = rows.stream().map(UserReportSummary::targetUserId).toList();
        CompletableFuture<List<User>> usersFuture = queryGateway.query(
                new UserQuery.GetUsersByIdsQuery(ids),
                QueryResponseTypes.multipleInstancesOf(User.class));
        CompletableFuture<List<Profile>> profilesFuture = profileLookup.getAll(ids);

        return CompletableFuture.allOf(usersFuture, profilesFuture).thenApply(_ -> {
            Map<String, User> userById = usersFuture.join().stream()
                    .collect(Collectors.toMap(User::userId, u -> u, (a, _) -> a));
            Map<String, Profile> profileById = profilesFuture.join().stream()
                    .collect(Collectors.toMap(Profile::userId, p -> p, (a, _) -> a));
            Instant now = Instant.now();
            List<AdminReportedUserSummaryView> content = rows.stream()
                    .map(row -> {
                        User user = userById.get(row.targetUserId());
                        Profile profile = profileById.get(row.targetUserId());
                        UserSuspension suspension = user == null ? null : user.suspension();
                        return new AdminReportedUserSummaryView(
                                row.targetUserId(),
                                user == null ? null : user.email(),
                                profile == null ? null : profile.pseudonym(),
                                profile == null ? null : profile.avatarOptions(),
                                user != null && user.suspended(now),
                                suspension == null ? null : suspension.suspendedUntil(),
                                row.openCount(),
                                row.totalCount(),
                                row.lastReportedAt());
                    })
                    .toList();
            return PageResponse.of(content, page, size, pageResult.totalElements());
        });
    }

    private CompletableFuture<List<AdminUserSearchResultView>> toSearchResults(List<User> users, int limit) {
        List<String> ids = users.stream().map(User::userId).toList();
        return profileLookup.getAll(ids).thenApply(profiles -> {
            Map<String, Profile> profileById = profiles.stream()
                    .collect(Collectors.toMap(Profile::userId, p -> p, (a, _) -> a));
            return users.stream()
                    .limit(limit)
                    .map(user -> searchResult(user, profileById.get(user.userId())))
                    .toList();
        });
    }

    private AdminUserSearchResultView searchResult(User user, Profile profile) {
        Instant now = Instant.now();
        return new AdminUserSearchResultView(
                user.userId(),
                user.email(),
                profile == null ? null : profile.pseudonym(),
                profile == null ? null : profile.avatarOptions(),
                user.status(),
                user.suspended(now),
                user.suspension() == null ? null : user.suspension().suspendedUntil(),
                isProtected(user.userId()));
    }

    private CompletableFuture<User> requireUser(String userId) {
        return queryGateway.query(
                new UserQuery.GetUserQuery(userId),
                QueryResponseTypes.instanceOf(User.class));
    }

    private static void requireNotProtected(User user) {
        if (JoutioConstants.SYSTEM_USER_ID.equals(user.userId())) {
            throw new BffProblems.ProtectedAccountProblem(user.userId());
        }
    }

    private static Instant suspensionEnd(Instant now, SuspendUserRequest request) {
        if (request.permanent()) {
            if (request.duration() != null) {
                throw new BffProblems.InvalidSuspensionRequestProblem(
                        "Une suspension définitive ne doit pas porter de durée");
            }
            return null;
        }
        Duration duration = request.duration();
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new BffProblems.InvalidSuspensionRequestProblem(
                    "Une suspension temporaire doit porter une durée strictement positive");
        }
        if (duration.compareTo(MAX_SUSPENSION) > 0) {
            throw new BffProblems.InvalidSuspensionRequestProblem(
                    "La durée maximale d'une suspension est de 3650 jours");
        }
        return now.plus(duration);
    }

    private static AdminUserAccountView accountView(User user) {
        UserSuspension suspension = user.suspension();
        Instant now = Instant.now();
        return new AdminUserAccountView(
                user.userId(),
                user.email(),
                user.createdAt(),
                user.status(),
                user.suspended(now),
                suspension == null ? null : suspension.suspendedUntil(),
                suspension == null ? null : suspension.reason(),
                suspension == null ? null : suspension.note(),
                suspension == null ? null : suspension.suspendedAt(),
                suspension == null ? null : suspension.suspendedBy(),
                suspension == null ? null : suspension.reinstatedAt(),
                suspension == null ? null : suspension.reinstatedBy(),
                suspension == null ? null : suspension.reinstatementReason(),
                suspension == null ? null : suspension.reinstatementNote(),
                JoutioConstants.SYSTEM_USER_ID.equals(user.userId()));
    }

    private static AdminUserProfileView profileView(Profile profile) {
        if (profile == null) {
            return null;
        }
        return new AdminUserProfileView(
                profile.pseudonym(),
                profile.bio(),
                profile.country(),
                profile.avatarOptions(),
                profile.bannerUrl());
    }

    private static String normalizeNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }

    private static String normalizeSearch(String query) {
        return query == null || query.isBlank() ? null : query.trim();
    }

    private static boolean isProtected(String userId) {
        return JoutioConstants.SYSTEM_USER_ID.equals(userId);
    }

    private static boolean looksLikeUuid(String value) {
        return value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }
}
