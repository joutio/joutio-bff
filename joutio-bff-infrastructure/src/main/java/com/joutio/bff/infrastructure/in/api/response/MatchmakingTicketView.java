package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.matchmaking.domain.model.MatchmakingStatus;

import java.time.Instant;

/**
 * Vue d'une recherche d'appariement public (« Défier le monde »).
 */
public record MatchmakingTicketView(
        String ticketId,
        String topicId,
        MatchmakingStatus status,
        String roomId,
        String opponentId,
        boolean vsBot,
        Instant createdAt,
        Instant updatedAt
) {
}
