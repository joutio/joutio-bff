package com.joutio.bff.application;

/**
 * Port d'envoi Web Push. {@code GONE} signale un abonnement révoqué par le navigateur
 * (404/410) : le dispatcher le purge alors de la base.
 */
public interface WebPushGateway {

    enum SendStatus {
        DELIVERED,
        GONE,
        FAILED
    }

    SendStatus send(PushSubscription subscription, WebPushMessage message);
}
