package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.profile.domain.model.ProfileRules;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code PUT /api/profiles/{userId}/banner}. {@code null} efface la bannière.
 */
public record UpdateBannerRequest(
        @Size(max = ProfileRules.MAX_BANNER_URL_LENGTH)
        @Pattern(regexp = "^https?://.+", message = "doit être une URL http(s)") String bannerUrl
) {
}
