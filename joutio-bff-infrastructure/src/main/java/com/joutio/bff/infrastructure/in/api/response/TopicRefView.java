package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.microservice.core.domain.model.i18n.Language;

import java.util.Map;

/**
 * Référence minimale d'un sujet (historique de duel, défi) : libellé + visuel + catégorie.
 */
public record TopicRefView(
        String topicId,
        Map<Language, String> names,
        String category,
        String imageUrl,
        /** Pictogramme carré (`null` : repli sur la vignette, puis la catégorie). */
        String pictogramUrl,
        /** Titres thématiques par palier et par langue (null → repli « palier · nom »). */
        Map<Language, Map<String, String>> titles,
        /** Badge « Officiel » (2026-11-15). */
        boolean official
) {

    /** Référence sans badge (sujet inconnu, tests). */
    public TopicRefView(
            String topicId,
            Map<Language, String> names,
            String category,
            String imageUrl,
            String pictogramUrl,
            Map<Language, Map<String, String>> titles) {
        this(topicId, names, category, imageUrl, pictogramUrl, titles, false);
    }
}
