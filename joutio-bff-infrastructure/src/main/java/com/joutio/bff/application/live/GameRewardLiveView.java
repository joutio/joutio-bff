package com.joutio.bff.application.live;

import com.joutio.bff.application.ProgressionViews;
import com.joutio.bff.infrastructure.in.api.response.GameResultView;
import com.joutio.bff.infrastructure.in.api.response.GameRewardView;
import com.joutio.profile.domain.model.AwardedGame;
import com.joutio.profile.domain.model.GameReward;
import com.joutio.profile.domain.model.MasteryTier;
import com.joutio.profile.domain.query.ProgressionQuery.GetGameRewardQuery;
import org.springframework.stereotype.Component;

/**
 * Récompense d'un duel, {@code /topic/live/game-reward/{gameId}:{userId}} : la clé porte le joueur, car la valeur est
 * propre à chacun (profile émet à chaque joueur récompensé de la partie). Accès réservé au joueur de la clé ; un
 * joueur qui n'a pas joué la partie ne reçoit jamais de valeur.
 */
@Component
public class GameRewardLiveView extends VersionedLiveView<GameReward, GameRewardView> {

    public static final String NAME = "game-reward";
    private static final char SEPARATOR = ':';

    public GameRewardLiveView() {
        super(NAME, GameReward.class);
    }

    /** Clé de la vue pour ce joueur et cette partie. */
    public static String key(String gameId, String userId) {
        return gameId + SEPARATOR + userId;
    }

    @Override
    public boolean canSubscribe(String userId, String key) {
        int separator = key.lastIndexOf(SEPARATOR);
        return separator > 0 && key.substring(separator + 1).equals(userId);
    }

    @Override
    public Object query(String key) {
        int separator = key.lastIndexOf(SEPARATOR);
        return new GetGameRewardQuery(key.substring(0, separator), key.substring(separator + 1));
    }

    @Override
    public long version(GameReward reward) {
        return reward.version();
    }

    @Override
    protected GameRewardView payload(GameReward reward) {
        AwardedGame player = reward.player();
        AwardedGame opponent = reward.opponent();
        // Entraînement : XP nulle par construction, aucun bonus de victoire à dériver.
        int victoryBonus = player.botGame() ? 0 : player.xp() - player.gameScore();
        MasteryTier tier = MasteryTier.tierFor(reward.topicXp());
        return new GameRewardView(
                new GameResultView.RewardView(player.xp(), victoryBonus),
                player.rating(),
                reward.peakRating(),
                reward.ratedGames(),
                new GameResultView.TopicMasteryView(
                        reward.topicXp(),
                        player.xp(),
                        tier.name(),
                        tier.next() == null ? null : tier.next().name(),
                        tier.progressPercent(reward.topicXp()),
                        tier.remainingXp(reward.topicXp())),
                player.ratingDelta(),
                player.rated(),
                opponent == null ? null : opponent.rating(),
                opponent == null ? 0 : opponent.ratingDelta(),
                opponent != null && opponent.rated(),
                reward.badges().stream().map(ProgressionViews::toBadgeView).toList());
    }
}
