package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.game.domain.model.GamePlayerType;
import com.joutio.game.domain.model.GameStatus;

import java.time.Instant;

/**
 * Partie en cours du joueur (reprise) : sujet, adversaire (null pour un bot),
 * statut serveur et date de création.
 */
public record ActiveGameView(
        String gameId,
        TopicRefView topic,
        UserRefView opponent,
        GamePlayerType opponentType,
        GameStatus status,
        Instant createdAt
) {
}
