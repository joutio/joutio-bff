package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.microservice.core.domain.model.i18n.Language;

import java.util.Map;

/**
 * Vue agrégée de la fiche sujet : carte (dont l'état de suivi) + rang réel + progression.
 * {@code canManage} indique au client que le joueur courant est le créateur du sujet.
 */
public record TopicOverviewView(
        TopicCardView topic,
        /**
         * Titres **thématiques** du sujet par langue puis palier de rareté (ex.
         * `{ fr: { OR: "Historien" } }`) : alimente l'onglet « Titres » de la fiche sujet.
         * Le libellé affiché est composé côté client (palier traduit + nom du sujet).
         */
        Map<Language, Map<String, String>> titles,
        Integer myRank,
        MyProgressView myProgress,
        boolean canManage
) {

    public record MyProgressView(
            int xp,
            /** Parties humaines terminées dans le sujet (abandon inclus) — condition du titre Fer. */
            int games,
            /** Palier de **maîtrise du sujet** atteint (`MasteryTier`), ex. {@code OR}. */
            String tier,
            /** Progression (0–100) vers le palier suivant du sujet ; 100 au dernier palier. */
            int tierProgressPercent,
            /** XP restante dans ce sujet avant le palier suivant (0 au dernier palier). */
            int tierRemainingXp,
            /** Palier suivant du sujet ({@code null} au dernier palier). */
            String nextTier,
            /** Questions distinctes du sujet effectivement répondues par le joueur. */
            int completedQuestions,
            /** Nombre total de questions approuvées du sujet. */
            int totalQuestions,
            /** Complétion du sujet en pourcentage (`completedQuestions / totalQuestions`). */
            int completionPercent
    ) {
    }
}
