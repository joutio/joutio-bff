package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.application.FollowLookup;
import com.joutio.bff.application.IdentityLookup;
import com.joutio.bff.application.ProfileLookup;
import com.joutio.bff.application.ProgressionLookup;
import com.joutio.bff.application.RewardLookup;
import com.joutio.identity.domain.event.UserEvent;
import com.joutio.identity.domain.event.UserEvent.UserAccountDeletedEvent;
import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.profile.domain.event.ProfileEvent;
import com.joutio.profile.domain.event.RewardEvent;
import com.joutio.profile.domain.model.RewardCode;
import com.joutio.profile.domain.model.RewardSource;
import com.joutio.social.domain.event.UserFollowerEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Invalidations cross-réplicas : chaque pod BFF purge son cache Caffeine local sur les événements
 * social (follows) et profile (éditions) — l'invalidation locale de la réplique qui a traité
 * l'écriture n'est qu'un fast-path.
 */
class CacheInvalidationHandlerTest {

    private final FollowLookup followLookup = mock(FollowLookup.class);
    private final ProfileLookup profileLookup = mock(ProfileLookup.class);
    private final IdentityLookup identityLookup = mock(IdentityLookup.class);
    private final ProgressionLookup progressionLookup = mock(ProgressionLookup.class);
    private final RewardLookup rewardLookup = mock(RewardLookup.class);
    private final CacheInvalidationHandler handler =
            new CacheInvalidationHandler(
                    followLookup, profileLookup, identityLookup, progressionLookup, rewardLookup);

    @Test
    void accountDeletionInvalidatesAllLocalCaches() {
        handler.on(new UserAccountDeletedEvent(
                "u1", "deletion-1", Instant.now()));

        verify(profileLookup).invalidate("u1");
        verify(progressionLookup).invalidate("u1");
        verify(rewardLookup).invalidate("u1");
        verify(identityLookup).invalidate("u1");
        verify(followLookup).invalidateAllCounts();
    }

    @Test
    void rewardGrantedInvalidatesRewardsCache() {
        handler.on(new RewardEvent.RewardGrantedEvent(
                "u1", RewardCode.MICAH_AVATAR, RewardSource.BADGE, Instant.now()));

        verify(rewardLookup).invalidate("u1");
    }

    @Test
    void followedEventInvalidatesBothSides() {
        handler.on(new UserFollowerEvent.UserFollowedEvent(
                "f1:f2", "f1", "f2", Instant.now()));

        verify(followLookup).invalidateCounts("f1");
        verify(followLookup).invalidateCounts("f2");
    }

    @Test
    void unfollowedEventInvalidatesBothSides() {
        handler.on(new UserFollowerEvent.UserUnfollowedEvent(
                "f1:f2", "f1", "f2", Instant.now()));

        verify(followLookup).invalidateCounts("f1");
        verify(followLookup).invalidateCounts("f2");
    }

    @Test
    void profileFieldEventsInvalidateProfileCache() {
        Instant now = Instant.now();
        handler.on(new ProfileEvent.ProfilePseudonymUpdatedEvent("u1", "u1", "Alicia", now));
        handler.on(new ProfileEvent.ProfileBioUpdatedEvent("u1", "u1", "bio", now));
        handler.on(new ProfileEvent.ProfileCountryUpdatedEvent("u1", "u1", "FR", now));
        handler.on(new ProfileEvent.ProfileAvatarUpdatedEvent("u1", "u1", "{}", now));
        handler.on(new ProfileEvent.ProfileBannerUpdatedEvent("u1", "u1", "https://media.example.test/b.jpg", now));
        handler.on(new ProfileEvent.ProfileLanguageUpdatedEvent("u1", "u1", Language.FR, now));
        handler.on(new ProfileEvent.ProfileInterestsUpdatedEvent(
                "u1", "u1", java.util.List.of("HISTORY"), now));
        handler.on(new ProfileEvent.ProfileEquippedTitleUpdatedEvent("u1", "u1", "t1:FER", now));

        verify(profileLookup, times(8)).invalidate("u1");
    }

    @Test
    void accountStatusEventsInvalidateIdentityCache() {
        Instant now = Instant.now();
        handler.on(new UserEvent.UserSuspendedEvent(
                "u1", "u1@joutio.dev", "s1",
                com.joutio.identity.domain.model.UserSuspensionReason.HARASSMENT,
                null, "admin-1", now, null));
        handler.on(new UserEvent.UserReinstatedEvent(
                "u1", "u1@joutio.dev", "s1",
                com.joutio.identity.domain.model.SuspensionReinstatementReason.MANUAL,
                null, "admin-1", now));

        verify(identityLookup, times(2)).invalidate("u1");
    }
}
