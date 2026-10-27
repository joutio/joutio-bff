package com.joutio.bff.infrastructure.in.api.response;

import java.time.Instant;

/**
 * Ligne de la file de modération, groupée par joueur signalé.
 */
public record AdminReportedUserSummaryView(
        String userId,
        String email,
        String pseudonym,
        String avatarOptions,
        boolean suspended,
        Instant suspendedUntil,
        int openReportCount,
        int totalReportCount,
        Instant lastReportedAt
) {
}
