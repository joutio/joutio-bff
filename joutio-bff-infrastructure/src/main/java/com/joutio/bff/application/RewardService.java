package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.response.RewardsView;
import com.joutio.profile.domain.model.PlayerRewards;
import com.joutio.profile.domain.model.RewardCode;
import com.joutio.profile.domain.model.RewardGrant;
import com.joutio.profile.domain.model.RewardRules;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Composition du catalogue de récompenses : catalogue profile + déblocages persistés
 * (`player_reward`). Les verrouillées sont exposées avec leur condition.
 */
@Service
public class RewardService {

    private final RewardLookup rewardLookup;

    public RewardService(RewardLookup rewardLookup) {
        this.rewardLookup = rewardLookup;
    }

    public CompletableFuture<RewardsView> rewards(String userId) {
        return rewardLookup.get(userId).thenApply(playerRewards -> {
            Map<RewardCode, RewardGrant> unlockedByCode = playerRewards.rewards().stream()
                    .collect(Collectors.toMap(RewardGrant::code, Function.identity(), (first, _) -> first));

            List<RewardsView.RewardView> rewards = RewardRules.catalog().stream()
                    .map(definition -> {
                        RewardGrant grant = unlockedByCode.get(definition.code());
                        return new RewardsView.RewardView(
                                definition.code().name(),
                                grant != null,
                                grant == null ? null : grant.unlockedAt(),
                                grant == null ? null : grant.source().name(),
                                new RewardsView.ConditionView(
                                        definition.badge().name(),
                                        definition.badge().label(),
                                        definition.badge().target()));
                    })
                    .sorted(Comparator.comparing(RewardsView.RewardView::unlocked).reversed()
                            .thenComparing(RewardsView.RewardView::code))
                    .toList();
            return new RewardsView(rewards);
        });
    }
}
