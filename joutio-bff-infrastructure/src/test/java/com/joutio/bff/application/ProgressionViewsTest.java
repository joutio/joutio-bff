package com.joutio.bff.application;

import com.joutio.profile.domain.model.Badge;
import com.joutio.profile.domain.model.PlayerProgress;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressionViewsTest {

    @Test
    void toView_exposes_badges_and_elo_only() {
        PlayerProgress progress = PlayerProgress.empty("user-1").toBuilder()
                .badges(Set.of(Badge.FIRST_WIN))
                .rating(842)
                .peakRating(900)
                .ratedGames(12)
                .provisional(false)
                .build();

        var view = ProgressionViews.toView(progress);

        assertThat(view.badges()).extracting("code").containsExactly("FIRST_WIN");
        assertThat(view.rating()).isEqualTo(842);
        assertThat(view.peakRating()).isEqualTo(900);
        assertThat(view.ratedGames()).isEqualTo(12);
        assertThat(view.provisional()).isFalse();
    }
}
