package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.theme.domain.model.QuestionReportRules;
import jakarta.validation.constraints.Size;

/** Suspension manuelle d'une question par un administrateur. */
public record SuspendQuestionRequest(
        @Size(max = QuestionReportRules.MAX_NOTE_LENGTH) String note
) {
}
