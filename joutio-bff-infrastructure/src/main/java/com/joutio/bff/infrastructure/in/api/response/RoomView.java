package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.matchmaking.domain.model.RoomOrigin;
import com.joutio.matchmaking.domain.model.RoomStatus;

import java.time.Instant;

/**
 * Vue d'une salle : sujet, statut (invitation, attente, préparation, fin), adversaire, présences et issue. Un défi est
 * une salle {@code INVITED}. Le lien de partage se compose via {@code go.joutio.com/j/{roomId}}.
 */
public record RoomView(
        String roomId,
        TopicRefView topic,
        RoomStatus status,
        boolean vsBot,
        /** Origine : salon partagé ({@code SHARED}), défi ({@code CHALLENGE}), revanche ({@code REMATCH}), appariement, bot. */
        RoomOrigin origin,
        /** Second joueur nommé, ou premier venu par le lien. */
        RoomAccess access,
        /** Vrai si le viewer est l'initiateur (seul habilité à annuler la salle et à relancer l'invitation). */
        boolean canCancel,
        RoomOpponentView opponent,
        boolean initiatorPresent,
        boolean participantPresent,
        String gameId,
        /** Partie terminée d'origine d'une revanche. */
        String sourceGameId,
        Instant createdAt,
        /** Échéance de l'étape en cours : réponse à l'invitation, puis attente des joueurs. */
        Instant expiresAt,
        /** Prochaine relance possible de l'invitation ({@code INVITED} seulement). */
        Instant remindableAt,
        Instant updatedAt
) {
}
