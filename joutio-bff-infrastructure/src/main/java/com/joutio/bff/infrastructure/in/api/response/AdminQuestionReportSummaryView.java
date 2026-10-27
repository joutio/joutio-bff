package com.joutio.bff.infrastructure.in.api.response;

/** Compteurs globaux de la file de signalements (badge console admin). */
public record AdminQuestionReportSummaryView(
        long openReports,
        long reportedQuestions,
        long suspendedQuestions,
        long archivedQuestions
) {
}
