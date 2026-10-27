package com.joutio.bff.application;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Entitlement Premium de la requête courante. L'admin est Premium par défaut ; un futur
 * abonnement pourra émettre {@code ROLE_PREMIUM} sans changer les consommateurs.
 */
@Component
public class PremiumAccessPolicy {

    static final String ROLE_ADMIN = "ROLE_ADMIN";
    static final String ROLE_PREMIUM = "ROLE_PREMIUM";

    public boolean isPremium() {
        return isPremium(SecurityContextHolder.getContext().getAuthentication());
    }

    static boolean isPremium(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> ROLE_ADMIN.equals(role) || ROLE_PREMIUM.equals(role));
    }
}
