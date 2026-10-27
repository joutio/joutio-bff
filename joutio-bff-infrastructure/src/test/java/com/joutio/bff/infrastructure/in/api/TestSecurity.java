package com.joutio.bff.infrastructure.in.api;

import com.joutio.microservice.core.domain.model.security.JoutioPrincipal;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Authentification de test : pose un {@link JoutioPrincipal} dans le contexte de sécurité,
 * ce que fait normalement le JWT resource server.
 */
final class TestSecurity {

    private TestSecurity() {
    }

    static void authenticate(String userId) {
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
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(principal, null));
    }

    static void clear() {
        SecurityContextHolder.clearContext();
    }
}
