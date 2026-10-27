package com.joutio.bff.infrastructure.in.api.request;

import jakarta.validation.constraints.NotNull;

/** Activation/désactivation d'une catégorie de notification. */
public record UpdateNotificationPreferenceRequest(
        @NotNull Boolean enabled
) {
}
