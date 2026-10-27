package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.application.WebPushDispatcher;
import com.joutio.notification.domain.event.NotificationEvent;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

/**
 * Envoie les notifications créées en **Web Push** (notification OS, app fermée), en complément du
 * fan-out STOMP de {@link NotificationWsPublisher}.
 *
 * <p>Groupe <b>global</b> (`notification-push`, non suffixé par instance) : il doit rester un
 * processor partagé unique pour qu'un événement ne déclenche qu'<b>une seule</b> notification OS,
 * même avec plusieurs répliques BFF.</p>
 */
@Service
@ProcessingGroup("notification-push")
public class NotificationPushPublisher {

    private static final Logger logger = LoggerFactory.getLogger(NotificationPushPublisher.class);

    private final WebPushDispatcher webPushDispatcher;
    private final Executor webPushExecutor;

    public NotificationPushPublisher(WebPushDispatcher webPushDispatcher,
                                     @Qualifier("webPushExecutor") Executor webPushExecutor) {
        this.webPushDispatcher = webPushDispatcher;
        this.webPushExecutor = webPushExecutor;
    }

    /**
     * Web Push hors thread du tracking processor : l'envoi HTTP (potentiellement lent ou en échec)
     * ne doit jamais retarder l'avancement du token Kafka.
     */
    @EventHandler
    public void onNotificationCreated(NotificationEvent.NotificationCreatedEvent event) {
        try {
            webPushExecutor.execute(() -> webPushDispatcher.dispatch(event));
        } catch (RuntimeException _) {
            logger.warn("Web Push non planifié pour la notification {}", event.notificationId());
        }
    }
}
