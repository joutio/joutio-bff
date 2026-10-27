package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.theme.domain.model.QuestionChoice;
import com.joutio.theme.domain.model.QuestionRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Contenu localisé d'une question : texte + 4 réponses (choix A-D). Le choix correct est porté
 * par la question, pas par le contenu.
 */
public record QuestionContentRequest(
        @NotNull Language language,
        @NotBlank @Size(max = QuestionRules.MAX_TEXT_LENGTH) String text,
        @NotEmpty @Size(min = 4, max = 4) @Valid List<AnswerRequest> answers
) {

    public record AnswerRequest(
            @NotNull QuestionChoice choice,
            @NotBlank String text
    ) {
    }
}
