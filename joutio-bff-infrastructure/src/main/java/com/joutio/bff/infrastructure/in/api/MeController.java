package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.AccountDeletionService;
import com.joutio.bff.application.MeService;
import com.joutio.bff.application.ProfileUpdateService;
import com.joutio.bff.application.RewardService;
import com.joutio.bff.application.TopicRecommendationService;
import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.request.DeleteAccountRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateInterestsRequest;
import com.joutio.bff.infrastructure.in.api.response.MeView;
import com.joutio.bff.infrastructure.in.api.response.RewardsView;
import com.joutio.bff.infrastructure.in.api.response.TopicCardView;
import com.joutio.microservice.security.SecurityHelper;
import com.joutio.theme.domain.model.TopicCategory;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /api/me} — joueur courant (profil, progression, compteurs, badge de défis).
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final MeService meService;
    private final AccountDeletionService accountDeletionService;
    private final ProfileUpdateService profileUpdateService;
    private final RewardService rewardService;
    private final TopicRecommendationService topicRecommendationService;

    public MeController(MeService meService,
                        AccountDeletionService accountDeletionService,
                        ProfileUpdateService profileUpdateService,
                        RewardService rewardService,
                        TopicRecommendationService topicRecommendationService) {
        this.meService = meService;
        this.accountDeletionService = accountDeletionService;
        this.profileUpdateService = profileUpdateService;
        this.rewardService = rewardService;
        this.topicRecommendationService = topicRecommendationService;
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<MeView>> me() {
        return meService.me(SecurityHelper.getUserId()).thenApply(ResponseEntity::ok);
    }

    /** Catalogue des récompenses du joueur courant, verrouillées comprises. */
    @GetMapping("/rewards")
    public CompletableFuture<ResponseEntity<RewardsView>> rewards() {
        return rewardService.rewards(SecurityHelper.getUserId()).thenApply(ResponseEntity::ok);
    }

    /** Termine (ou passe) l'onboarding du joueur courant. */
    @PostMapping("/onboarding/complete")
    public CompletableFuture<ResponseEntity<MeView>> completeOnboarding() {
        String userId = SecurityHelper.getUserId();
        boolean premium = meService.isPremium();
        return profileUpdateService.completeOnboarding(userId)
                .thenCompose(_ -> meService.me(userId, premium))
                // La projection profile est asynchrone : le flag renvoyé doit refléter la commande
                // déjà acceptée, sinon le guard web renvoie le joueur sur l'onboarding.
                .thenApply(view -> ResponseEntity.ok(view.withOnboardingCompleted(true)));
    }

    /**
     * Recommandations pour l'onboarding : intérêts en cours de saisie, avant persistance.
     * Retourne jusqu'à 3 sujets jouables, ordonnés selon les intérêts (premier = principal).
     */
    @GetMapping("/onboarding/recommendations")
    public CompletableFuture<ResponseEntity<List<TopicCardView>>> onboardingRecommendations(
            @RequestParam List<TopicCategory> interests) {
        if (interests == null || interests.isEmpty()) {
            throw new BffProblems.InvalidInterestsProblem("At least one interest is required");
        }
        ProfileUpdateService.validateInterests(interests);
        return topicRecommendationService.recommend(interests, Set.of(), 3)
                .thenApply(ResponseEntity::ok);
    }

    /** Remplace les centres d'intérêt du joueur courant (0 à 3 codes ordonnés). */
    @PutMapping("/interests")
    public CompletableFuture<ResponseEntity<Void>> updateInterests(
            @Valid @RequestBody UpdateInterestsRequest request) {
        String userId = SecurityHelper.getUserId();
        return profileUpdateService.updateInterests(userId, userId, request.interests())
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    /** Envoie le code OTP de confirmation de suppression à l'email du compte. */
    @PostMapping("/deletion-code")
    public ResponseEntity<Void> requestDeletionCode() {
        accountDeletionService.requestCode(SecurityHelper.getUserId());
        return ResponseEntity.accepted().build();
    }

    /** Supprime définitivement le compte après vérification du code OTP. */
    @DeleteMapping
    public ResponseEntity<Void> delete(@Valid @RequestBody DeleteAccountRequest request) {
        accountDeletionService.delete(SecurityHelper.getUserId(), request.code());
        return ResponseEntity.accepted().build();
    }
}
