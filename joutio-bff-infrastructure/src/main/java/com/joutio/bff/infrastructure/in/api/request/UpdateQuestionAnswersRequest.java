package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.microservice.core.domain.model.i18n.Language;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Mise à jour des 4 réponses d'un contenu localisé existant. */
public record UpdateQuestionAnswersRequest(
        @NotNull Language language,
        @NotEmpty @Size(min = 4, max = 4) @Valid List<QuestionContentRequest.AnswerRequest> answers
) {
}
