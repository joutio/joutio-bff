package com.joutio.bff.application;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PremiumAccessPolicyTest {

    @Test
    void adminIsPremiumByDefault() {
        TestingAuthenticationToken admin = new TestingAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        admin.setAuthenticated(true);
        assertTrue(PremiumAccessPolicy.isPremium(admin));
    }

    @Test
    void premiumRoleIsPremium() {
        TestingAuthenticationToken premium = new TestingAuthenticationToken(
                "premium", null, List.of(new SimpleGrantedAuthority("ROLE_PREMIUM")));
        premium.setAuthenticated(true);
        assertTrue(PremiumAccessPolicy.isPremium(premium));
    }

    @Test
    void regularUserIsNotPremium() {
        TestingAuthenticationToken user = new TestingAuthenticationToken(
                "user", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        user.setAuthenticated(true);
        assertFalse(PremiumAccessPolicy.isPremium(user));
        assertFalse(PremiumAccessPolicy.isPremium(null));
    }
}
