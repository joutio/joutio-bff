package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.theme.domain.model.TopicCategory;
import com.joutio.theme.domain.model.TopicStatus;

import java.util.Map;

/**
 * Carte de sujet (catalogue, accueil, sujets suivis, mes sujets).
 */
public record TopicCardView(
        String topicId,
        Map<Language, String> names,
        String description,
        TopicCategory category,
        String categoryLabel,
        /** Vignette (celle de `TopicCard`). */
        String imageUrl,
        /** Bannière panoramique de la fiche sujet. */
        String bannerUrl,
        /** Pictogramme carré de la tuile du catalogue (`TopicTile`) ; repli : vignette, puis catégorie. */
        String pictogramUrl,
        int followersCount,
        int questionsCount,
        boolean followed,
        TopicStatus status
) {
}
