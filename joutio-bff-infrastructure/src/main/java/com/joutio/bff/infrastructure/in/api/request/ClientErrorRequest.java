package com.joutio.bff.infrastructure.in.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Erreur côté navigateur (exception JS, promesse rejetée, erreur de rendu React).
 * Le client n'envoie ni identité, ni contenu de formulaire : uniquement le diagnostic technique.
 */
public record ClientErrorRequest(
        @NotBlank @Size(max = 40) String type,
        @NotBlank @Size(max = 500) String message,
        @Size(max = 4000) String stack,
        @Size(max = 120) String route,
        @Size(max = 64) String buildId,
        @Size(max = 120) String source
) {
}
