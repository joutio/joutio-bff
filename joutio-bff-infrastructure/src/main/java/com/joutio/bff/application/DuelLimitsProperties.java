package com.joutio.bff.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limites de création de duels ({@code joutio.duel.limits.*}), surchargeables par GitOps.
 * Gratuit / Premium pour les défis et salons entre joueurs ; les salons bot ont un garde-fou
 * anti-spam identique pour tous (décision du 2026-11-12, premium/CONTEXT.md).
 */
@ConfigurationProperties(prefix = "joutio.duel.limits")
public record DuelLimitsProperties(
        /** Défis PENDING créés par un joueur gratuit (0 = illimité). */
        @DefaultValue("5") int pendingChallenges,
        /** Salons WAITING_PLAYERS (entre joueurs) créés par un joueur gratuit (0 = illimité). */
        @DefaultValue("5") int openRooms,
        /** Défis PENDING créés par un joueur Premium (0 = illimité). */
        @DefaultValue("20") int premiumPendingChallenges,
        /** Salons WAITING_PLAYERS (entre joueurs) créés par un joueur Premium (0 = illimité). */
        @DefaultValue("20") int premiumOpenRooms,
        /** Salons bot ouverts simultanément, pour tous (0 = illimité). */
        @DefaultValue("5") int botRooms) {
}
