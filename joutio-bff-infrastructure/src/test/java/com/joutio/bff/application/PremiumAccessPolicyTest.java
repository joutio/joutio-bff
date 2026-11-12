package com.joutio.bff.application;

import com.joutio.identity.domain.model.AccountStatus;
import com.joutio.identity.domain.model.PremiumSource;
import com.joutio.identity.domain.model.UserAccountStatus;
import com.joutio.identity.domain.model.UserPremium;
import com.joutio.microservice.core.domain.model.security.JoutioPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PremiumAccessPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

    private final IdentityLookup identityLookup = mock(IdentityLookup.class);
    private final PremiumAccessPolicy policy =
            new PremiumAccessPolicy(identityLookup, Clock.fixed(NOW, ZoneOffset.UTC));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminIsPremiumWithoutGrant() {
        authenticate("admin", "ROLE_ADMIN");
        stub("admin", status(AccountStatus.ACTIVE, null, null));
        assertTrue(policy.isPremium());
    }

    @Test
    void grantInAggregateIsPremiumWithoutTokenRole() {
        Instant until = NOW.plusSeconds(86_400);
        authenticate("user", "ROLE_USER");
        stub("user", status(AccountStatus.ACTIVE, null, grant(until)));

        PremiumAccessPolicy.PremiumStatus status = policy.status();
        assertTrue(status.premium());
        assertEquals(until, status.premiumUntil());
        assertEquals(PremiumSource.MANUAL, status.source());
    }

    @Test
    void premiumRoleInTokenIsNoLongerTrusted() {
        authenticate("user", "ROLE_PREMIUM");
        stub("user", status(AccountStatus.ACTIVE, null, null));
        assertFalse(policy.isPremium());
    }

    @Test
    void expiredGrantIsNotPremium() {
        authenticate("user", "ROLE_USER");
        stub("user", status(AccountStatus.ACTIVE, null, grant(NOW.minusSeconds(1))));
        assertFalse(policy.isPremium());
    }

    @Test
    void suspendedAccountIsNotPremium() {
        authenticate("user", "ROLE_USER");
        stub("user", status(AccountStatus.SUSPENDED, null, grant(null)));
        assertFalse(policy.isPremium());
    }

    @Test
    void identityFailureIsFailClosed() {
        authenticate("user", "ROLE_USER");
        when(identityLookup.status("user"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("down")));
        PremiumAccessPolicy.PremiumStatus status = policy.status();
        assertFalse(status.premium());
        assertNull(status.source());
    }

    @Test
    void anonymousIsNotPremium() {
        assertFalse(policy.isPremium());
    }

    private void stub(String userId, UserAccountStatus status) {
        when(identityLookup.status(userId)).thenReturn(CompletableFuture.completedFuture(status));
    }

    private static UserAccountStatus status(AccountStatus accountStatus, Instant suspendedUntil, UserPremium premium) {
        return new UserAccountStatus("user", accountStatus, suspendedUntil, premium);
    }

    private static UserPremium grant(Instant until) {
        return new UserPremium(PremiumSource.MANUAL, until, "admin-1", NOW.minusSeconds(3600));
    }

    private static void authenticate(String userId, String role) {
        JoutioPrincipal principal = new JoutioPrincipal() {
            @Override
            public String getUserId() {
                return userId;
            }

            @Override
            public String getEmail() {
                return userId + "@joutio.io";
            }
        };
        TestingAuthenticationToken token = new TestingAuthenticationToken(principal, null, role);
        token.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
