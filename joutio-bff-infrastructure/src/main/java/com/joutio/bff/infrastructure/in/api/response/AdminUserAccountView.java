package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.identity.domain.model.AccountStatus;
import com.joutio.identity.domain.model.SuspensionReinstatementReason;
import com.joutio.identity.domain.model.UserSuspensionReason;

import java.time.Instant;

/**
 * Compte vu par l'administration : statut effectif et métadonnées de la dernière suspension.
 */
public record AdminUserAccountView(
        String userId,
        String email,
        Instant createdAt,
        AccountStatus status,
        boolean suspended,
        Instant suspendedUntil,
        UserSuspensionReason suspensionReason,
        String suspensionNote,
        Instant suspendedAt,
        String suspendedBy,
        Instant reinstatedAt,
        String reinstatedBy,
        SuspensionReinstatementReason reinstatementReason,
        String reinstatementNote,
        boolean protectedAccount
) {
}
