package com.joutio.bff.application;

import com.joutio.identity.domain.model.AccountStatus;
import com.joutio.identity.domain.model.User;
import com.joutio.identity.domain.model.UserSuspension;
import com.joutio.identity.domain.model.UserSuspensionReason;
import com.joutio.identity.domain.model.UserAccountStatus;
import com.joutio.identity.domain.query.UserQuery;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityLookupTest {

    @Mock
    private QueryGateway queryGateway;

    @Test
    void resolvesStatusFromIdentity() {
        User user = new User("user-1", "user@joutio.dev", Set.of(), Instant.now(),
                AccountStatus.ACTIVE, null);
        when(queryGateway.query(any(UserQuery.FindUserQuery.class), any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(user)));
        IdentityLookup lookup = new IdentityLookup(queryGateway);

        assertEquals(AccountStatus.ACTIVE, lookup.status("user-1").join().status());
    }

    @Test
    void reportsMissingUserAsNotFound() {
        when(queryGateway.query(any(UserQuery.FindUserQuery.class), any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        IdentityLookup lookup = new IdentityLookup(queryGateway);

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
        IdentityLookup lookup = new IdentityLookup(queryGateway);

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
