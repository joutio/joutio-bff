package com.joutio.bff.infrastructure.in.api.response;

/**
 * Elo public minimal d'un joueur, pour la bordure d'avatar globale (hors topbar/sidebar).
 */
public record PlayerRatingView(String userId, int rating, boolean provisional) {
}
