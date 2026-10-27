package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.theme.domain.model.TopicRules;
import jakarta.validation.constraints.Size;

/** Mise à jour de l'emoji d'un sujet ({@code null} efface). */
public record UpdateTopicEmojiRequest(
        @Size(max = TopicRules.MAX_EMOJI_LENGTH) String emoji
) {
}
