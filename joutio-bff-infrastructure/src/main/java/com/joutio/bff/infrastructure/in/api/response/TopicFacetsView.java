package com.joutio.bff.infrastructure.in.api.response;

import java.util.List;

/**
 * Facettes du catalogue : total + compteurs par catégorie pour les filtres courants.
 * Toutes les catégories sont présentes (compteur 0 si vide).
 */
public record TopicFacetsView(
        long total,
        List<CategoryFacetView> categories
) {

    public record CategoryFacetView(String category, String label, long count) {
    }
}
