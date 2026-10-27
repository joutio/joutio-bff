package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.response.MatchmakingTicketView;
import com.joutio.matchmaking.domain.command.MatchmakingCommand;
import com.joutio.matchmaking.domain.model.Matchmaking;
import com.joutio.matchmaking.domain.model.MatchmakingStatus;
import com.joutio.matchmaking.domain.model.TopicLanguageCoverage;
import com.joutio.matchmaking.domain.query.MatchmakingQuery;
import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.profile.domain.model.PlayerProgress;
import com.joutio.profile.domain.model.Profile;
import com.joutio.theme.domain.model.QuestionLanguageCoverage;
import com.joutio.theme.domain.query.QuestionQuery;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Écritures et lecture de la recherche d'appariement public (« Défier le monde »).
 */
@Service
public class MatchmakingViewService {

    private final QueryGateway queryGateway;
    private final CommandGateway commandGateway;
    private final ProfileLookup profileLookup;
    private final ProgressionLookup progressionLookup;

    public MatchmakingViewService(QueryGateway queryGateway,
                                  CommandGateway commandGateway,
                                  ProfileLookup profileLookup,
                                  ProgressionLookup progressionLookup) {
        this.queryGateway = queryGateway;
        this.commandGateway = commandGateway;
        this.profileLookup = profileLookup;
        this.progressionLookup = progressionLookup;
    }

    /**
     * Entre en recherche : résout Elo/langue du joueur et <b>photo de couverture linguistique</b>
     * du sujet (query theme), puis crée l'agrégat. La photo voyage dans l'événement de démarrage :
     * la saga compare localement, sans query bloquante (contrat d'auto-suffisance des événements).
     */
    public CompletableFuture<MatchmakingTicketView> enqueue(String playerId, String topicId) {
        CompletableFuture<Profile> profileFuture = profileLookup.get(playerId);
        CompletableFuture<PlayerProgress> progressFuture = progressionLookup.get(playerId);
        CompletableFuture<List<QuestionLanguageCoverage>> coverageFuture = queryGateway
                .query(new QuestionQuery.GetTopicLanguageCoverageQuery(topicId),
                        QueryResponseTypes.multipleInstancesOf(QuestionLanguageCoverage.class));

        return profileFuture.thenCombine(progressFuture, (profile, progress) -> {
            String matchmakingId = UUID.randomUUID().toString();
            Set<Language> languages = profile.language() == null
                    ? Set.of()
                    : Set.of(profile.language());
            return new PendingCommand(matchmakingId, topicId, ratingOf(progress), languages);
        }).thenCombine(coverageFuture, (pending, coverage) ->
                new PreparedCommand(pending, toTopicCoverage(coverage))
        ).thenCompose(prepared -> commandGateway
                .send(new MatchmakingCommand.CreateMatchmakingCommand(
                        prepared.pending().matchmakingId(),
                        playerId,
                        prepared.pending().topicId(),
                        prepared.pending().rating(),
                        prepared.pending().languages(),
                        prepared.topicCoverage()))
                .thenApply(_ -> new MatchmakingTicketView(
                        prepared.pending().matchmakingId(),
                        prepared.pending().topicId(),
                        MatchmakingStatus.SEARCHING,
                        null,
                        null,
                        false,
                        Instant.now(),
                        Instant.now())));
    }

    public CompletableFuture<MatchmakingTicketView> get(String ticketId) {
        return queryGateway
                .query(new MatchmakingQuery.GetMatchmakingByIdQuery(ticketId),
                        QueryResponseTypes.instanceOf(Matchmaking.class))
                .thenApply(MatchmakingViewService::toView);
    }

    public CompletableFuture<Void> cancel(String playerId, String ticketId) {
        // Pré-vérification : un ticket purgé (état terminal + rétention) doit répondre 404,
        // pas une AggregateDeletedException non mappée.
        return queryGateway
                .query(new MatchmakingQuery.GetMatchmakingByIdQuery(ticketId),
                        QueryResponseTypes.instanceOf(Matchmaking.class))
                .thenCompose(_ -> commandGateway
                        .send(new MatchmakingCommand.CancelMatchmakingCommand(ticketId, playerId))
                        .thenAccept(_ -> {
                        }));
    }

    private static int ratingOf(PlayerProgress progress) {
        return progress == null ? 200 : Math.max(0, progress.rating());
    }

    /** Mapping theme → modèle local matchmaking (un {@code *-domain} ne dépend jamais d'un autre service). */
    private static TopicLanguageCoverage toTopicCoverage(List<QuestionLanguageCoverage> coverage) {
        return new TopicLanguageCoverage(coverage.stream()
                .map(entry -> new TopicLanguageCoverage.LanguageCoverage(
                        entry.languages(), entry.approvedCount()))
                .toList());
    }

    private static MatchmakingTicketView toView(Matchmaking matchmaking) {
        return new MatchmakingTicketView(
                matchmaking.matchmakingId(),
                matchmaking.topicId(),
                matchmaking.status(),
                matchmaking.roomId(),
                matchmaking.opponentId(),
                matchmaking.vsBot(),
                matchmaking.createdAt(),
                matchmaking.updatedAt());
    }

    private record PendingCommand(String matchmakingId, String topicId, int rating, Set<Language> languages) {
    }

    private record PreparedCommand(PendingCommand pending, TopicLanguageCoverage topicCoverage) {
    }
}
