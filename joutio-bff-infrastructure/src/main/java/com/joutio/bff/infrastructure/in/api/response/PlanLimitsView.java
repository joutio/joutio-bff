package com.joutio.bff.infrastructure.in.api.response;

/**
 * Limites du plan du joueur courant (gratuit ou Premium), appliquées par le BFF.
 * {@link Integer#MAX_VALUE} = illimité.
 */
public record PlanLimitsView(
        int pendingChallenges,
        int openRooms,
        int botRooms,
        int publishedTopics,
        int questionsPerTopic
) {
}
