package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.theme.domain.model.QuestionChoice;
import com.joutio.theme.domain.model.QuestionRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Création d'une question rattachée à un sujet brouillon (propriétaire uniquement). Le contenu
 * français est attendu ; la liste peut porter un contenu anglais en plus.
 */
public record CreateQuestionRequest(
        @NotEmpty @Valid List<QuestionContentRequest> contents,
        @NotNull QuestionChoice correctAnswer,
        @Size(max = QuestionRules.MAX_IMAGE_URL_LENGTH)
        @Pattern(regexp = "^https?://.+", message = "doit être une URL http(s)") String imageUrl
) {
}
