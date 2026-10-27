package com.joutio.bff.infrastructure.in.api.response;

/** Statistiques de la file de modération des comptes. */
public record AdminUserModerationSummaryView(
        long openReports,
        long reportedUsers,
        long suspendedUsers
) {
}
