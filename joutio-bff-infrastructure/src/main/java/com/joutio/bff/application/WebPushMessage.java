package com.joutio.bff.application;

import java.time.Instant;

/**
 * Payload poussé au Service Worker : données structurées (pas de texte figé) pour que le client
 * compose la notification et résolve la route à ouvrir. {@code path} est la route applicative
 * canonique (ex. {@code /lobbies/{id}}).
 */
public record WebPushMessage(String notificationId,
                             String type,
                             String actorId,
                             String actorPseudonym,
                             String sourceId,
                             String topicId,
                             String gameId,
                             Instant expiresAt,
                             String path) {
}
