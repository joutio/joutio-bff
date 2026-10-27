package com.joutio.bff.application;

import org.springframework.stereotype.Service;

/**
 * Politique de quota des duels, <b>point d'extension unique du premium</b>.
 *
 * <p>Aujourd'hui, tous les comptes reçoivent les limites configurées
 * ({@code joutio.duel.limits.*}, 5 par défaut). Quand le compte premium existera, seule cette
 * classe changera : elle lira le plan du joueur (identity/profile) et renverra une limite
 * rehaussée ou {@link Integer#MAX_VALUE}. Les gardes et contrôleurs ne connaissent que ces
 * méthodes.</p>
 */
@Service
public class DuelLimitPolicy {

    private final DuelLimitsProperties properties;

    public DuelLimitPolicy(DuelLimitsProperties properties) {
        this.properties = properties;
    }

    /** Plafond de défis PENDING créés par le joueur. {@code <= 0} = illimité. */
    public int maxPendingChallenges(String playerId) {
        return limit(properties.pendingChallenges());
    }

    /** Plafond de salons WAITING_PLAYERS créés par le joueur. {@code <= 0} = illimité. */
    public int maxOpenRooms(String playerId) {
        return limit(properties.openRooms());
    }

    /**
     * Le MVP n'a pas encore de plan premium : le joueur est traité comme gratuit. Le paramètre
     * est conservé pour que le remplacement par un lookup identity soit local à cette méthode.
     */
    private static int limit(int configured) {
        return configured <= 0 ? Integer.MAX_VALUE : configured;
    }
}
