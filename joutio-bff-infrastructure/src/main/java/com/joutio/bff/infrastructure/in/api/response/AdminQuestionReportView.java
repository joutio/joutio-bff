package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.theme.domain.model.QuestionReport;
import com.joutio.theme.domain.model.QuestionReportReason;
import com.joutio.theme.domain.model.QuestionReportStatus;

import java.time.Instant;
import java.util.List;

/** Signalement vu par l'administration, avec l'identité du signalant. */
public record AdminQuestionReportView(
        String reportId,
        String reporterId,
        String reporterPseudonym,
        List<QuestionReportReason> reasons,
        String comment,
        Language language,
        QuestionReportSnapshotView snapshot,
        QuestionReportStatus status,
        Instant createdAt,
        Instant reviewedAt,
        String reviewedBy,
        String resolutionNote
) {

    public static AdminQuestionReportView of(QuestionReport report, String reporterPseudonym) {
        return new AdminQuestionReportView(
                report.reportId(),
                report.reporterId(),
                reporterPseudonym,
                report.reasons().stream().sorted().toList(),
                report.comment(),
                report.language(),
                QuestionReportSnapshotView.of(report.snapshot()),
                report.status(),
                report.createdAt(),
                report.reviewedAt(),
                report.reviewedBy(),
                report.resolutionNote());
    }
}
