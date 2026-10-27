package com.joutio.bff.application;

import com.joutio.matchmaking.domain.command.MatchmakingCommand;
import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.profile.domain.model.PlayerProgress;
import com.joutio.profile.domain.model.Profile;
import com.joutio.theme.domain.model.QuestionLanguageCoverage;
import com.joutio.theme.domain.query.QuestionQuery;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Vérifie que la façade prépare la photo de couverture linguistique du sujet et la porte dans la
 * commande d'appariement (événement de démarrage auto-suffisant, aucune query dans la saga).
 */
class MatchmakingViewServiceTest {

    private final QueryGateway queryGateway = mock(QueryGateway.class);
    private final CommandGateway commandGateway = mock(CommandGateway.class);
    private final ProfileLookup profileLookup = mock(ProfileLookup.class);
    private final ProgressionLookup progressionLookup = mock(ProgressionLookup.class);

    private final MatchmakingViewService service =
            new MatchmakingViewService(queryGateway, commandGateway, profileLookup, progressionLookup);

    @Test
    void enqueue_carriesTopicLanguageCoverageFromTheme() {
        when(profileLookup.get("user-1")).thenReturn(CompletableFuture.completedFuture(profile()));
        when(progressionLookup.get("user-1")).thenReturn(CompletableFuture.completedFuture(progress()));
        when(queryGateway.query(any(QuestionQuery.GetTopicLanguageCoverageQuery.class), any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(List.of(
                        new QuestionLanguageCoverage(Set.of(Language.FR), 10),
                        new QuestionLanguageCoverage(Set.of(Language.EN), 8),
                        new QuestionLanguageCoverage(Set.of(Language.FR, Language.EN), 7))));
        when(commandGateway.send(any(MatchmakingCommand.CreateMatchmakingCommand.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        service.enqueue("user-1", "topic-1").join();

        ArgumentCaptor<MatchmakingCommand.CreateMatchmakingCommand> captor =
                ArgumentCaptor.forClass(MatchmakingCommand.CreateMatchmakingCommand.class);
        verify(commandGateway).send(captor.capture());
        MatchmakingCommand.CreateMatchmakingCommand command = captor.getValue();
        assertThat(command.playerId()).isEqualTo("user-1");
        assertThat(command.topicId()).isEqualTo("topic-1");
        assertThat(command.rating()).isEqualTo(842);
        assertThat(command.languages()).containsExactly(Language.FR);
        assertThat(command.topicCoverage().approvedCountFor(Set.of(Language.FR))).isEqualTo(10);
        assertThat(command.topicCoverage().approvedCountFor(Set.of(Language.EN))).isEqualTo(8);
        assertThat(command.topicCoverage().approvedCountFor(Set.of(Language.FR, Language.EN))).isEqualTo(7);
    }

    private static Profile profile() {
        return new Profile(
                "user-1",
                "user-1@joutio.test",
                "Alice",
                null,
                "FR",
                null,
                null,
                null,
                Language.FR,
                Instant.now(),
                Instant.now());
    }

    private static PlayerProgress progress() {
        return PlayerProgress.builder()
                .userId("user-1")
                .rating(842)
                .xpByTopic(Map.of())
                .badges(Set.of())
                .gamesPlayed(10)
                .wins(6)
                .losses(3)
                .draws(1)
                .bestScore(900)
                .currentWinStreak(2)
                .bestWinStreak(4)
                .updatedAt(Instant.now())
                .build();
    }
}
