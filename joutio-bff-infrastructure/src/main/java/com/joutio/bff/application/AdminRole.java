package com.joutio.bff.application;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Rôle {@code ROLE_ADMIN} du JWT : exploitation et contenus éditoriaux (sujets officiels). Sans lien avec le Premium,
 * qu'un admin n'a que par un droit accordé.
 */
public final class AdminRole {

    static final String ROLE_ADMIN = "ROLE_ADMIN";

    private AdminRole() {
    }

    public static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_ADMIN::equals);
    }
}
