package com.joutio.bff.infrastructure.in.api.response;

import java.util.List;

/**
 * Données de l'accueil : sujets suivis (récents), sujets les plus joués et sujets récemment
 * publiés ou mis à jour (nouvelles questions).
 */
public record HomeView(
        List<TopicCardView> followedTopics,
        List<TopicCardView> trendingTopics,
        List<TopicCardView> newTopics,
        /** Sélection personnalisée « Pour toi » issue des centres d'intérêt persistés. */
        List<TopicCardView> recommendedTopics
) {
}
