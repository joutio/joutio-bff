package com.joutio.bff.application.live;

import com.joutio.bff.application.live.TestLiveViews.GetScoreQuery;
import com.joutio.bff.application.live.TestLiveViews.MatchStreamLiveView;
import com.joutio.bff.application.live.TestLiveViews.Score;
import com.joutio.bff.application.live.TestLiveViews.ScoreLiveView;
import com.joutio.bff.application.live.TestLiveViews.ScoreView;
import com.joutio.bff.infrastructure.in.api.problem.BffProblems.LiveValueNotFoundProblem;
import com.joutio.bff.infrastructure.in.api.problem.BffProblems.LiveViewForbiddenProblem;
import com.joutio.bff.infrastructure.in.api.problem.BffProblems.LiveViewNotFoundProblem;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LiveViewsTest {

    private final QueryGateway gateway = mock(QueryGateway.class);
    private final LiveViews views = new LiveViews(List.of(new ScoreLiveView(), new MatchStreamLiveView()), gateway);

    @Test
    void resolves_exact_destinations_only() {
        assertThat(views.resolve("/topic/live/score/u1"))
                .hasValueSatisfying(target -> {
                    assertThat(target.view()).isInstanceOf(ScoreLiveView.class);
                    assertThat(target.key()).isEqualTo("u1");
                    assertThat(target.destination()).isEqualTo("/topic/live/score/u1");
                });
        assertThat(views.resolve("/topic/matches/m1")).isPresent();
        assertThat(views.resolve("/topic/live/score/")).isEmpty();
        assertThat(views.resolve("/topic/live/score/u1/more")).isEmpty();
        assertThat(views.resolve("/topic/live/unknown/u1")).isEmpty();
        assertThat(views.resolve("/topic/games/g1")).isEmpty();
    }

    @Test
    void rejects_two_views_on_the_same_destination() {
        assertThatThrownBy(() -> new LiveViews(List.of(new ScoreLiveView(), new ScoreLiveView()), gateway))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void reads_the_current_value_through_the_view_query_and_mapper() throws Exception {
        when(gateway.query(eq(new GetScoreQuery("u1")), any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(new Score("u1", 7, 120)));

        LiveUpdate<?> update = views.read("score", "u1", "u1").get();

        assertThat(update).isEqualTo(new LiveUpdate<>("score", "u1", 7, new ScoreView(120)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void an_absent_value_is_not_found() {
        when(gateway.query(eq(new GetScoreQuery("u1")), any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertThatThrownBy(() -> views.read("score", "u1", "u1").get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(LiveValueNotFoundProblem.class);
    }

    @Test
    void refuses_unknown_views_and_other_players_without_querying() {
        assertThatThrownBy(() -> views.read("unknown", "u1", "u1").get())
                .hasCauseInstanceOf(LiveViewNotFoundProblem.class);
        assertThatThrownBy(() -> views.read("score", "u2", "u1").get())
                .hasCauseInstanceOf(LiveViewForbiddenProblem.class);
        verifyNoInteractions(gateway);
    }
}
