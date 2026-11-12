package com.joutio.bff.infrastructure.in.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code PUT /api/profiles/{userId}/pseudonym}. Les règles (3 à 20 caractères
 * {@code [A-Za-z][A-Za-z0-9_]}) et l'unicité sont tenues par {@code joutio-profile} :
 * {@code urn:joutio:profile:pseudonymInvalid}, {@code urn:joutio:profile:pseudonymTaken}.
 */
public record UpdatePseudonymRequest(
        @NotBlank @Size(max = 100) String pseudonym
) {
}
