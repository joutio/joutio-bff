package com.joutio.bff.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.request.SuspendUserRequest;
import com.joutio.identity.domain.command.UserCommand;
import com.joutio.identity.domain.model.AccountStatus;
import com.joutio.identity.domain.model.User;
import com.joutio.identity.domain.model.UserReportCount;
import com.joutio.identity.domain.model.UserSuspension;
import com.joutio.identity.domain.model.UserSuspensionReason;
import com.joutio.identity.domain.query.UserQuery;
import com.joutio.identity.domain.query.UserReportQuery;
import com.joutio.microservice.core.domain.constant.JoutioConstants;
import com.joutio.microservice.core.infrastructure.in.api.request.FilterRequest;
import com.joutio.microservice.core.infrastructure.in.api.response.SearchResponse;
import com.joutio.profile.domain.model.Profile;
import com.joutio.profile.domain.query.ProfileQuery;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserModerationAdminServiceTest {

    private final QueryGateway queryGateway = mock(QueryGateway.class);
    private final CommandGateway commandGateway = mock(CommandGateway.class);
    private final IdentityLookup identityLookup = mock(IdentityLookup.class);
    private final ProfileLookup profileLookup = mock(ProfileLookup.class);
    private final UserReportService userReportService = mock(UserReportService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final UserModerationAdminService service = new UserModerationAdminService(
            queryGateway, commandGateway, identityLookup, profileLookup, userReportService, objectMapper);

    @Test
    void catalogue_returns_enriched_players() {
        User user = activeUser();
        when(queryGateway.query(any(UserQuery.UserSearchQuery.class), any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(new SearchResponse<>(
                        List.of(user), 0, 20, 1, 1, List.of(), true, true, false)));
        when(profileLookup.getAll(List.of("user-2"))).thenReturn(CompletableFuture.completedFuture(
                List.of(Profile.builder()
                        .userId("user-2")
                        .email("user2@joutio.dev")
                        .pseudonym("Bravo")
                        .avatarOptions("{}")
                        .build())));
        when(queryGateway.query(any(UserReportQuery.GetUserReportCountsQuery.class), any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(List.of(
                        new UserReportCount("user-2", 1, 2, Instant.now()))));

        var result = service.catalogue(null, null, false, 0, 20).join();

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().pseudonym()).isEqualTo("Bravo");
        assertThat(result.content().getFirst().openReportCount()).isEqualTo(1);
        assertThat(result.content().getFirst().totalReportCount()).isEqualTo(2);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void catalogue_materializes_map_content_from_distributed_query() {
        Instant createdAt = Instant.parse("2026-10-18T12:00:00Z");
        SearchResponse raw = new SearchResponse(
                List.of(Map.of(
                        "userId", "user-2",
                        "email", "user2@joutio.dev",
                        "linkedSocialAccounts", List.of(),
                        "createdAt", createdAt.toString(),
                        "status", "ACTIVE")),
                0, 20, 1, 1, List.of(), true, true, false);
        when(queryGateway.query(any(UserQuery.UserSearchQuery.class), any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(raw));
        when(profileLookup.getAll(List.of("user-2"))).thenReturn(CompletableFuture.completedFuture(
                List.of(Profile.builder().userId("user-2").pseudonym("Bravo").build())));
        when(queryGateway.query(any(UserReportQuery.GetUserReportCountsQuery.class), any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        var result = service.catalogue(null, null, false, 0, 20).join();

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().userId()).isEqualTo("user-2");
        assertThat(result.content().getFirst().createdAt()).isEqualTo(createdAt);
        assertThat(result.content().getFirst().pseudonym()).isEqualTo("Bravo");
    }

    @Test
    void catalogue_maps_pseudonym_status_and_report_filters() {
        when(queryGateway.query(any(ProfileQuery.GetProfileSuggestionsQuery.class),
                any(ResponseType.class))).thenReturn(CompletableFuture.completedFuture(
                List.of(Profile.builder().userId("user-2").build())));
        when(queryGateway.query(any(UserQuery.UserSearchQuery.class), any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(new SearchResponse<>(
                        List.of(), 0, 20, 0, 0, List.of(), true, true, true)));

        service.catalogue("brav", AccountStatus.SUSPENDED, true, 0, 20).join();

        ArgumentCaptor<UserQuery.UserSearchQuery> captor =
                ArgumentCaptor.forClass(UserQuery.UserSearchQuery.class);
        verify(queryGateway).query(captor.capture(), any(ResponseType.class));
        var filters = captor.getValue().request().filters();
        assertThat(filters).extracting(FilterRequest::property)
                .containsExactly("userId", "status", "reported");
        assertThat(filters.getFirst().values()).containsExactly("user-2");
        assertThat(filters.get(1).value()).isEqualTo("SUSPENDED");
        assertThat(filters.get(2).value()).isEqualTo(true);
    }

    @Test
    void suspend_temporary_sendsCommandAndResolvesReports() {
        stubUser(activeUser());
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(userReportService.resolveOpenReports(eq("user-2"), eq("admin-1"), any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new com.joutio.bff.infrastructure.in.api.response.AdminUserReportResolutionView(2)));

        var result = service.suspend("user-2", "admin-1", new SuspendUserRequest(
                UserSuspensionReason.HARASSMENT, "internal", Duration.ofDays(7), false, true)).join();

        ArgumentCaptor<UserCommand.SuspendUserCommand> captor =
                ArgumentCaptor.forClass(UserCommand.SuspendUserCommand.class);
        org.mockito.Mockito.verify(commandGateway).send(captor.capture());
        UserCommand.SuspendUserCommand command = captor.getValue();
        assertThat(command.userId()).isEqualTo("user-2");
        assertThat(command.requestedBy()).isEqualTo("admin-1");
        assertThat(command.suspendedUntil()).isAfter(Instant.now().plus(Duration.ofDays(6)));
        assertThat(command.note()).isEqualTo("internal");
        assertThat(result.resolvedReports()).isEqualTo(2);
        assertThat(result.status()).isEqualTo(AccountStatus.SUSPENDED);
    }

    @Test
    void suspend_permanent_rejectsDuration() {
        stubUser(activeUser());

        assertThatThrownBy(() -> service.suspend("user-2", "admin-1", new SuspendUserRequest(
                UserSuspensionReason.SPAM, null, Duration.ofDays(1), true, false)).join())
                .hasCauseInstanceOf(BffProblems.InvalidSuspensionRequestProblem.class);
    }

    @Test
    void suspend_systemAccount_isRejected() {
        stubUser(new User(
                JoutioConstants.SYSTEM_USER_ID, "system@joutio.dev", Set.of(), Instant.now()));

        assertThatThrownBy(() -> service.suspend(JoutioConstants.SYSTEM_USER_ID, "admin-1",
                new SuspendUserRequest(UserSuspensionReason.OTHER, null, null, true, false)).join())
                .hasCauseInstanceOf(BffProblems.ProtectedAccountProblem.class);
    }

    @Test
    void suspend_alreadySuspended_isRejected() {
        Instant now = Instant.now();
        stubUser(new User(
                "user-2", "user2@joutio.dev", Set.of(), now, AccountStatus.SUSPENDED,
                new UserSuspension("s1", UserSuspensionReason.SPAM, null, "admin-0",
                        now, now.plusSeconds(3600), null, null, null, null)));

        assertThatThrownBy(() -> service.suspend("user-2", "admin-1",
                new SuspendUserRequest(UserSuspensionReason.HARASSMENT, null,
                        Duration.ofDays(1), false, false)).join())
                .hasCauseInstanceOf(BffProblems.UserAlreadySuspendedProblem.class);
    }

    private void stubUser(User user) {
        when(queryGateway.query(any(UserQuery.GetUserQuery.class),
                any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(user));
    }

    private static User activeUser() {
        return new User("user-2", "user2@joutio.dev", Set.of(), Instant.now());
    }
}
