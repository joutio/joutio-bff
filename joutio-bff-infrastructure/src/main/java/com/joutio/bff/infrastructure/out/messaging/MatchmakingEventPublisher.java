package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.application.ProfileLookup;
import com.joutio.bff.application.ProgressionLookup;
import com.joutio.bff.infrastructure.in.api.response.EventEnvelopeResponse;
import com.joutio.bff.infrastructure.in.api.response.RoomOpponentView;
import com.joutio.bff.infrastructure.out.messaging.mapper.MatchmakingEventResponseMapper;
import com.joutio.bff.infrastructure.out.messaging.response.MatchmakingEventResponse;
import com.joutio.matchmaking.domain.event.MatchmakingEvent;
import com.joutio.profile.domain.model.PlayerProgress;
import com.joutio.profile.domain.model.Profile;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.SequenceNumber;
import org.axonframework.eventhandling.Timestamp;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Diffuse les événements d'appariement public sur
 * {@code /topic/matchmaking/tickets/{ticketId}}.
 *
 * <p>{@code MATCHED} est enrichi du profil de l'adversaire (pseudo, avatar, Elo, pays) pour que
 * la carte « Adversaire trouvé » affiche la ligne complète sans attendre la lecture de la salle.
 * La salle reste la source de repli si le profil est momentanément indisponible.</p>
 */
@Service
@ProcessingGroup("matchmaking-events")
public class MatchmakingEventPublisher {

    private static final String DESTINATION_PREFIX = "/topic/matchmaking/tickets/";

    private final SimpMessagingTemplate messagingTemplate;
    private final ProfileLookup profileLookup;
    private final ProgressionLookup progressionLookup;

    public MatchmakingEventPublisher(SimpMessagingTemplate messagingTemplate,
                                            ProfileLookup profileLookup,
                                            ProgressionLookup progressionLookup) {
        this.messagingTemplate = messagingTemplate;
        this.profileLookup = profileLookup;
        this.progressionLookup = progressionLookup;
    }

    @EventHandler
    public void onMatchmakingEvent(MatchmakingEvent event, @SequenceNumber long sequenceNumber, @Timestamp Instant timestamp) {
        Optional<MatchmakingEventResponse> mapped = MatchmakingEventResponseMapper.toResponse(event);
        if (mapped.isEmpty()) {
            return;
        }
        MatchmakingEventResponse response = mapped.get();
        if (response instanceof MatchmakingEventResponse.MatchedEventResponse matched
                && matched.opponentId() != null
                && !matched.vsBot()) {
            enrich(matched).thenAccept(enriched -> publish(event.matchmakingId(), sequenceNumber, timestamp, enriched));
            return;
        }
        publish(event.matchmakingId(), sequenceNumber, timestamp, response);
    }

    /** Enrichit l’événement d’appariement du profil et de la progression de l'adversaire. */
    private CompletableFuture<MatchmakingEventResponse.MatchedEventResponse> enrich(
            MatchmakingEventResponse.MatchedEventResponse matched) {
        String opponentId = matched.opponentId();
        CompletableFuture<Profile> profileFuture = profileLookup.get(opponentId).exceptionally(_ -> null);
        CompletableFuture<PlayerProgress> progressFuture = progressionLookup.get(opponentId).exceptionally(_ -> null);
        return CompletableFuture.allOf(profileFuture, progressFuture).thenApply(_ -> {
            Profile profile = profileFuture.join();
            if (profile == null) {
                return matched;
            }
            PlayerProgress progress = progressFuture.join();
            RoomOpponentView opponent = new RoomOpponentView(
                    profile.userId(),
                    profile.pseudonym(),
                    profile.avatarOptions(),
                    false,
                    false,
                    progress == null ? null : progress.rating(),
                    profile.country());
            return new MatchmakingEventResponse.MatchedEventResponse(
                    matched.ticketId(), matched.roomId(), matched.opponentId(), matched.vsBot(), opponent);
        });
    }

    private void publish(String ticketId, long sequenceNumber, Instant timestamp, MatchmakingEventResponse response) {
        EventEnvelopeResponse envelope = EventEnvelopeResponse.of(
                ticketId,
                sequenceNumber,
                timestamp,
                response.type().name(),
                response);
        messagingTemplate.convertAndSend(DESTINATION_PREFIX + ticketId, envelope);
    }
}
