package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.theme.domain.model.TopicRules;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Mise à jour de la bannière de la fiche sujet (visuel panoramique distinct de la couverture ;
 * {@code null} efface).
 */
public record UpdateTopicBannerUrlRequest(
        @Size(max = TopicRules.MAX_BANNER_URL_LENGTH)
        @Pattern(regexp = "^https?://.+", message = "doit être une URL http(s)") String bannerUrl
) {
}
