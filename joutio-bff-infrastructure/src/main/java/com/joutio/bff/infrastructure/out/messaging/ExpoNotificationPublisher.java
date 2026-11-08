package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.application.ExpoPushService;
import com.joutio.bff.application.ExpoPushStore;
import com.joutio.notification.domain.event.NotificationEvent;
import com.joutio.identity.domain.event.UserEvent;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.DisallowReplay;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

/** Groupe global existant : persistance seule ici, HTTP dans le worker. */
@Component
@ProcessingGroup("notification-push")
@DisallowReplay
public class ExpoNotificationPublisher {
    private final ExpoPushService service;
    private final ExpoPushStore store;
    public ExpoNotificationPublisher(ExpoPushService service, ExpoPushStore store) {
        this.service=service; this.store=store;
    }
    @EventHandler public void on(NotificationEvent.NotificationCreatedEvent event) { service.enqueue(event); }
    @EventHandler public void on(NotificationEvent.NotificationDeletedEvent event) { store.cancel(event.notificationId()); }
    @EventHandler public void on(UserEvent.UserAccountDeletedEvent event) { store.deleteUser(event.userId()); }
    @EventHandler public void on(UserEvent.UserSuspendedEvent event) { store.deleteUser(event.userId()); }
}
