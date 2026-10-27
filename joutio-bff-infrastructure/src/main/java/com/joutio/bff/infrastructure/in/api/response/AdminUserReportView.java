package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.identity.domain.model.UserReport;
import com.joutio.identity.domain.model.UserReportContext;
import com.joutio.identity.domain.model.UserReportReason;
import com.joutio.identity.domain.model.UserReportStatus;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Signalement d'un joueur vu par l'administration : identité du signalant (pseudonyme),
 * contexte et snapshot du profil signalé.
 */
public record AdminUserReportView(
        String reportId,
        String reporterId,
        String reporterPseudonym,
        Set<UserReportReason> reasons,
        String comment,
        UserReportContext context,
        String gameId,
        UserReportSnapshotView snapshot,
        UserReportStatus status,
        Instant createdAt,
        String reviewedBy,
        Instant reviewedAt,
        String resolutionNote
) {

    public record UserReportSnapshotView(String pseudonym, String avatarOptions) {
    }

    public static AdminUserReportView of(UserReport report, String reporterPseudonym) {
        return new AdminUserReportView(
                report.reportId(),
                report.reporterId(),
                reporterPseudonym,
                report.reasons(),
                report.comment(),
                report.context(),
                report.gameId(),
                report.snapshot() == null ? null : new UserReportSnapshotView(
                        report.snapshot().pseudonym(), report.snapshot().avatarOptions()),
                report.status(),
                report.createdAt(),
                report.reviewedBy(),
                report.reviewedAt(),
                report.resolutionNote());
    }
}
