package com.joutio.bff.infrastructure.in.api.request;

import jakarta.validation.constraints.NotNull;

/**
 * Corps de {@code PUT /api/profiles/{userId}/auto-train} : entraînement automatique contre le bot
 * quand une recherche d'adversaire expire.
 */
public record UpdateAutoTrainOnTimeoutRequest(
        @NotNull Boolean enabled
) {
}
