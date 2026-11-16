package com.joutio.bff.infrastructure.out.messaging.response;

import java.time.Instant;

/**
 * Présence d'un joueur dans un salon, poussée sur le flux personnel de l'autre joueur
 * ({@code ROOM_PRESENCE}) : {@code present=false} quand il est parti. Éphémère, hors inbox.
 */
public record RoomPresenceView(String roomId, String playerId, boolean present, Instant at) {}
