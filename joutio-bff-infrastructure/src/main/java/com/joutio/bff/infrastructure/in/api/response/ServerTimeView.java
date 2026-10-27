package com.joutio.bff.infrastructure.in.api.response;

import java.time.Instant;

/**
 * Horloge serveur utilisée pour synchroniser les chronos de l'arène.
 */
public record ServerTimeView(
        Instant serverTime,
        long epochMillis
) {
}
