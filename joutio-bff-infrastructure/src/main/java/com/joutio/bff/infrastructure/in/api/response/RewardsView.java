package com.joutio.bff.infrastructure.in.api.response;

import java.time.Instant;
import java.util.List;

/**
 * Catalogue des récompenses du joueur courant : les récompenses débloquées viennent de
 * `player_reward`, les verrouillées du catalogue profile. Le libellé et la condition sont
 * traduits côté client à partir du code et du badge.
 */
public record RewardsView(List<RewardView> rewards) {

    public record RewardView(
            String code,
            boolean unlocked,
            Instant unlockedAt,
            String source,
            ConditionView condition
    ) {
    }

    public record ConditionView(
            String badgeCode,
            String badgeLabel,
            int target
    ) {
    }
}
