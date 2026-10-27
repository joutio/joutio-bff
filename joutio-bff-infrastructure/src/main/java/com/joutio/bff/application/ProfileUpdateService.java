package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.profile.domain.command.ProfileCommand;
import com.joutio.profile.domain.model.MasteryTier;
import com.joutio.profile.domain.model.PlayerTitle;
import com.joutio.profile.domain.model.RewardCode;
import com.joutio.theme.domain.model.TopicCategory;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Écritures du profil : envoie la commande Axon puis **purge le cache de lecture**
 * ({@link ProfileLookup}) sur succès. Sans cette invalidation, {@code GET /api/me} et les vues
 * enrichies servent l'ancien profil jusqu'à l'expiration du TTL (30 s), donnant l'impression
 * d'une sauvegarde perdue. L'invalidation s'applique aussi aux commandes no-op (valeur
 * inchangée) : la prochaine lecture re-projette la vérité.
 */
@Service
public class ProfileUpdateService {

    private final CommandGateway commandGateway;
    private final ProfileLookup profileLookup;
    private final ProgressionLookup progressionLookup;
    private final RewardLookup rewardLookup;
    private final AvatarEntitlementPolicy avatarEntitlementPolicy;
    private final PremiumAccessPolicy premiumAccessPolicy;

    public ProfileUpdateService(CommandGateway commandGateway,
                                ProfileLookup profileLookup,
                                ProgressionLookup progressionLookup,
                                RewardLookup rewardLookup,
                                AvatarEntitlementPolicy avatarEntitlementPolicy,
                                PremiumAccessPolicy premiumAccessPolicy) {
        this.commandGateway = commandGateway;
        this.profileLookup = profileLookup;
        this.progressionLookup = progressionLookup;
        this.rewardLookup = rewardLookup;
        this.avatarEntitlementPolicy = avatarEntitlementPolicy;
        this.premiumAccessPolicy = premiumAccessPolicy;
    }

    /** Fin explicite ou skip de l'onboarding ; idempotent côté agrégat. */
    public CompletableFuture<Void> completeOnboarding(String userId) {
        return dispatch(userId, new ProfileCommand.CompleteOnboardingCommand(userId, java.time.Instant.now()));
    }

    public CompletableFuture<Void> updatePseudonym(String userId, String requestedBy, String pseudonym) {
        return dispatch(userId,
                new ProfileCommand.UpdateProfilePseudonymCommand(userId, requestedBy, pseudonym));
    }

    public CompletableFuture<Void> updateBio(String userId, String requestedBy, String bio) {
        return dispatch(userId,
                new ProfileCommand.UpdateProfileBioCommand(userId, requestedBy, bio));
    }

    public CompletableFuture<Void> updateCountry(String userId, String requestedBy, String country) {
        return dispatch(userId,
                new ProfileCommand.UpdateProfileCountryCommand(userId, requestedBy, country));
    }

    public CompletableFuture<Void> updateAvatarOptions(String userId, String requestedBy, String avatarOptions) {
        // Capture le rôle sur le thread HTTP : le callback Axon peut perdre le SecurityContext.
        boolean premium = premiumAccessPolicy.isPremium();
        return rewardLookup.get(userId).thenCompose(rewards -> {
            avatarEntitlementPolicy.assertCanSave(
                    avatarOptions, premium, rewards.has(RewardCode.MICAH_AVATAR));
            return dispatch(userId,
                    new ProfileCommand.UpdateProfileAvatarCommand(userId, requestedBy, avatarOptions));
        });
    }

    public CompletableFuture<Void> updateBanner(String userId, String requestedBy, String bannerUrl) {
        return dispatch(userId,
                new ProfileCommand.UpdateProfileBannerCommand(userId, requestedBy, bannerUrl));
    }

    public CompletableFuture<Void> updateLanguage(String userId, String requestedBy, Language language) {
        return dispatch(userId,
                new ProfileCommand.UpdateProfileLanguageCommand(userId, requestedBy, language));
    }

    /**
     * Centres d'intérêt : codes ordonnés (premier = univers principal), max 3, sans doublon.
     * L'enum {@link TopicCategory} est la source de vérité des codes autorisés ; le domaine
     * profile ne stocke que des chaînes (frontière de bounded context).
     */
    public CompletableFuture<Void> updateInterests(String userId, String requestedBy,
                                                   List<TopicCategory> interests) {
        List<String> codes = validateInterests(interests);
        return dispatch(userId,
                new ProfileCommand.UpdateProfileInterestsCommand(userId, requestedBy, codes));
    }

    public static List<String> validateInterests(List<TopicCategory> interests) {
        if (interests == null) {
            throw new BffProblems.InvalidInterestsProblem("Interests are required");
        }
        if (interests.size() > 3) {
            throw new BffProblems.InvalidInterestsProblem("At most 3 interests are allowed");
        }
        LinkedHashSet<TopicCategory> unique = new LinkedHashSet<>(interests);
        if (unique.size() != interests.size()) {
            throw new BffProblems.InvalidInterestsProblem("Interest codes must be unique");
        }
        return unique.stream().map(Enum::name).toList();
    }

    /**
     * Titre thématique porté. La commande ne porte que la clé : la **légitimité** du titre
     * (sujet avec **au moins une partie humaine terminée** — abandon inclus, entraînement bot
     * exclu — et palier réellement atteint dans ce sujet) est vérifiée **ici**, avant l'envoi —
     * l'agrégat ne connaît pas la progression (`best-practices/.backend/command-preparation.md`).
     * {@code titleKey} {@code null} efface le titre porté.
     */
    public CompletableFuture<Void> updateEquippedTitle(String userId, String requestedBy, String titleKey) {
        if (titleKey == null) {
            return dispatch(userId,
                    new ProfileCommand.UpdateEquippedTitleCommand(userId, requestedBy, null));
        }

        PlayerTitle title = PlayerTitle.parse(titleKey)
                .orElseThrow(() -> new BffProblems.InvalidEquippedTitleProblem(titleKey));

        return progressionLookup.get(userId).thenCompose(progress -> {
            int topicXp = progress.xpForTopic(title.topicId());
            MasteryTier reached = MasteryTier.tierFor(topicXp);
            if (progress.gamesForTopic(title.topicId()) <= 0
                    || reached.ordinal() < title.tier().ordinal()) {
                throw new BffProblems.TitleNotEarnedProblem(titleKey, reached.name());
            }

            return dispatch(userId,
                    new ProfileCommand.UpdateEquippedTitleCommand(userId, requestedBy, titleKey));
        });
    }

    private CompletableFuture<Void> dispatch(String userId, ProfileCommand command) {
        return commandGateway.send(command).thenRun(() -> profileLookup.invalidate(userId));
    }
}
