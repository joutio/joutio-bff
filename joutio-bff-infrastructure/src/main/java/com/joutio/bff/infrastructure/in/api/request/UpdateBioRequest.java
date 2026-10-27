package com.joutio.bff.infrastructure.in.api.request;

import jakarta.validation.constraints.Size;

/**
 * Corps de {@code PUT /api/profiles/{userId}/bio}. {@code null} efface la bio.
 */
public record UpdateBioRequest(
        @Size(max = 300) String bio
) {
}
