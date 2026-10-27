package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.theme.domain.model.QuestionReportRules;
import jakarta.validation.constraints.Size;

/** Archivage d'une question (soft delete), avec clôture optionnelle des signalements ouverts. */
public record ArchiveQuestionRequest(
        Boolean resolveOpenReports,
        @Size(max = QuestionReportRules.MAX_NOTE_LENGTH) String note
) {
}
