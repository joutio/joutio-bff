package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.microservice.core.domain.model.i18n.Language;

import java.util.List;

/**
 * Joueur courant (coquille, écrans profil) : profil + progression + compteurs d'abonnements.
 */
public record MeView(
        String userId,
        String email,
        String pseudonym,
        String bio,
        String country,
        String avatarOptions,
        String bannerUrl,
        /** Clé du titre thématique **porté** (`topicId:TIER`) ; {@code null} = aucun titre. */
        String equippedTitle,
        Language language,
        ProgressionView progression,
        DuelStatsView stats,
        long followingCount,
        long followersCount,
        /** Onboarding terminé (ou première partie humaine jouée). */
        boolean onboardingCompleted,
        /** Récompenses persistées du joueur (`MICAH_AVATAR`, …). */
        List<String> rewards,
        /** Styles d'avatar DiceBear débloqués (admin ou abonnement Premium). */
        boolean premium,
        /**
         * Centres d'intérêt : codes {@code TopicCategory} ordonnés (premier = univers principal),
         * max 3. Liste vide = aucun intérêt.
         */
        List<String> interests
) {

    /** Copie avec le flag forcé (la projection profile peut être en retard juste après la commande). */
    public MeView withOnboardingCompleted(boolean completed) {
        return new MeView(userId, email, pseudonym, bio, country, avatarOptions, bannerUrl,
                equippedTitle, language, progression, stats, followingCount, followersCount, completed,
                rewards, premium, interests);
    }
}
