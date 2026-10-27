package com.joutio.bff.infrastructure.in.api.response;

import java.util.List;

/**
 * Progression d'un joueur : badges acquis et Elo. L'XP est une métrique par sujet (voir
 * {@code TopicProgress}) ; il n'existe plus de niveau ni d'XP globale au niveau joueur.
 */
public record ProgressionView(
        List<BadgeView> badges,
        /** Elo global du profil (profil, matchmaking, bordure). */
        int rating,
        int peakRating,
        int ratedGames,
        boolean provisional
) {

    public record BadgeView(String code, String label, String family, String tier) {
    }
}
