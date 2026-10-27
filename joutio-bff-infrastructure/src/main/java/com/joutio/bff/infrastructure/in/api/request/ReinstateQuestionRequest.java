package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.theme.domain.model.QuestionReportRules;
import jakarta.validation.constraints.Size;

/** Réactivation d'une question suspendue, avec clôture optionnelle des signalements ouverts. */
public record ReinstateQuestionRequest(
        Boolean resolveOpenReports,
        @Size(max = QuestionReportRules.MAX_NOTE_LENGTH) String note
) {
}
