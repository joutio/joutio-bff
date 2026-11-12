package com.joutio.bff.application;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DuelLimitPolicyTest {

    private final PremiumAccessPolicy premium = mock(PremiumAccessPolicy.class);
    private final DuelLimitPolicy policy =
            new DuelLimitPolicy(new DuelLimitsProperties(5, 5, 20, 20, 5), premium);

    @Test
    void freePlayerGetsFreeQuotas() {
        when(premium.premium("free")).thenReturn(CompletableFuture.completedFuture(false));
        assertThat(policy.maxPendingChallenges("free").join()).isEqualTo(5);
        assertThat(policy.maxOpenRooms("free").join()).isEqualTo(5);
    }

    @Test
    void premiumPlayerGetsRaisedQuotas() {
        when(premium.premium("vip")).thenReturn(CompletableFuture.completedFuture(true));
        assertThat(policy.maxPendingChallenges("vip").join()).isEqualTo(20);
        assertThat(policy.maxOpenRooms("vip").join()).isEqualTo(20);
    }

    @Test
    void botRoomsAreTheSameForEveryone() {
        assertThat(policy.maxBotRooms()).isEqualTo(5);
        assertThat(policy.limits(true).botRooms()).isEqualTo(5);
        assertThat(policy.limits(false).botRooms()).isEqualTo(5);
    }

    @Test
    void zeroMeansUnlimited() {
        DuelLimitPolicy unlimited = new DuelLimitPolicy(new DuelLimitsProperties(0, 0, 0, 0, 0), premium);
        assertThat(unlimited.limits(false).pendingChallenges()).isEqualTo(Integer.MAX_VALUE);
        assertThat(unlimited.maxBotRooms()).isEqualTo(Integer.MAX_VALUE);
    }
}
