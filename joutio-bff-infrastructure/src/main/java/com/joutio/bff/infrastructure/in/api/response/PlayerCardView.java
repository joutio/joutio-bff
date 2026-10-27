package com.joutio.bff.infrastructure.in.api.response;

/**
 * Carte joueur (listes Abonnements / Abonnés).
 */
public record PlayerCardView(
        String userId,
        String pseudonym,
        String avatarOptions,
        int rating,
        boolean provisional,
        boolean following,
        PresenceView presence,
        boolean suspended
) {
}
