package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.theme.domain.model.QuestionRules;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Mise à jour de l'illustration d'une question ({@code null} efface). */
public record UpdateQuestionImageUrlRequest(
        @Size(max = QuestionRules.MAX_IMAGE_URL_LENGTH)
        @Pattern(regexp = "^https?://.+", message = "doit être une URL http(s)") String imageUrl
) {
}
