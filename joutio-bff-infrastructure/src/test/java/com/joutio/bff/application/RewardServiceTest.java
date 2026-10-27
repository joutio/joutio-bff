package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.response.RewardsView;
import com.joutio.profile.domain.model.PlayerRewards;
import com.joutio.profile.domain.model.RewardCode;
import com.joutio.profile.domain.model.RewardGrant;
import com.joutio.profile.domain.model.RewardSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RewardServiceTest {

    private final RewardLookup rewardLookup = mock(RewardLookup.class);
    private final RewardService service = new RewardService(rewardLookup);

    @Test
    void lockedRewardIsExposedWithItsBadgeCondition() {
        when(rewardLookup.get("u1"))
                .thenReturn(CompletableFuture.completedFuture(PlayerRewards.empty("u1")));

        RewardsView view = service.rewards("u1").join();

        assertThat(view.rewards()).hasSize(1);
        RewardsView.RewardView micah = view.rewards().getFirst();
        assertThat(micah.code()).isEqualTo("MICAH_AVATAR");
        assertThat(micah.unlocked()).isFalse();
        assertThat(micah.unlockedAt()).isNull();
        assertThat(micah.source()).isNull();
        assertThat(micah.condition().badgeCode()).isEqualTo("ASSIDUITY_BRONZE");
        assertThat(micah.condition().badgeLabel()).isEqualTo("Régulier");
        assertThat(micah.condition().target()).isEqualTo(7);
    }

    @Test
    void unlockedRewardCarriesDateAndSource() {
        Instant at = Instant.parse("2026-10-23T10:00:00Z");
        when(rewardLookup.get("u1")).thenReturn(CompletableFuture.completedFuture(
                new PlayerRewards("u1", List.of(
                        new RewardGrant(RewardCode.MICAH_AVATAR, RewardSource.BADGE, at)))));

        RewardsView view = service.rewards("u1").join();

        RewardsView.RewardView micah = view.rewards().getFirst();
        assertThat(micah.unlocked()).isTrue();
        assertThat(micah.unlockedAt()).isEqualTo(at);
        assertThat(micah.source()).isEqualTo("BADGE");
    }
}
