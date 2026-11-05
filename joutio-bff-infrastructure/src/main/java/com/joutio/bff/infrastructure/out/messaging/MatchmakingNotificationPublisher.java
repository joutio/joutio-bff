package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.application.ProfileLookup;
import com.joutio.bff.application.ProgressionLookup;
import com.joutio.bff.infrastructure.in.api.response.EventEnvelopeResponse;
import com.joutio.bff.infrastructure.in.api.response.RoomOpponentView;
import com.joutio.bff.infrastructure.out.messaging.mapper.MatchmakingEventNotificationMapper;
import com.joutio.bff.infrastructure.out.messaging.response.MatchmakingNotification;
import com.joutio.matchmaking.domain.event.MatchmakingEvent;
import com.joutio.profile.domain.model.PlayerProgress;
import com.joutio.profile.domain.model.Profile;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Diffuse les notifications d'appariement public sur
 * {@code /topic/matchmaking/tickets/{ticketId}}.
 *
 * <p>{@code MATCHED} est enrichi du profil de l'adversaire (pseudo, avatar, Elo, pays) pour que
 * la carte « Adversaire trouvé » affiche la ligne complète sans attendre la lecture de la salle.
 * La salle reste la source de repli si le profil est momentanément indisponible.</p>
 */
@Service
@ProcessingGroup("matchmaking-notification")
public class MatchmakingNotificationPublisher {

    private static final Logger logger = LoggerFactory.getLogger(MatchmakingNotificationPublisher.class);
    private static final String DESTINATION_PREFIX = "/topic/matchmaking/tickets/";

    private final SimpMessagingTemplate messagingTemplate;
    private final ProfileLookup profileLookup;
    private final ProgressionLookup progressionLookup;

    public MatchmakingNotificationPublisher(SimpMessagingTemplate messagingTemplate,
                                            ProfileLookup profileLookup,
                                            ProgressionLookup progressionLookup) {
        this.messagingTemplate = messagingTemplate;
        this.profileLookup = profileLookup;
        this.progressionLookup = progressionLookup;
    }

    @EventHandler
    public void onMatchmakingEvent(EventMessage<?> eventMessage) {
        if (!(eventMessage.getPayload() instanceof MatchmakingEvent event)) {
            return;
        }
        if (!(eventMessage instanceof DomainEventMessage<?> domainMessage)) {
            logger.warn("Notification ignorée (sans métadonnées d'agrégat): {}", event.getClass().getSimpleName());
            return;
        }

        Optional<MatchmakingNotification> mapped = MatchmakingEventNotificationMapper.toNotification(event);
        if (mapped.isEmpty()) {
            return;
        }
        MatchmakingNotification notification = mapped.get();
        if (notification instanceof MatchmakingNotification.MatchedNotification matched
                && matched.opponentId() != null
                && !matched.vsBot()) {
            enrich(matched).thenAccept(enriched -> publish(event, domainMessage, enriched));
            return;
        }
        publish(event, domainMessage, notification);
    }

    /** Enrichit la notification d'appariement du profil et de la progression de l'adversaire. */
    private CompletableFuture<MatchmakingNotification.MatchedNotification> enrich(
            MatchmakingNotification.MatchedNotification matched) {
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
                    progress == null ? null : progress.rating(),
                    progress == null ? null : progress.provisional(),
                    profile.country());
            return new MatchmakingNotification.MatchedNotification(
                    matched.ticketId(), matched.roomId(), matched.opponentId(), matched.vsBot(), opponent);
        });
    }

    private void publish(MatchmakingEvent event,
                         DomainEventMessage<?> domainMessage,
                         MatchmakingNotification notification) {
        EventEnvelopeResponse envelope = EventEnvelopeResponse.of(
                event.matchmakingId(),
                domainMessage.getSequenceNumber(),
                domainMessage.getTimestamp(),
                notification.type().name(),
                notification);
        messagingTemplate.convertAndSend(DESTINATION_PREFIX + event.matchmakingId(), envelope);
    }
}
