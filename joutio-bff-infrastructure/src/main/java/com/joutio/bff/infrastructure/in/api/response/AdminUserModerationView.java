package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.identity.domain.model.AccountStatus;
import com.joutio.identity.domain.model.UserSuspensionReason;

import java.time.Instant;

/** Résultat d'une suspension ou d'une réintégration. */
public record AdminUserModerationView(
        String userId,
        AccountStatus status,
        Instant suspendedUntil,
        UserSuspensionReason suspensionReason,
        int resolvedReports,
        boolean protectedAccount
) {
}
