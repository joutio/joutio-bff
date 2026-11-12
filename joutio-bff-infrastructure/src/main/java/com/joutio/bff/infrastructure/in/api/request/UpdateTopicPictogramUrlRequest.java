package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.theme.domain.model.TopicRules;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Mise à jour du pictogramme carré de la tuile du catalogue ({@code null} efface : repli sur la vignette, puis la
 * catégorie). L'image est d'abord envoyée par {@code POST /api/media} (PNG/JPEG normalisé) ; les SVG restent réservés
 * au seed.
 */
public record UpdateTopicPictogramUrlRequest(
        @Size(max = TopicRules.MAX_PICTOGRAM_URL_LENGTH)
        @Pattern(regexp = "^https?://.+", message = "doit être une URL http(s)") String pictogramUrl
) {
}
