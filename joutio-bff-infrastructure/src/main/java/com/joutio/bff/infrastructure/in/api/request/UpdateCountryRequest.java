package com.joutio.bff.infrastructure.in.api.request;

import jakarta.validation.constraints.Size;

/**
 * Corps de {@code PUT /api/profiles/{userId}/country}. {@code null} efface le pays.
 */
public record UpdateCountryRequest(
        @Size(max = 100) String country
) {
}
