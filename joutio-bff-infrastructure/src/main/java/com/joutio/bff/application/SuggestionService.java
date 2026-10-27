package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.response.SuggestionView;
import com.joutio.identity.domain.model.UserAccountStatus;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.profile.domain.model.PlayerProgress;
import com.joutio.profile.domain.model.Profile;
import com.joutio.profile.domain.query.ProgressionQuery;
import com.joutio.profile.domain.query.ProfileQuery;
import com.joutio.theme.domain.model.TopicPage;
import com.joutio.theme.domain.model.TopicSort;
import com.joutio.theme.domain.query.TopicQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Suggestions de la palette ⌘K : sujets puis joueurs.
 */
@Service
public class SuggestionService {

    private final QueryGateway queryGateway;
    private final IdentityLookup identityLookup;

    public SuggestionService(QueryGateway queryGateway, IdentityLookup identityLookup) {
        this.queryGateway = queryGateway;
        this.identityLookup = identityLookup;
    }

    public CompletableFuture<List<SuggestionView>> suggest(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return CompletableFuture.completedFuture(List.of());
        }

        CompletableFuture<TopicPage> topicsFuture = queryGateway.query(
                new TopicQuery.GetTopicPageQuery(query, null, TopicSort.POPULAR, 0, limit),
                QueryResponseTypes.instanceOf(TopicPage.class));
        CompletableFuture<List<Profile>> playersFuture = queryGateway.query(
                new ProfileQuery.GetProfileSuggestionsQuery(query, limit),
                QueryResponseTypes.multipleInstancesOf(Profile.class));

        return CompletableFuture.allOf(topicsFuture, playersFuture).thenCompose(_ -> {
            List<Profile> players = playersFuture.join();
            List<String> playerIds = players.stream().map(Profile::userId).toList();
            CompletableFuture<List<PlayerProgress>> progressionsFuture = playerIds.isEmpty()
                    ? CompletableFuture.completedFuture(List.of())
                    : queryGateway.query(new ProgressionQuery.GetProgressionsByIdsQuery(playerIds),
                            QueryResponseTypes.multipleInstancesOf(PlayerProgress.class));
            CompletableFuture<Map<String, UserAccountStatus>> statusesFuture = identityLookup.statuses(playerIds)
                    .exceptionally(_ -> Map.<String, UserAccountStatus>of());

            return CompletableFuture.allOf(progressionsFuture, statusesFuture).thenApply(_ -> {
                List<PlayerProgress> progressions = progressionsFuture.join();
                Map<String, UserAccountStatus> statusById = statusesFuture.join();
                Map<String, PlayerProgress> progressById = progressions.stream()
                        .collect(Collectors.toMap(PlayerProgress::userId, Function.identity(), (first, _) -> first));
                List<SuggestionView> suggestions = new ArrayList<>();
                topicsFuture.join().topics().forEach(topic -> suggestions.add(new SuggestionView(
                        SuggestionView.Type.TOPIC,
                        topic.topicId(),
                        topic.name(),
                        topic.category() == null ? null : topic.category().label(),
                        topic.category() == null ? null : topic.category().name(),
                        topic.emoji(),
                        topic.color(),
                        topic.imageUrl(),
                        null,
                        topic.names())));
                players.forEach(profile -> suggestions.add(new SuggestionView(
                        SuggestionView.Type.PLAYER,
                        profile.userId(),
                        profile.pseudonym(),
                        "Elo " + progressById.getOrDefault(profile.userId(),
                                PlayerProgress.empty(profile.userId())).rating(),
                        null,
                        null,
                        null,
                        null,
                        profile.avatarOptions(),
                        null,
                        statusById.get(profile.userId()) != null
                                && statusById.get(profile.userId()).suspended(java.time.Instant.now()))));
                return List.copyOf(suggestions);
            });
        });
    }
}
