package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.theme.domain.model.TopicCategory;
import com.joutio.theme.domain.model.TopicRules;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Création d'un sujet (brouillon) par le joueur courant. Les noms sont localisés (FR obligatoire,
 * EN optionnel) ; l'illustration de couverture est une URL externe optionnelle ; l'emoji et la
 * couleur d'accent alimentent la teinte des cartes.
 */
public record CreateTopicRequest(
        @NotEmpty Map<Language, @Size(max = TopicRules.MAX_NAME_LENGTH) String> names,
        @Size(max = TopicRules.MAX_DESCRIPTION_LENGTH) String description,
        @NotNull TopicCategory category,
        @Size(max = TopicRules.MAX_EMOJI_LENGTH) String emoji,
        @Size(max = TopicRules.MAX_COLOR_LENGTH) String color,
        @Size(max = TopicRules.MAX_IMAGE_URL_LENGTH)
        @Pattern(regexp = "^https?://.+", message = "doit être une URL http(s)") String imageUrl
) {
}
