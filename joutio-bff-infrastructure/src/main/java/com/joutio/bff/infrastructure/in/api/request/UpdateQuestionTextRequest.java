package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.theme.domain.model.QuestionRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Mise à jour du texte d'un contenu localisé existant. */
public record UpdateQuestionTextRequest(
        @NotNull Language language,
        @NotBlank @Size(max = QuestionRules.MAX_TEXT_LENGTH) String text
) {
}
