package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.infrastructure.in.api.response.PresenceView;
import com.joutio.profile.domain.event.PresenceEvent;
import com.joutio.profile.domain.model.PresenceStatus;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Diffuse les transitions de présence sur {@code /topic/presence/{userId}}. Le BFF étant la
 * seule surface STOMP, il consomme les événements de présence de {@code joutio-profile}.
 */
@Service
@ProcessingGroup("presence-notification")
public class PresenceNotificationPublisher {

    private static final Logger logger = LoggerFactory.getLogger(PresenceNotificationPublisher.class);
    private static final String DESTINATION_PREFIX = "/topic/presence/";

    private final SimpMessagingTemplate messagingTemplate;

    public PresenceNotificationPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @EventHandler
    public void onPlayerWentOnline(PresenceEvent.PlayerWentOnlineEvent event) {
        send(event.userId(), PresenceStatus.ONLINE, event.at());
    }

    @EventHandler
    public void onPlayerWentOffline(PresenceEvent.PlayerWentOfflineEvent event) {
        send(event.userId(), PresenceStatus.OFFLINE, event.lastSeenAt());
    }

    private void send(String userId, PresenceStatus status, java.time.Instant lastSeenAt) {
        logger.debug("Presence publiée: userId={}, status={}", userId, status);
        messagingTemplate.convertAndSend(
                DESTINATION_PREFIX + userId,
                new PresenceView(userId, status, lastSeenAt)
        );
    }
}
