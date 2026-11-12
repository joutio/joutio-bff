package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.identity.domain.model.PremiumSource;

import java.time.Instant;

/** Résultat d'une attribution ou d'un retrait de Premium par l'administration. */
public record AdminUserPremiumView(
        String userId,
        boolean premium,
        Instant premiumUntil,
        PremiumSource premiumSource
) {
}
