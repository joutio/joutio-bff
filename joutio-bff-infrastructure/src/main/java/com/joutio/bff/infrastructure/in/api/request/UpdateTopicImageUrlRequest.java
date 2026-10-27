package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.theme.domain.model.TopicRules;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Mise à jour de l'illustration d'un sujet ({@code null} efface). */
public record UpdateTopicImageUrlRequest(
        @Size(max = TopicRules.MAX_IMAGE_URL_LENGTH)
        @Pattern(regexp = "^https?://.+", message = "doit être une URL http(s)") String imageUrl
) {
}
