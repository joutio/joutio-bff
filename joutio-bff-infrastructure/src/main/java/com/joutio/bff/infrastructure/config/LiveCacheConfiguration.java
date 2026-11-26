package com.joutio.bff.infrastructure.config;

import com.joutio.bff.application.live.LiveCache;
import com.joutio.identity.domain.model.User;
import com.joutio.identity.domain.model.UserUpdate;
import com.joutio.identity.domain.query.UserQuery;
import com.joutio.profile.domain.model.PlayerProgress;
import com.joutio.profile.domain.model.PlayerProgressUpdate;
import com.joutio.profile.domain.model.PlayerRewards;
import com.joutio.profile.domain.model.PlayerRewardsUpdate;
import com.joutio.profile.domain.model.Profile;
import com.joutio.profile.domain.model.ProfileUpdate;
import com.joutio.profile.domain.query.ProfileQuery;
import com.joutio.profile.domain.query.ProgressionQuery;
import com.joutio.profile.domain.query.RewardQuery;
import com.joutio.social.domain.model.UserFollowCounts;
import com.joutio.social.domain.model.UserFollowCountsUpdate;
import com.joutio.social.domain.query.UserFollowerQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Caches du BFF tenus à jour par abonnement ({@link LiveCache}) : un {@code subscriptionQuery} par vue et par pod, sur
 * la requête de surveillance du service propriétaire.
 */
@Configuration
public class LiveCacheConfiguration {

    private static final long MAX_SIZE = 10_000;
    private static final Duration IDLE = Duration.ofMinutes(10);

    @Bean
    public LiveCache<UserUpdate, User> userCache(QueryGateway queryGateway) {
        return new LiveCache<>(new LiveCache.Spec<>("users", new UserQuery.WatchUsersQuery(), UserUpdate.class,
                UserUpdate::userId, UserUpdate::version, UserUpdate::user, MAX_SIZE, IDLE), queryGateway);
    }

    @Bean
    public LiveCache<ProfileUpdate, Profile> profileCache(QueryGateway queryGateway) {
        return new LiveCache<>(new LiveCache.Spec<>("profiles", new ProfileQuery.WatchProfilesQuery(),
                ProfileUpdate.class, ProfileUpdate::userId, ProfileUpdate::version, ProfileUpdate::profile, MAX_SIZE,
                IDLE), queryGateway);
    }

    @Bean
    public LiveCache<PlayerProgressUpdate, PlayerProgress> progressionCache(QueryGateway queryGateway) {
        return new LiveCache<>(new LiveCache.Spec<>("progressions", new ProgressionQuery.WatchProgressionsQuery(),
                PlayerProgressUpdate.class, PlayerProgressUpdate::userId, PlayerProgressUpdate::version,
                PlayerProgressUpdate::progress, MAX_SIZE, IDLE), queryGateway);
    }

    @Bean
    public LiveCache<PlayerRewardsUpdate, PlayerRewards> rewardCache(QueryGateway queryGateway) {
        return new LiveCache<>(new LiveCache.Spec<>("rewards", new RewardQuery.WatchPlayerRewardsQuery(),
                PlayerRewardsUpdate.class, PlayerRewardsUpdate::userId, PlayerRewardsUpdate::version,
                PlayerRewardsUpdate::rewards, MAX_SIZE, IDLE), queryGateway);
    }

    @Bean
    public LiveCache<UserFollowCountsUpdate, UserFollowCounts> followCountsCache(QueryGateway queryGateway) {
        return new LiveCache<>(new LiveCache.Spec<>("follow-counts", new UserFollowerQuery.WatchUserFollowCountsQuery(),
                UserFollowCountsUpdate.class, UserFollowCountsUpdate::userId, UserFollowCountsUpdate::version,
                UserFollowCountsUpdate::counts, MAX_SIZE, IDLE), queryGateway);
    }
}
