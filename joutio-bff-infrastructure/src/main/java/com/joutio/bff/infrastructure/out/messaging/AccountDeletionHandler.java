package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.application.FollowLookup;
import com.joutio.bff.application.IdentityLookup;
import com.joutio.bff.application.MediaService;
import com.joutio.bff.application.ProfileLookup;
import com.joutio.bff.application.ProgressionLookup;
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
    private final ProfileLookup profileLookup;
    private final ProgressionLookup progressionLookup;
    private final FollowLookup followLookup;
    private final IdentityLookup identityLookup;

    public AccountDeletionHandler(PushSubscriptionRepository pushSubscriptionRepository,
                                  MediaService mediaService,
                                  ProfileLookup profileLookup,
                                  ProgressionLookup progressionLookup,
                                  FollowLookup followLookup,
                                  IdentityLookup identityLookup) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.mediaService = mediaService;
        this.profileLookup = profileLookup;
        this.progressionLookup = progressionLookup;
        this.followLookup = followLookup;
        this.identityLookup = identityLookup;
    }

    @EventHandler
    public void on(UserAccountDeletedEvent event) {
        String userId = event.userId();
        pushSubscriptionRepository.deleteByUserId(userId);
        mediaService.deleteAllForUser(userId);
        profileLookup.invalidate(userId);
        progressionLookup.invalidate(userId);
        followLookup.invalidateAllCounts();
        identityLookup.invalidate(userId);
    }
}
