package com.joutio.bff.application.live;

import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.DefaultSubscriptionQueryResult;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiveCacheTest {

    record Watch() {
    }

    record Update(String key, long version, String value) {
    }

    private final QueryGateway gateway = mock(QueryGateway.class);
    private final Sinks.One<Object> initial = Sinks.one();
    private final Sinks.Many<Object> updates = Sinks.many().unicast().onBackpressureBuffer();
    private final AtomicInteger loads = new AtomicInteger();
    private final LiveCache<Update, String> cache = new LiveCache<>(new LiveCache.Spec<>("test", new Watch(),
            Update.class, Update::key, Update::version, Update::value, 100, Duration.ofMinutes(1)), gateway);

    LiveCacheTest() {
        when(gateway.subscriptionQuery(any(), any(ResponseType.class), any(ResponseType.class)))
                .thenAnswer(_ -> new DefaultSubscriptionQueryResult<>(initial.asMono(), updates.asFlux(), () -> true));
    }

    @AfterEach
    void stop() {
        cache.stop();
    }

    private Function<String, CompletableFuture<String>> source(String value) {
        return _ -> {
            loads.incrementAndGet();
            return CompletableFuture.completedFuture(value);
        };
    }

    private void subscribed() {
        cache.start();
        initial.tryEmitValue(List.of());
        assertThat(cache.live()).isTrue();
    }

    @Test
    void readsTheSourceWithoutCachingUntilTheWatchIsOpen() {
        cache.start();

        cache.get("a", source("v1")).join();
        cache.get("a", source("v1")).join();

        assertThat(loads).hasValue(2);
    }

    @Test
    void cachesOnceTheWatchIsOpen() {
        subscribed();

        cache.get("a", source("v1")).join();
        assertThat(cache.get("a", source("other")).join()).isEqualTo("v1");

        assertThat(loads).hasValue(1);
    }

    @Test
    void anUpdateReplacesTheCachedValueAndAnOlderOneIsIgnored() {
        subscribed();
        cache.get("a", source("v1")).join();

        updates.tryEmitNext(new Update("a", 3, "v3"));
        updates.tryEmitNext(new Update("a", 2, "v2"));

        assertThat(cache.get("a", source("other")).join()).isEqualTo("v3");
        assertThat(loads).hasValue(1);
    }

    @Test
    void anUpdateOfAKeyNotInCacheIsNotCached() {
        subscribed();

        updates.tryEmitNext(new Update("a", 1, "v1"));

        assertThat(cache.get("a", source("source")).join()).isEqualTo("source");
    }

    @Test
    void anEmptyUpdateRemovesTheKey() {
        subscribed();
        cache.get("a", source("v1")).join();

        updates.tryEmitNext(new Update("a", Long.MAX_VALUE, null));

        assertThat(cache.get("a", source("reloaded")).join()).isEqualTo("reloaded");
    }

    @Test
    void anUpdateDuringALoadWinsOverTheLoadedValue() {
        subscribed();
        CompletableFuture<String> pending = new CompletableFuture<>();
        CompletableFuture<String> read = cache.get("a", _ -> pending);

        updates.tryEmitNext(new Update("a", 1, "pushed"));
        pending.complete("stale");

        assertThat(read.join()).isEqualTo("stale");
        assertThat(cache.get("a", source("other")).join()).isEqualTo("pushed");
    }

    @Test
    void losingTheWatchEmptiesTheCacheAndReadsTheSource() {
        subscribed();
        cache.get("a", source("v1")).join();

        updates.tryEmitError(new IllegalStateException("providers lost"));

        assertThat(cache.live()).isFalse();
        assertThat(cache.get("a", source("v2")).join()).isEqualTo("v2");
        assertThat(cache.get("a", source("v3")).join()).isEqualTo("v3");
    }

    @Test
    void getAllLoadsOnlyTheMissingKeysInOneCall() {
        subscribed();
        cache.get("a", source("va")).join();
        AtomicInteger batches = new AtomicInteger();

        Map<String, String> values = cache.getAll(List.of("a", "b", "c", "b"), missing -> {
            batches.incrementAndGet();
            assertThat(missing).containsExactlyInAnyOrder("b", "c");
            return CompletableFuture.completedFuture(Map.of("b", "vb"));
        }).join();

        assertThat(values).containsExactlyInAnyOrderEntriesOf(Map.of("a", "va", "b", "vb"));
        assertThat(batches).hasValue(1);
    }
}
