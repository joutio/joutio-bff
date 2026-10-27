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
        String emoji,
        String color,
        String imageUrl,
        /** Titres thématiques par palier et par langue (null → repli « palier · nom »). */
        Map<Language, Map<String, String>> titles
) {
}
