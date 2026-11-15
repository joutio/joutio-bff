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
        /** Description française (contrat initial) ; voir {@link #descriptions()}. */
        String description,
        /** Description par langue (2026-11-13) ; l'app lit sa langue, sinon le français. */
        Map<Language, String> descriptions,
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
        TopicStatus status,
        /** Badge « Officiel » (2026-11-15) : sujet éditorial de Joutio, jamais un sujet de joueur. */
        boolean official
) {

    /** Carte sans badge (vues antérieures au badge « Officiel », tests). */
    public TopicCardView(
            String topicId,
            Map<Language, String> names,
            String description,
            Map<Language, String> descriptions,
            TopicCategory category,
            String categoryLabel,
            String imageUrl,
            String bannerUrl,
            String pictogramUrl,
            int followersCount,
            int questionsCount,
            boolean followed,
            TopicStatus status) {
        this(topicId, names, description, descriptions, category, categoryLabel, imageUrl, bannerUrl, pictogramUrl,
                followersCount, questionsCount, followed, status, false);
    }
}
