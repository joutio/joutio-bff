package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.identity.domain.model.AccountStatus;

import java.time.Instant;

/**
 * Ligne du catalogue admin des joueurs : compte (identity), profil public (profile) et
 * compteurs de signalements (identity).
 */
public record AdminUserCatalogueView(
        String userId,
        String email,
        String pseudonym,
        String avatarOptions,
        AccountStatus status,
        boolean suspended,
        Instant suspendedUntil,
        Instant createdAt,
        boolean protectedAccount,
        int openReportCount,
        int totalReportCount,
        Instant lastReportedAt
) {
}
