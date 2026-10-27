package com.joutio.bff.infrastructure.in.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code PUT /api/profiles/{userId}/pseudonym}.
 */
public record UpdatePseudonymRequest(
        @NotBlank @Size(max = 100) String pseudonym
) {
}
