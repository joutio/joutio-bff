package com.joutio.bff.infrastructure.in.api.response;

import java.util.List;

/**
 * Titres thématiques d'un joueur : le **titre porté** ({@code equippedKey}) et les titres qu'il a
 * débloqués — **tous les paliers atteints** de chaque sujet avec **au moins une partie humaine
 * terminée** (abandon inclus ; un entraînement bot, à 0 XP, ne débloque rien), le palier d'entrée
 * Fer compris même à 0 XP. Un joueur peut équiper un palier dépassé : la vue expose donc aussi
 * les paliers inférieurs au palier courant, pas seulement ce dernier.
 * Le libellé n'est pas composé côté serveur : le client combine le palier
 * ({@code tier}, à traduire), sa rareté (le palier lui-même) et le nom du sujet
 * ({@code topic.names}, dans la langue du lecteur).
 */
public record TitlesView(
        String equippedKey,
        List<TitleView> titles
) {

    public record TitleView(
            /** Clé stable du titre, telle qu'attendue par `PUT /api/profiles/{id}/title`. */
            String key,
            String topicId,
            /** Palier de maîtrise atteint dans ce sujet (`MasteryTier`), ex. {@code OR}. */
            String tier,
            /** Progression (0–100) vers le palier suivant du sujet. */
            int tierProgressPercent,
            /** XP restante dans ce sujet avant le palier suivant (0 au dernier palier). */
            int tierRemainingXp,
            /** Palier suivant du sujet ({@code null} au dernier palier). */
            String nextTier,
            TopicRefView topic
    ) {
    }
}
