package com.joutio.bff.application.live;

import com.joutio.bff.infrastructure.in.api.response.GameResultView;
import com.joutio.bff.infrastructure.in.api.response.GameRewardView;
import com.joutio.profile.domain.model.AwardedGame;
import com.joutio.profile.domain.model.Badge;
import com.joutio.profile.domain.model.GameReward;
import com.joutio.profile.domain.query.ProgressionQuery.GetGameRewardQuery;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GameRewardLiveViewTest {

    private static final AwardedGame ME = new AwardedGame("me", "game-1", "topic-1", 130, 100, false, 1212, 12, true);
    private static final AwardedGame OPPONENT =
            new AwardedGame("opponent", "game-1", "topic-1", 80, 80, false, 988, -12, true);

    private final GameRewardLiveView view = new GameRewardLiveView();

    @Test
    void only_the_player_of_the_key_subscribes() {
        String key = GameRewardLiveView.key("game-1", "me");

        assertThat(view.destinationPrefix()).isEqualTo("/topic/live/game-reward/");
        assertThat(view.canSubscribe("me", key)).isTrue();
        assertThat(view.canSubscribe("opponent", key)).isFalse();
        assertThat(view.canSubscribe(null, key)).isFalse();
        assertThat(view.canSubscribe("me", "me")).isFalse();
        assertThat(view.canSubscribe("me", ":me")).isFalse();
    }

    @Test
    void queries_the_reward_of_the_game_seen_by_the_player() {
        assertThat(view.query(GameRewardLiveView.key("game-1", "me")))
                .isEqualTo(new GetGameRewardQuery("game-1", "me"));
    }

    @Test
    void maps_the_reward_under_the_names_of_the_result() {
        GameReward reward = new GameReward("game-1", "me", 4, ME, OPPONENT, 900, 1250, 7,
                List.of(Badge.DUELIST_FER));

        LiveUpdate<?> update = view.message(GameRewardLiveView.key("game-1", "me"), reward).join();

        assertThat(update.view()).isEqualTo("game-reward");
        assertThat(update.version()).isEqualTo(4);
        GameRewardView payload = (GameRewardView) update.payload();
        assertThat(payload.reward()).isEqualTo(new GameResultView.RewardView(130, 30));
        assertThat(payload.rating()).isEqualTo(1212);
        assertThat(payload.peakRating()).isEqualTo(1250);
        assertThat(payload.ratedGames()).isEqualTo(7);
        assertThat(payload.topicMastery().xp()).isEqualTo(900);
        assertThat(payload.topicMastery().xpGained()).isEqualTo(130);
        assertThat(payload.eloDelta()).isEqualTo(12);
        assertThat(payload.rated()).isTrue();
        assertThat(payload.opponentRating()).isEqualTo(988);
        assertThat(payload.opponentEloDelta()).isEqualTo(-12);
        assertThat(payload.opponentRated()).isTrue();
        assertThat(payload.badges()).extracting(badge -> badge.code()).containsExactly("DUELIST_FER");
    }

    @Test
    void a_missing_opponent_or_a_training_keeps_the_result_values() {
        AwardedGame training = new AwardedGame("me", "game-1", "topic-1", 0, 60, true, null, 0, false);
        GameReward reward = new GameReward("game-1", "me", 1, training, null, 40, 1000, 0, List.of());

        GameRewardView payload = (GameRewardView) view.message(GameRewardLiveView.key("game-1", "me"), reward)
                .join().payload();

        assertThat(payload.reward()).isEqualTo(new GameResultView.RewardView(0, 0));
        assertThat(payload.rating()).isNull();
        assertThat(payload.opponentRating()).isNull();
        assertThat(payload.opponentEloDelta()).isZero();
        assertThat(payload.opponentRated()).isFalse();
    }
}
