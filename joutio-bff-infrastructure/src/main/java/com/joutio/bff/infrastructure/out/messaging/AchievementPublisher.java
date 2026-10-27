package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.infrastructure.in.api.response.AchievementView;
import com.joutio.profile.domain.event.ProgressionEvent;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Fan-out **éphémère** des **déblocages** du joueur : badge gagné et titre
 * thématique débloqué → poussés sur {@code /topic/achievements/{userId}} pour que le client
 * célèbre (popup + jingle) où qu'il soit — hors écran immersif, où la célébration est reportée.
 *
 * <p>Comme la présence éphémère ({@code follow-presence-notification}) : aucune persistance, pas
 * d'inbox, pas d'`EventEnvelopeResponse` — le client n'a rien à replier, seulement à fêter.
 * Le groupe est <b>instance-scopé</b> : chaque pod sert ses propres clients STOMP.</p>
 */
@Service
@ProcessingGroup("achievement-notification")
public class AchievementPublisher {

    private static final Logger logger = LoggerFactory.getLogger(AchievementPublisher.class);
    private static final String DESTINATION_PREFIX = "/topic/achievements/";

    private final SimpMessagingTemplate messagingTemplate;

    public AchievementPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @EventHandler
    public void onBadgeEarned(ProgressionEvent.BadgeEarnedEvent event) {
        publish(event.userId(), AchievementView.badge(
                event.badge().name(),
                event.badge().label(),
                event.badge().family().name(),
                event.badge().tier().name(),
                event.badge().metric().name(),
                event.badge().target(),
                event.earnedAt()));
    }

    @EventHandler
    public void onMasteryTierReached(ProgressionEvent.MasteryTierReachedEvent event) {
        publish(event.userId(), AchievementView.title(
                event.topicId(), event.tier().name(), event.reachedAt()));
    }

    private void publish(String userId, AchievementView view) {
        if (userId == null) {
            return;
        }
        messagingTemplate.convertAndSend(DESTINATION_PREFIX + userId, view);
        logger.debug("Déblocage diffusé: userId={}, kind={}", userId, view.kind());
    }
}
