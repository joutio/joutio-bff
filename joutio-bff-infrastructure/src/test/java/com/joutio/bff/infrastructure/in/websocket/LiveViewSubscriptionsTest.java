package com.joutio.bff.infrastructure.in.websocket;

import com.joutio.bff.application.live.LiveUpdate;
import com.joutio.bff.application.live.LiveViews;
import com.joutio.bff.application.live.TestLiveViews.GetScoreQuery;
import com.joutio.bff.application.live.TestLiveViews.MatchStreamLiveView;
import com.joutio.bff.application.live.TestLiveViews.Score;
import com.joutio.bff.application.live.TestLiveViews.ScoreLiveView;
import com.joutio.bff.application.live.TestLiveViews.ScoreView;
import com.joutio.microservice.core.domain.model.event.EventEnvelope;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.DefaultSubscriptionQueryResult;
import org.axonframework.queryhandling.QueryExecutionException;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveViewSubscriptionsTest {

    private static final String SCORE = "/topic/live/score/u1";
    private static final String MATCH = "/topic/matches/m1";

    private final List<Pod> pods = new ArrayList<>();

    @AfterEach
    void tearDown() {
        pods.forEach(pod -> pod.subscriptions.shutdown());
    }

    @Test
    void two_sessions_on_two_pods_open_one_subscription_per_pod_and_close_on_last_departure() {
        Pod first = pod();
        Pod second = pod();

        first.subscriptions.subscribed("s1", "sub-1", SCORE);
        first.subscriptions.subscribed("s2", "sub-1", SCORE);
        second.subscriptions.subscribed("s3", "sub-1", SCORE);
        second.subscriptions.subscribed("s4", "sub-7", SCORE);

        assertThat(first.openings).hasSize(1);
        assertThat(second.openings).hasSize(1);
        assertThat(first.openings.getFirst().query).isEqualTo(new GetScoreQuery("u1"));

        first.subscriptions.unsubscribed("s1", "sub-1");
        second.subscriptions.disconnected("s3");
        assertThat(first.openings.getFirst().cancelled).isFalse();
        assertThat(second.openings.getFirst().cancelled).isFalse();

        first.subscriptions.disconnected("s2");
        second.subscriptions.unsubscribed("s4", "sub-7");
        assertThat(first.openings.getFirst().cancelled).isTrue();
        assertThat(second.openings.getFirst().cancelled).isTrue();
        assertThat(first.subscriptions.openRelays()).isZero();
        assertThat(second.subscriptions.openRelays()).isZero();
    }

    @Test
    void a_new_subscriber_after_the_last_departure_reopens() {
        Pod pod = pod();
        pod.subscriptions.subscribed("s1", "sub-1", SCORE);
        pod.subscriptions.unsubscribed("s1", "sub-1");
        pod.subscriptions.subscribed("s1", "sub-2", SCORE);

        assertThat(pod.openings).hasSize(2);
        assertThat(pod.openings.getFirst().cancelled).isTrue();
        assertThat(pod.openings.getLast().cancelled).isFalse();
    }

    @Test
    void other_destinations_open_nothing() {
        Pod pod = pod();
        pod.subscriptions.subscribed("s1", "sub-1", "/topic/games/g1");
        pod.subscriptions.subscribed("s1", "sub-2", "/topic/live/unknown/u1");

        assertThat(pod.openings).isEmpty();
    }

    @Test
    void broadcasts_the_initial_result_then_only_newer_versions() {
        Pod pod = pod();
        pod.subscriptions.subscribed("s1", "sub-1", SCORE);
        Opening opening = pod.openings.getFirst();

        opening.initial(new Score("u1", 5, 100));
        opening.update(new Score("u1", 5, 100));
        opening.update(new Score("u1", 4, 90));
        opening.update(new Score("u1", 6, 110));
        opening.update(new Score("u1", 6, 110));

        assertThat(pod.broadcasts()).containsExactly(score(5, 100), score(6, 110));
    }

    @Test
    void a_late_subscriber_receives_the_current_value_alone() {
        Pod pod = pod();
        pod.subscriptions.subscribed("s1", "sub-1", SCORE);
        Opening opening = pod.openings.getFirst();
        opening.initial(new Score("u1", 5, 100));
        opening.update(new Score("u1", 6, 110));

        pod.subscriptions.subscribed("s2", "sub-3", SCORE);

        verify(pod.sender).sendTo("s2", "sub-3", SCORE, score(6, 110));
        assertThat(pod.broadcasts()).hasSize(2);
    }

    @Test
    void a_subscriber_joining_while_the_view_opens_waits_for_the_initial_broadcast() {
        Pod pod = pod();
        pod.subscriptions.subscribed("s1", "sub-1", SCORE);
        pod.subscriptions.subscribed("s2", "sub-1", SCORE);

        verify(pod.sender, never()).sendTo(anyString(), anyString(), anyString(), any());
        pod.openings.getFirst().initial(new Score("u1", 5, 100));
        assertThat(pod.broadcasts()).containsExactly(score(5, 100));
    }

    @Test
    void reopens_after_losing_every_provider_and_broadcasts_what_was_missed() {
        Pod pod = pod();
        pod.subscriptions.subscribed("s1", "sub-1", SCORE);
        Opening lost = pod.openings.getFirst();
        lost.initial(new Score("u1", 5, 100));

        lost.fail(new QueryExecutionException("lost all its providers", null));
        assertThat(lost.cancelled).isTrue();
        await(() -> pod.openings.size() == 2);

        Opening reopened = pod.openings.getLast();
        reopened.initial(new Score("u1", 8, 140));
        reopened.update(new Score("u1", 7, 130));
        lost.update(new Score("u1", 9, 150));

        await(() -> pod.broadcasts().size() >= 2);
        assertThat(pod.broadcasts()).containsExactly(score(5, 100), score(8, 140));
    }

    @Test
    void reopening_after_an_empty_initial_result_broadcasts_the_value_that_appeared() {
        Pod pod = pod();
        pod.subscriptions.subscribed("s1", "sub-1", SCORE);
        Opening lost = pod.openings.getFirst();
        lost.initial(null);

        lost.fail(new QueryExecutionException("lost all its providers", null));
        await(() -> pod.openings.size() == 2);
        pod.openings.getLast().initial(new Score("u1", 1, 10));

        await(() -> !pod.broadcasts().isEmpty());
        assertThat(pod.broadcasts()).containsExactly(score(1, 10));
    }

    @Test
    void an_aggregate_stream_fills_the_gap_with_the_missed_events() {
        Pod pod = pod();
        pod.subscriptions.subscribed("s1", "sub-1", MATCH);
        Opening lost = pod.openings.getFirst();
        lost.initial(List.of(event(0, "Created"), event(1, "Started")));
        lost.update(event(2, "RoundStarted"));

        lost.fail(new QueryExecutionException("lost all its providers", null));
        await(() -> pod.openings.size() == 2);
        pod.openings.getLast().initial(List.of(event(0, "Created"), event(1, "Started"), event(2, "RoundStarted"),
                event(3, "RoundClosed"), event(4, "Ended")));

        await(() -> pod.broadcasts().size() >= 5);
        assertThat(pod.broadcasts())
                .containsExactly("Created", "Started", "RoundStarted", "RoundClosed", "Ended");
    }

    @Test
    void a_view_completed_by_its_provider_is_not_reopened() throws InterruptedException {
        Pod pod = pod();
        pod.subscriptions.subscribed("s1", "sub-1", SCORE);
        Opening opening = pod.openings.getFirst();

        opening.complete();
        Thread.sleep(50);

        assertThat(opening.cancelled).isTrue();
        assertThat(pod.openings).hasSize(1);
    }

    @Test
    void no_reopen_once_the_last_subscriber_left() throws InterruptedException {
        Pod pod = pod();
        pod.subscriptions.subscribed("s1", "sub-1", SCORE);
        pod.openings.getFirst().fail(new QueryExecutionException("lost all its providers", null));
        pod.subscriptions.disconnected("s1");

        Thread.sleep(50);

        assertThat(pod.openings).hasSize(1);
        assertThat(pod.broadcasts()).isEmpty();
    }

    private Pod pod() {
        Pod pod = new Pod();
        pods.add(pod);
        return pod;
    }

    private static LiveUpdate<ScoreView> score(long version, int points) {
        return new LiveUpdate<>("score", "u1", version, new ScoreView(points));
    }

    /** Un pod du BFF : son pont, son bus de requêtes (simulé) et ses envois STOMP. */
    private static final class Pod {

        final QueryGateway gateway = mock(QueryGateway.class);
        final LiveViewSender sender = mock(LiveViewSender.class);
        final List<Opening> openings = new CopyOnWriteArrayList<>();
        final LiveViewSubscriptions subscriptions;

        @SuppressWarnings("unchecked")
        Pod() {
            when(gateway.subscriptionQuery(any(), any(ResponseType.class), any(ResponseType.class)))
                    .thenAnswer(invocation -> {
                        Opening opening = new Opening(invocation.getArgument(0));
                        openings.add(opening);
                        return opening.result();
                    });
            LiveViews views = new LiveViews(List.of(new ScoreLiveView(), new MatchStreamLiveView()), gateway);
            subscriptions = new LiveViewSubscriptions(views, gateway, sender, Duration.ofMillis(5),
                    Duration.ofMillis(20));
        }

        List<Object> broadcasts() {
            return mockingDetails(sender).getInvocations().stream()
                    .filter(invocation -> invocation.getMethod().getName().equals("broadcast"))
                    .map(invocation -> invocation.getArgument(1))
                    .toList();
        }
    }

    /** Un {@code subscriptionQuery} ouvert par le pont, piloté par le test. */
    private static final class Opening {

        final Object query;
        final Sinks.One<Object> initial = Sinks.one();
        final Sinks.Many<Object> updates = Sinks.many().unicast().onBackpressureBuffer();
        volatile boolean cancelled;

        Opening(Object query) {
            this.query = query;
        }

        DefaultSubscriptionQueryResult<Object, Object> result() {
            return new DefaultSubscriptionQueryResult<>(initial.asMono(), updates.asFlux(), () -> {
                cancelled = true;
                return true;
            });
        }

        void initial(Object value) {
            if (value == null) {
                initial.tryEmitEmpty();
            } else {
                initial.tryEmitValue(value);
            }
        }

        void update(Object value) {
            updates.tryEmitNext(value);
        }

        void fail(Throwable failure) {
            updates.tryEmitError(failure);
        }

        void complete() {
            updates.tryEmitComplete();
        }
    }

    private static EventEnvelope event(long sequence, String type) {
        return new EventEnvelope("m1", sequence, Instant.EPOCH, type, null);
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 2_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("condition not met within 2 s");
            }
            Thread.onSpinWait();
        }
    }
}
