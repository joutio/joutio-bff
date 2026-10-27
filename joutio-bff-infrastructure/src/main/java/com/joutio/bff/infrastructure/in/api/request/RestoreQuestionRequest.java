package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.theme.domain.model.QuestionReportRules;
import jakarta.validation.constraints.Size;

/** Restauration d'une question archivée. */
public record RestoreQuestionRequest(
        @Size(max = QuestionReportRules.MAX_NOTE_LENGTH) String note
) {
}
