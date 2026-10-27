package com.joutio.bff.infrastructure.in.api.response;

/**
 * Fiche joueur complète (profil public + progression + stats + compteurs + présence + suivi).
 */
public record PlayerProfileView(
        String userId,
        String pseudonym,
        String bio,
        String country,
        String avatarOptions,
        String bannerUrl,
        /** Clé du titre thématique **porté** par le joueur (`topicId:TIER`) ; {@code null} = aucun. */
        String equippedTitle,
        boolean isMe,
        boolean following,
        PresenceView presence,
        ProgressionView progression,
        DuelStatsView stats,
        long followersCount,
        long followingCount,
        boolean suspended,
        /** Compte système bot : la fiche web se limite à l'avatar et au nom. */
        boolean bot
) {
}
