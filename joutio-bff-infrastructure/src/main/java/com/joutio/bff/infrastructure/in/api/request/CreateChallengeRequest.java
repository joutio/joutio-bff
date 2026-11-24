package com.joutio.bff.infrastructure.in.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Défi ({@code POST /api/rooms/challenge}) : le lanceur vient du JWT, l'invité du body. {@code gameId} (optionnel)
 * référence la <b>partie terminée d'origine</b> d'une revanche.
 */
public record CreateChallengeRequest(
        @NotBlank @Size(max = 255) String topicId,
        @NotBlank @Size(max = 255) String opponentId,
        @Size(max = 255) String gameId) {
}
