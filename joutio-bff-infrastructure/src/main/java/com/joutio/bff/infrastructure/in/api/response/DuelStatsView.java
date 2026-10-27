package com.joutio.bff.infrastructure.in.api.response;

/**
 * Statistiques de duels (V/N/D) d'un joueur sur ses duels **humains** : les duels contre bot
 * (XP par thème/badges conservés) sont exclus ; {@code draws} = égalités, jamais comptées en défaite.
 */
public record DuelStatsView(
        int played,
        int wins,
        int losses,
        int draws,
        int winPercent,
        int bestScore,
        int currentWinStreak,
        int bestWinStreak
) {
}
