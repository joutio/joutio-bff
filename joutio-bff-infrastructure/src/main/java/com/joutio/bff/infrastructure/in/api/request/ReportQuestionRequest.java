package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.theme.domain.model.QuestionReportReason;
import com.joutio.theme.domain.model.QuestionReportRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Signalement d'une question par un joueur, depuis la revue d'une partie terminée. */
public record ReportQuestionRequest(
        @NotBlank String gameId,
        @NotEmpty Set<QuestionReportReason> reasons,
        @Size(max = QuestionReportRules.MAX_COMMENT_LENGTH) String comment,
        Language language
) {
}
