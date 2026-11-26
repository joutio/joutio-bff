package com.joutio.bff.application;

import com.joutio.bff.application.live.LiveCache;
import com.joutio.identity.domain.model.AccountStatus;
import com.joutio.identity.domain.model.UserUpdate;
import com.joutio.identity.domain.model.PremiumSource;
import com.joutio.identity.domain.model.User;
import com.joutio.identity.domain.model.UserPremium;
import com.joutio.identity.domain.model.UserSuspension;
import com.joutio.identity.domain.model.UserSuspensionReason;
import com.joutio.identity.domain.model.UserAccountStatus;
import com.joutio.identity.domain.query.UserQuery;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.DefaultSubscriptionQueryResult;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.AfterEach;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityLookupTest {

    @Mock
    private QueryGateway queryGateway;

    private final Sinks.Many<Object> updates = Sinks.many().unicast().onBackpressureBuffer();
    private LiveCache<UserUpdate, User> users;

    /** Lookup sur un cache dont l'abonnement à {@code WatchUsersQuery} est établi. */
    private IdentityLookup lookup() {
        lenient().when(queryGateway.subscriptionQuery(any(), any(ResponseType.class), any(ResponseType.class)))
                .thenAnswer(_ -> new DefaultSubscriptionQueryResult<>(Mono.just(List.of()), updates.asFlux(),
                        () -> true));
        users = new LiveCache<>(new LiveCache.Spec<>("users", new UserQuery.WatchUsersQuery(), UserUpdate.class,
                UserUpdate::userId, UserUpdate::version, UserUpdate::user, 100, Duration.ofMinutes(1)), queryGateway);
        users.start();
        return new IdentityLookup(queryGateway, users);
    }

    @AfterEach
    void stop() {
        if (users != null) {
            users.stop();
        }
    }

    @Test
    void resolvesStatusFromIdentity() {
        User user = new User("user-1", "user@joutio.dev", Set.of(), Instant.now(),
                AccountStatus.ACTIVE, null);
        when(queryGateway.query(any(UserQuery.FindUserQuery.class), any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(user)));
        IdentityLookup lookup = lookup();

        assertEquals(AccountStatus.ACTIVE, lookup.status("user-1").join().status());
    }

    @Test
    void premiumGrantPushedByIdentityReplacesTheCachedAccount() {
        UserPremium grant = new UserPremium(PremiumSource.MANUAL, null, "admin-1", Instant.now());
        User free = new User("user-1", "user@joutio.dev", Set.of(), Instant.now(), AccountStatus.ACTIVE, null);
        User premium = new User("user-1", "user@joutio.dev", Set.of(), Instant.now(),
                AccountStatus.ACTIVE, null, grant);
        when(queryGateway.query(any(UserQuery.FindUserQuery.class), any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(free)));
        IdentityLookup lookup = lookup();

        assertFalse(lookup.status("user-1").join().premiumActive(Instant.now()));
        updates.tryEmitNext(new UserUpdate("user-1", 2, premium));
        UserAccountStatus refreshed = lookup.status("user-1").join();
        assertEquals(grant, refreshed.premium());
        assertTrue(refreshed.premiumActive(Instant.now()));
    }

    @Test
    void reportsMissingUserAsNotFound() {
        when(queryGateway.query(any(UserQuery.FindUserQuery.class), any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        IdentityLookup lookup = lookup();

        assertTrue(lookup.status("ghost").isCompletedExceptionally());
    }

    @Test
    void cachesTheStatusAcrossCalls() {
        UserAccountStatus status = new UserAccountStatus(
                "user-1", AccountStatus.SUSPENDED, Instant.parse("2026-02-01T00:00:00Z"));
        User user = new User("user-1", "user@joutio.dev", Set.of(), Instant.now(),
                AccountStatus.SUSPENDED, new UserSuspension(
                        "s1", UserSuspensionReason.SPAM, null, "admin", Instant.now(),
                        Instant.parse("2026-02-01T00:00:00Z"), null, null, null, null));
        when(queryGateway.query(any(UserQuery.FindUserQuery.class), any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(user)));
        IdentityLookup lookup = lookup();

        assertTrue(lookup.status("user-1").join().suspended(Instant.parse("2026-01-01T00:00:00Z")));
        assertTrue(lookup.status("user-1").join().suspended(Instant.parse("2026-01-01T00:00:00Z")));

        verify(queryGateway, times(1))
                .query(eq(new UserQuery.FindUserQuery("user-1")), any(ResponseType.class));
    }

    @Test
    void expiredSuspensionIsEffectivelyActive() {
        UserAccountStatus status = new UserAccountStatus(
                "user-1", AccountStatus.SUSPENDED, Instant.parse("2026-01-01T00:00:00Z"));
        assertFalse(status.suspended(Instant.parse("2026-02-01T00:00:00Z")));
    }
}
