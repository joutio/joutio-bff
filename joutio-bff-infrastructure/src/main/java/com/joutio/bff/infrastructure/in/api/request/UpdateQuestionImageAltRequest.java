package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.theme.domain.model.QuestionRules;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Description de l'image d'un contenu localisé existant ; {@code null} ou vide l'efface. */
public record UpdateQuestionImageAltRequest(
        @NotNull Language language,
        @Size(max = QuestionRules.MAX_IMAGE_ALT_LENGTH) String imageAlt
) {
}
