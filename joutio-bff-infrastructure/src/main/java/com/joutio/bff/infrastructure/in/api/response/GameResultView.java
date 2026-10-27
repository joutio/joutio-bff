package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.game.domain.model.BotDifficulty;

/**
 * Écran de résultat d'un duel terminé : score, détail de la performance, récompense XP de la
 * partie ({@code null} si la progression n'est pas encore projetée), Elo et maîtrise du sujet.
 * Enrichi du sujet et de l'adversaire pour que la page résultat soit autosuffisante.
 */
public record GameResultView(
        int myScore,
        int opponentScore,
        String winnerId,
        boolean botGame,
        int basePoints,
        int speedBonus,
        int correctAnswers,
        int fastAnswers,
        int answeredRounds,
        int totalRounds,
        RewardView reward,
        ProgressionResultView progression,
        TopicMasteryView topicMastery,
        /** Variation d'Elo du joueur (0 si non classé). */
        int eloDelta,
        boolean rated,
        int opponentRating,
        /** Variation d'Elo de l'adversaire (0 si non classé). */
        int opponentEloDelta,
        boolean opponentRated,
        TopicRefView topic,
        UserRefView opponent,
        String opponentId,
        BotDifficulty botDifficulty,
        /** L'adversaire a supprimé son compte : pseudo anonymisé et revanche interdite. */
        boolean opponentDeleted
) {

    /** Récompense de la partie : XP gagnée dans le sujet et bonus de victoire dérivé du score. */
    public record RewardView(int xp, int victoryBonus) {
    }

    /** État Elo du joueur à l'issue de la partie. */
    public record ProgressionResultView(
            int rating,
            int peakRating,
            int ratedGames,
            boolean provisional
    ) {
    }

    /** Maîtrise du sujet après la partie : XP courante, gain du duel et palier atteint. */
    public record TopicMasteryView(
            int xp,
            int xpGained,
            String tier,
            String nextTier,
            int tierProgressPercent,
            int tierRemainingXp
    ) {
    }
}
