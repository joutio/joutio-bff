package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.response.MeView;
import com.joutio.profile.domain.model.PlayerProgress;
import com.joutio.profile.domain.model.PlayerRewards;
import com.joutio.profile.domain.model.Profile;
import com.joutio.social.domain.model.UserFollowCounts;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Vue du joueur courant : profil, progression, compteurs d'abonnements et défis en attente.
 * Les trois lectures passent par les lookups cachés (Caffeine) : `/api/me` est appelé sur chaque
 * écran et en polling, on amortit les requêtes distribuées (`profile`, `profile`, `social`).
 */
@Service
public class MeService {

    private final ProfileLookup profileLookup;
    private final ProgressionLookup progressionLookup;
    private final RewardLookup rewardLookup;
    private final FollowLookup followLookup;
    private final PremiumAccessPolicy premiumAccessPolicy;

    public MeService(ProfileLookup profileLookup,
                     ProgressionLookup progressionLookup,
                     RewardLookup rewardLookup,
                     FollowLookup followLookup,
                     PremiumAccessPolicy premiumAccessPolicy) {
        this.profileLookup = profileLookup;
        this.progressionLookup = progressionLookup;
        this.rewardLookup = rewardLookup;
        this.followLookup = followLookup;
        this.premiumAccessPolicy = premiumAccessPolicy;
    }

    public CompletableFuture<MeView> me(String userId) {
        // Capturé sur le thread HTTP : le callback Axon peut s'exécuter sur un autre thread sans
        // SecurityContext, ce qui ferait perdre le rôle admin/premium.
        return me(userId, premiumAccessPolicy.isPremium());
    }

    /** Variante pour les appels déclenchés depuis un callback asynchrone (fin d'onboarding). */
    public CompletableFuture<MeView> me(String userId, boolean premium) {
        CompletableFuture<Profile> profileFuture = profileLookup.get(userId);
        CompletableFuture<PlayerProgress> progressFuture = progressionLookup.get(userId);
        CompletableFuture<PlayerRewards> rewardsFuture = rewardLookup.get(userId)
                .exceptionally(_ -> PlayerRewards.empty(userId));
        CompletableFuture<UserFollowCounts> countsFuture = followLookup.userCounts(userId);

        return CompletableFuture.allOf(profileFuture, progressFuture, rewardsFuture, countsFuture)
                .thenApply(_ -> {
                    Profile profile = profileFuture.join();
                    PlayerProgress progress = progressFuture.join();
                    PlayerRewards rewards = rewardsFuture.join();
                    UserFollowCounts counts = countsFuture.join();
                    return new MeView(
                            profile.userId(),
                            profile.email(),
                            profile.pseudonym(),
                            profile.bio(),
                            profile.country(),
                            profile.avatarOptions(),
                            profile.bannerUrl(),
                            profile.equippedTitle(),
                            profile.language(),
                            ProgressionViews.toView(progress),                            ProgressionViews.toStats(progress),
                            counts.following(),
                            counts.followers(),
                            profile.onboardingCompletedAt() != null || progress.gamesPlayed() > 0,
                            rewards.codes().stream().map(Enum::name).sorted().toList(),
                            premium,
                            profile.interests());
                });
    }


    public boolean isPremium() {
        return premiumAccessPolicy.isPremium();
    }
}
