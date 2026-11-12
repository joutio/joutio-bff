package com.joutio.bff.application;

import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Politique de quota des duels, <b>point d'extension unique du premium</b>.
 *
 * <p>Le plan du joueur est relu dans identity ({@link PremiumAccessPolicy#premium(String)}) :
 * utilisable hors du thread HTTP, et frais après une attribution ou un retrait. Les gardes et
 * contrôleurs ne connaissent que ces méthodes.</p>
 */
@Service
public class DuelLimitPolicy {

    /** Limites résolues pour un joueur ({@link Integer#MAX_VALUE} = illimité). */
    public record DuelLimits(int pendingChallenges, int openRooms, int botRooms) {
    }

    private final DuelLimitsProperties properties;
    private final PremiumAccessPolicy premiumAccessPolicy;

    public DuelLimitPolicy(DuelLimitsProperties properties, PremiumAccessPolicy premiumAccessPolicy) {
        this.properties = properties;
        this.premiumAccessPolicy = premiumAccessPolicy;
    }

    /** Plafond de défis PENDING créés par le joueur. */
    public CompletableFuture<Integer> maxPendingChallenges(String playerId) {
        return premiumAccessPolicy.premium(playerId).thenApply(premium -> limits(premium).pendingChallenges());
    }

    /** Plafond de salons WAITING_PLAYERS entre joueurs créés par le joueur. */
    public CompletableFuture<Integer> maxOpenRooms(String playerId) {
        return premiumAccessPolicy.premium(playerId).thenApply(premium -> limits(premium).openRooms());
    }

    /** Plafond de salons bot ouverts : identique pour tous. */
    public int maxBotRooms() {
        return limit(properties.botRooms());
    }

    /** Limites d'un plan, exposées dans {@code MeView}. */
    public DuelLimits limits(boolean premium) {
        return new DuelLimits(
                limit(premium ? properties.premiumPendingChallenges() : properties.pendingChallenges()),
                limit(premium ? properties.premiumOpenRooms() : properties.openRooms()),
                limit(properties.botRooms()));
    }

    private static int limit(int configured) {
        return configured <= 0 ? Integer.MAX_VALUE : configured;
    }
}
