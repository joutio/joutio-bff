package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.profile.domain.model.PresenceStatus;

import java.time.Instant;

/**
 * Présence d'un joueur (REST {@code /api/presence/{id}} et push WS {@code /topic/presence/{id}}).
 */
public record PresenceView(
        String userId,
        PresenceStatus status,
        Instant lastSeenAt
) {
}
