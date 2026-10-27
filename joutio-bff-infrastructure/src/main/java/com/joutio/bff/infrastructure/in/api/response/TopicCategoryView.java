package com.joutio.bff.infrastructure.in.api.response;

/**
 * Catégorie de sujet exposée au web (nom + libellé FR).
 */
public record TopicCategoryView(
        String category,
        String label
) {
}
