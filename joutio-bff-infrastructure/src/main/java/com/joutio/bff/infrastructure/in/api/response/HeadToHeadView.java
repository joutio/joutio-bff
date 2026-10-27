package com.joutio.bff.infrastructure.in.api.response;

/**
 * Bilan des duels communs entre deux joueurs, du point de vue de {@code userId}.
 */
public record HeadToHeadView(
        int played,
        int wins,
        int losses,
        int draws
) {
}
