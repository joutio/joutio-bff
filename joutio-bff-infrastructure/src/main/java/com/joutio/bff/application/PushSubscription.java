package com.joutio.bff.application;

import java.time.Instant;

/**
 * Abonnement Web Push d'un navigateur : endpoint du service de push + clés publiques du client
 * (p256dh/auth) nécessaires au chiffrement du payload, rattaché à un joueur.
 */
public record PushSubscription(String endpoint,
                               String userId,
                               String p256dh,
                               String auth,
                               String userAgent,
                               Instant createdAt,
                               Instant updatedAt) {
}
