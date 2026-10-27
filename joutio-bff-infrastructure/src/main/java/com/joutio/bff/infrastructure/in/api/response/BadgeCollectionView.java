package com.joutio.bff.infrastructure.in.api.response;

import java.util.List;

/**
 * Collection de badges d'un joueur : **tout le catalogue**, paliers verrouillés compris, groupé
 * par famille. Chaque badge porte sa progression (`progress` / `target`, plafonnée) pour donner
 * une raison de continuer après le bronze — « 7/10 duels ».
 *
 * <p>Le libellé n'est pas traduit côté serveur (`label` reste le libellé source du catalogue) :
 * le client associe le `code` à sa propre traduction, `label` servant de repli.</p>
 */
public record BadgeCollectionView(
        /** Badges acquis par le joueur. */
        int earnedCount,
        /** Taille du catalogue (tous paliers confondus). */
        int totalCount,
        List<FamilyView> families
) {

    public record FamilyView(String family, List<BadgeItemView> badges) {
    }

    public record BadgeItemView(
            String code,
            String label,
            String tier,
            /** Compteur mesuré (`BadgeMetric`) : duels, victoires, maîtrise, collection, fait ponctuel. */
            String metric,
            int target,
            boolean earned,
            /** Valeur courante du compteur, **plafonnée** à {@code target} (`target` si acquis). */
            int progress
    ) {
    }
}
