package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.theme.domain.model.QuestionReport;
import com.joutio.theme.domain.model.QuestionReportReason;
import com.joutio.theme.domain.model.QuestionReportStatus;

import java.time.Instant;
import java.util.List;

/** Signalement vu par le propriétaire du sujet — sans identité du signalant. */
public record OwnerQuestionReportView(
        String reportId,
        List<QuestionReportReason> reasons,
        String comment,
        Language language,
        QuestionReportSnapshotView snapshot,
        QuestionReportStatus status,
        Instant createdAt,
        Instant reviewedAt,
        String resolutionNote
) {

    public static OwnerQuestionReportView of(QuestionReport report) {
        return new OwnerQuestionReportView(
                report.reportId(),
                report.reasons().stream().sorted().toList(),
                report.comment(),
                report.language(),
                QuestionReportSnapshotView.of(report.snapshot()),
                report.status(),
                report.createdAt(),
                report.reviewedAt(),
                report.resolutionNote());
    }
}
