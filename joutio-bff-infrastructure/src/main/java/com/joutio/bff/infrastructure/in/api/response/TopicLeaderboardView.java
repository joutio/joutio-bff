package com.joutio.bff.infrastructure.in.api.response;

/**
 * Classement d'un sujet : page d'entrées + rang du joueur courant dans la même portée.
 */
public record TopicLeaderboardView(
        PageResponse<EntryView> entries,
        EntryView me
) {

    public record EntryView(
            int rank,
            String userId,
            String pseudonym,
            String avatarOptions,
            String country,
            /** Elo global courant (bordure d'avatar) et statut provisoire. */
            int rating,
            boolean provisional,
            int totalXp,
            int monthlyXp,
            boolean suspended
    ) {
    }
}
