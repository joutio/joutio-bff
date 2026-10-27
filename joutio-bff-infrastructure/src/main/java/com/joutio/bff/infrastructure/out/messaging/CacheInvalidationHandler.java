package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.application.FollowLookup;
import com.joutio.bff.application.IdentityLookup;
import com.joutio.bff.application.ProfileLookup;
import com.joutio.bff.application.ProgressionLookup;
import com.joutio.bff.application.RewardLookup;
import com.joutio.identity.domain.event.UserEvent;
import com.joutio.identity.domain.event.UserEvent.UserAccountDeletedEvent;
import com.joutio.profile.domain.event.ProfileEvent;
import com.joutio.profile.domain.event.RewardEvent;
import com.joutio.social.domain.event.UserFollowerEvent;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Invalidation des caches de lecture sur <b>toutes</b> les répliques BFF : un follow/unfollow ou
 * une édition de profil n'invalide que la réplique qui a traité la commande, les autres
 * serviraient sinon une valeur périmée jusqu'au TTL (30 s pour les compteurs de follows).
 * <p>
 * Le groupe est <b>instance-scopé</b> (cf. {@code joutio.axon.instance-scoped-groups}) : chaque pod
 * consomme tous les événements et purge son propre cache Caffeine. Les lectures qui suivent sont
 * protégées par la fenêtre no-cache des lookups (la projection peut être en retard de quelques
 * millisecondes sur l'événement).
 */
@Service
@ProcessingGroup("cache-invalidation")
public class CacheInvalidationHandler {

    private static final Logger logger = LoggerFactory.getLogger(CacheInvalidationHandler.class);

    private final FollowLookup followLookup;
    private final ProfileLookup profileLookup;
    private final IdentityLookup identityLookup;
    private final ProgressionLookup progressionLookup;
    private final RewardLookup rewardLookup;

    public CacheInvalidationHandler(FollowLookup followLookup,
                                    ProfileLookup profileLookup,
                                    IdentityLookup identityLookup,
                                    ProgressionLookup progressionLookup,
                                    RewardLookup rewardLookup) {
        this.followLookup = followLookup;
        this.profileLookup = profileLookup;
        this.identityLookup = identityLookup;
        this.progressionLookup = progressionLookup;
        this.rewardLookup = rewardLookup;
    }

    @EventHandler
    public void on(UserFollowerEvent.UserFollowedEvent event) {
        invalidateCounts(event);
    }

    @EventHandler
    public void on(UserFollowerEvent.UserUnfollowedEvent event) {
        invalidateCounts(event);
    }

    @EventHandler
    public void on(ProfileEvent.ProfilePseudonymUpdatedEvent event) {
        invalidateProfile(event.userId());
    }

    @EventHandler
    public void on(ProfileEvent.ProfileBioUpdatedEvent event) {
        invalidateProfile(event.userId());
    }

    @EventHandler
    public void on(ProfileEvent.ProfileCountryUpdatedEvent event) {
        invalidateProfile(event.userId());
    }

    @EventHandler
    public void on(ProfileEvent.ProfileAvatarUpdatedEvent event) {
        invalidateProfile(event.userId());
    }

    @EventHandler
    public void on(ProfileEvent.ProfileBannerUpdatedEvent event) {
        invalidateProfile(event.userId());
    }

    @EventHandler
    public void on(ProfileEvent.ProfileLanguageUpdatedEvent event) {
        invalidateProfile(event.userId());
    }

    @EventHandler
    public void on(ProfileEvent.ProfileInterestsUpdatedEvent event) {
        invalidateProfile(event.userId());
    }

    @EventHandler
    public void on(ProfileEvent.ProfileEquippedTitleUpdatedEvent event) {
        invalidateProfile(event.userId());
    }

    @EventHandler
    public void on(RewardEvent.RewardGrantedEvent event) {
        rewardLookup.invalidate(event.userId());
        logger.debug("Récompenses invalidées: userId={}, code={}", event.userId(), event.rewardCode());
    }

    @EventHandler
    public void on(UserAccountDeletedEvent event) {
        profileLookup.invalidate(event.userId());
        progressionLookup.invalidate(event.userId());
        rewardLookup.invalidate(event.userId());
        followLookup.invalidateAllCounts();
        identityLookup.invalidate(event.userId());
    }

    @EventHandler
    public void on(UserEvent.UserSuspendedEvent event) {
        identityLookup.invalidate(event.userId());
        logger.debug("Statut identity invalidé (suspension): userId={}", event.userId());
    }

    @EventHandler
    public void on(UserEvent.UserReinstatedEvent event) {
        identityLookup.invalidate(event.userId());
        logger.debug("Statut identity invalidé (réintégration): userId={}", event.userId());
    }

    private void invalidateCounts(UserFollowerEvent event) {
        // Le follower (followingCount) et le suivi (followersCount) changent tous les deux.
        followLookup.invalidateCounts(event.followerId());
        followLookup.invalidateCounts(event.followedId());
        logger.debug("Compteurs de follows invalidés: followerId={}, followedId={}",
                event.followerId(), event.followedId());
    }

    private void invalidateProfile(String userId) {
        profileLookup.invalidate(userId);
        logger.debug("Profil invalidé: userId={}", userId);
    }
}
