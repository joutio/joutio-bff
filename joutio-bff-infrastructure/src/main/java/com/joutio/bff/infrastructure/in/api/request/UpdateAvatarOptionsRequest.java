package com.joutio.bff.infrastructure.in.api.request;

import jakarta.validation.constraints.Size;

/**
 * Corps de {@code PUT /api/profiles/{userId}/avatar-options}. {@code null} efface l'avatar.
 */
public record UpdateAvatarOptionsRequest(
        @Size(max = 2000) String avatarOptions
) {
}
