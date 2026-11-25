package com.joutio.bff.infrastructure.in.api.response;

import java.util.List;

/**
 * Récompense d'un duel vue par l'un de ses joueurs : vue vivante {@code game-reward}, poussée à chaque écriture de
 * profile qui la change. Elle porte la partie de {@link GameResultView} calculée après la partie, sous les mêmes noms ;
 * le reste du résultat (scores, manches, sujet, adversaire) ne change plus une fois la partie terminée.
 *
 * @param rating           Elo du joueur après la partie, {@code null} pour une partie attribuée avant le 2026-11-25
 *                         (garder celui du résultat)
 * @param opponentRating   Elo de l'adversaire après la partie, {@code null} tant qu'il n'est pas récompensé, pour un
 *                         entraînement ou une partie ancienne (garder celui du résultat)
 * @param opponentEloDelta variation d'Elo de l'adversaire, 0 tant qu'il n'est pas récompensé
 * @param badges           badges débloqués par la partie, dans l'ordre d'obtention
 */
public record GameRewardView(
        GameResultView.RewardView reward,
        Integer rating,
        int peakRating,
        int ratedGames,
        GameResultView.TopicMasteryView topicMastery,
        int eloDelta,
        boolean rated,
        Integer opponentRating,
        int opponentEloDelta,
        boolean opponentRated,
        List<ProgressionView.BadgeView> badges
) {
}
