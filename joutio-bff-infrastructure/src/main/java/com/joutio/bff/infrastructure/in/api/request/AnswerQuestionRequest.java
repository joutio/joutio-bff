package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.game.domain.model.GameQuestionChoice;
import com.joutio.game.domain.model.GameRoundType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Réponse à la manche {@code round} (1 à 7) de l'arène. La manche rend le renvoi sûr : une réponse rejouée après une
 * coupure ne peut pas tomber sur la manche suivante.
 */
public record AnswerQuestionRequest(
        @NotNull @Min(1) @Max(7) Integer round,
        @NotNull GameQuestionChoice choice
) {

    public GameRoundType roundType() {
        return GameRoundType.values()[round - 1];
    }
}
