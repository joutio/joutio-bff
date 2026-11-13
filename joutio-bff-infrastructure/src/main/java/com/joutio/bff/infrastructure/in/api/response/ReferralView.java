package com.joutio.bff.infrastructure.in.api.response;

/**
 * {@code GET /api/me/referral} : code de parrainage du joueur, filleuls rattachés, filleuls qualifiés (premier duel
 * terminé, badge Recruteur) et parrain éventuel.
 */
public record ReferralView(
        String code,
        long invited,
        int qualified,
        PlayerCardView referredBy
) {
}
