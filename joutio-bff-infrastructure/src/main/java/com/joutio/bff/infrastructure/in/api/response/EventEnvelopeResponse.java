package com.joutio.bff.infrastructure.in.api.response;

import java.time.Instant;

/**
 * Enveloppe d'événement exposée au web (contrat Jackson du BFF) : le BFF y mappe le payload du
 * domaine (reçu typé via l'{@code EventEnvelope} du bus, grâce au codec SDK) vers son DTO de
 * notification ({@code GameNotification}, {@code TicketNotification}, {@code SocialNotification}).
 *
 * <p>Contrairement à l'{@code EventEnvelope} du SDK (transport, sans annotation), ce DTO est le
 * point d'ancrage des annotations Jackson du contrat web ; {@code eventType} porte le type web
 * ({@code GAME_CREATED}, {@code MATCHED}, {@code ROOM_COMPLETED}…), jamais le nom de classe
 * interne.</p>
 */
public record EventEnvelopeResponse(
        String aggregateId,
        long sequenceNumber,
        Instant timestamp,
        String eventType,
        Object payload
) {

    public static EventEnvelopeResponse of(String aggregateId,
                                           long sequenceNumber,
                                           Instant timestamp,
                                           String eventType,
                                           Object payload) {
        return new EventEnvelopeResponse(aggregateId, sequenceNumber, timestamp, eventType, payload);
    }
}
