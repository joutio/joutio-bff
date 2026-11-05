package com.joutio.bff.infrastructure.in.api.request;

/**
 * Tri des listes de personnes.
 */
public enum PeopleSort {
    /** En ligne d'abord, puis hors ligne ; pseudonyme à égalité. */
    PRESENCE,
    RECENT,
    ELO,
    ALPHA
}
