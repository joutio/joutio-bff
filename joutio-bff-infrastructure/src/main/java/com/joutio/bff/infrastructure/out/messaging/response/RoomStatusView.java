package com.joutio.bff.infrastructure.out.messaging.response;

import com.joutio.matchmaking.domain.model.RoomStatus;

import java.time.Instant;

/**
 * Changement de cycle de vie d'une salle, poussé aux joueurs concernés sur leur flux personnel ({@code ROOM_STATUS}) :
 * invitation reçue ou envoyée, acceptée, refusée, annulée, expirée, partie créée. {@code expiresAt} est l'échéance de
 * la nouvelle étape quand elle en a une.
 */
public record RoomStatusView(String roomId, RoomStatus status, Instant expiresAt, Instant at) {
}
