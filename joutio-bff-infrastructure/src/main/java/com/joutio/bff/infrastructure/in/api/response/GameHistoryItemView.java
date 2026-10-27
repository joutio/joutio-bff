package com.joutio.bff.infrastructure.in.api.response;

import java.time.Instant;

/**
 * Ligne d'historique d'un duel, relative au joueur consulté.
 */
public record GameHistoryItemView(
        String gameId,
        TopicRefView topic,
        UserRefView opponent,
        String opponentType,
        Outcome outcome,
        int myScore,
        int opponentScore,
        Integer xp,
        Instant playedAt
) {

    public enum Outcome {
        WIN,
        LOSS,
        DRAW,
        /** Partie créée, pas encore démarrée (salle/arène en attente des joueurs). */
        WAITING,
        /** Partie démarrée. */
        IN_PROGRESS,
        /** Partie annulée (no-show, expiration, sortie d'un joueur). */
        CANCELLED
    }
}
