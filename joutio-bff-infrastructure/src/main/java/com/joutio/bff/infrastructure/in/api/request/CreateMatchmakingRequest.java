package com.joutio.bff.infrastructure.in.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Entrée en recherche d'appariement public (« Défier le monde ») sur un sujet.
 */
public record CreateMatchmakingRequest(
        @NotBlank String topicId
) {
}
