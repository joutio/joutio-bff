package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.theme.domain.model.QuestionRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Ajout d'un contenu localisé absent (langue non encore portée par la question). */
public record AddQuestionTranslationRequest(
        @NotNull Language language,
        @NotBlank @Size(max = QuestionRules.MAX_TEXT_LENGTH) String text,
        @NotEmpty @Size(min = 4, max = 4) @Valid List<QuestionContentRequest.AnswerRequest> answers
) {
}
