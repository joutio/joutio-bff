package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.application.MediaService;
import com.joutio.bff.application.PushSubscriptionRepository;
import com.joutio.identity.domain.event.UserEvent.UserAccountDeletedEvent;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

/** Efface les abonnements push et les médias personnels d'un compte supprimé. */
@Component
@ProcessingGroup("account-deletion-bff")
public class AccountDeletionHandler {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final MediaService mediaService;

    public AccountDeletionHandler(PushSubscriptionRepository pushSubscriptionRepository,
                                  MediaService mediaService) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.mediaService = mediaService;
    }

    @EventHandler
    public void on(UserAccountDeletedEvent event) {
        String userId = event.userId();
        pushSubscriptionRepository.deleteByUserId(userId);
        mediaService.deleteAllForUser(userId);
    }
}
