package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.matchmaking.domain.model.RoomStatus;

import java.time.Instant;

/**
 * Vue d'une salle temps réel : sujet, statut de préparation, adversaire, présences et issue.
 * Le lien de partage se compose via {@code /join/{roomId}}.
 */
public record RoomView(
        String roomId,
        TopicRefView topic,
        RoomStatus status,
        boolean vsBot,
        /** Vrai si le viewer est l'initiateur (seul habilité à annuler la salle). */
        boolean canCancel,
        UserRefView opponent,
        boolean initiatorPresent,
        boolean participantPresent,
        String gameId,
        Instant createdAt,
        Instant expiresAt,
        Instant updatedAt
) {
}
