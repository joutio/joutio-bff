package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.profile.domain.command.ProfileCommand;
import com.joutio.profile.domain.model.MasteryTier;
import com.joutio.profile.domain.model.PlayerProgress;
import com.joutio.profile.domain.model.PlayerRewards;
import com.joutio.theme.domain.model.TopicCategory;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Une écriture de profil réussie doit purger le cache de lecture ({@link ProfileLookup}),
 * sinon les vues servent l'ancien profil jusqu'à l'expiration du TTL (sauvegarde perçue
 * comme perdue).
 */
class ProfileUpdateServiceTest {

    private final CommandGateway commandGateway = mock(CommandGateway.class);
    private final ProfileLookup profileLookup = mock(ProfileLookup.class);
    private final ProgressionLookup progressionLookup = mock(ProgressionLookup.class);
    private final RewardLookup rewardLookup = mock(RewardLookup.class);
    private final AvatarEntitlementPolicy avatarEntitlementPolicy = mock(AvatarEntitlementPolicy.class);
    private final PremiumAccessPolicy premiumAccessPolicy = mock(PremiumAccessPolicy.class);
    private final ProfileUpdateService service =
            new ProfileUpdateService(commandGateway, profileLookup, progressionLookup, rewardLookup,
                    avatarEntitlementPolicy, premiumAccessPolicy);

    @Test
    void updateAvatarChecksPremiumEntitlementBeforeDispatch() {
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(rewardLookup.get("u1")).thenReturn(
                CompletableFuture.completedFuture(PlayerRewards.empty("u1")));

        service.updateAvatarOptions("u1", "u1", "{\"style\":\"base\"}").join();

        verify(avatarEntitlementPolicy).assertCanSave("{\"style\":\"base\"}", false, false);
        verify(profileLookup).invalidate("u1");
    }

    @Test
    void updateAvatarPassesMicahRewardToThePolicy() {
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(rewardLookup.get("u1")).thenReturn(CompletableFuture.completedFuture(
                new PlayerRewards("u1", java.util.List.of(new com.joutio.profile.domain.model.RewardGrant(
                        com.joutio.profile.domain.model.RewardCode.MICAH_AVATAR,
                        com.joutio.profile.domain.model.RewardSource.BADGE,
                        java.time.Instant.now())))));

        service.updateAvatarOptions("u1", "u1", "{\"style\":\"micah\"}").join();

        verify(avatarEntitlementPolicy).assertCanSave(
                "{\"style\":\"micah\"}", false, true);
    }

    @Test
    void updatePseudonymInvalidatesCacheOnSuccess() {
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.updatePseudonym("u1", "u1", "Nouveau").join();

        verify(profileLookup).invalidate("u1");
    }

    @Test
    void updateLanguageInvalidatesCacheOnSuccess() {
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.updateLanguage("u1", "u1", Language.EN).join();

        verify(profileLookup).invalidate("u1");
    }

    @Test
    void updateInterests_sendsOrderedCodesAndInvalidatesCache() {
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.updateInterests("u1", "u1",
                java.util.List.of(TopicCategory.HISTORY, TopicCategory.SCIENCE)).join();

        verify(commandGateway).send(new ProfileCommand.UpdateProfileInterestsCommand(
                "u1", "u1", java.util.List.of("HISTORY", "SCIENCE")));
        verify(profileLookup).invalidate("u1");
    }

    @Test
    void updateInterests_rejectsDuplicatesBeforeDispatch() {
        assertThatThrownBy(() -> service.updateInterests("u1", "u1",
                java.util.List.of(TopicCategory.HISTORY, TopicCategory.HISTORY)).join())
                .isInstanceOf(BffProblems.InvalidInterestsProblem.class);
        verifyNoInteractions(commandGateway);
        verifyNoInteractions(profileLookup);
    }

    @Test
    void updateBannerInvalidatesCacheOnSuccess() {
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.updateBanner("u1", "u1", "https://media.example.test/banner.jpg").join();

        verify(profileLookup).invalidate("u1");
    }

    @Test
    void failedCommandDoesNotInvalidateCache() {
        CompletableFuture<Object> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("boom"));
        when(commandGateway.send(any())).thenReturn(failed);

        assertThatThrownBy(() -> service.updateBio("u1", "u1", "bio").join())
                .isInstanceOf(CompletionException.class);
        verifyNoInteractions(profileLookup);
    }

    @Test
    void equippedTitle_sendsTheCommandAndInvalidatesCacheWhenEarned() {
        stubProgress(Map.of("topic-1", MasteryTier.OR.xpThreshold() + 100));
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.updateEquippedTitle("u1", "u1", "topic-1:OR").join();

        verify(commandGateway).send(new ProfileCommand.UpdateEquippedTitleCommand(
                "u1", "u1", "topic-1:OR"));
        verify(profileLookup).invalidate("u1");
    }

    @Test
    void equippedTitle_isRejectedAndNoCommandIsSentWhenTheTierIsNotReached() {
        stubProgress(Map.of("topic-1", MasteryTier.OR.xpThreshold() + 100));

        assertThatThrownBy(() -> service.updateEquippedTitle("u1", "u1", "topic-1:PLATINE").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(BffProblems.TitleNotEarnedProblem.class);
        verifyNoInteractions(commandGateway);
        verifyNoInteractions(profileLookup);
    }

    @Test
    void equippedTitle_noviceIsAllowedOnceTheTopicHasBeenPlayed() {
        stubProgress(Map.of("topic-1", 120));
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.updateEquippedTitle("u1", "u1", "topic-1:FER").join();

        verify(commandGateway).send(new ProfileCommand.UpdateEquippedTitleCommand(
                "u1", "u1", "topic-1:FER"));
        verify(profileLookup).invalidate("u1");
    }

    @Test
    void equippedTitle_noviceOnUnplayedTopic_isRejectedAsNotEarned() {
        stubProgress(Map.of("topic-1", 0), Map.of());

        assertThatThrownBy(() -> service.updateEquippedTitle("u1", "u1", "topic-1:FER").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(BffProblems.TitleNotEarnedProblem.class);
        verifyNoInteractions(commandGateway);
    }

    @Test
    void equippedTitle_noviceIsAllowedAfterAHumanGameEvenAtZeroXp() {
        stubProgress(Map.of("topic-1", 0), Map.of("topic-1", 1));
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.updateEquippedTitle("u1", "u1", "topic-1:FER").join();

        verify(commandGateway).send(new ProfileCommand.UpdateEquippedTitleCommand(
                "u1", "u1", "topic-1:FER"));
        verify(profileLookup).invalidate("u1");
    }

    @Test
    void equippedTitle_noviceOnXpOnlyTopicWithoutHumanGame_isRejected() {
        stubProgress(Map.of("topic-1", 120), Map.of());

        assertThatThrownBy(() -> service.updateEquippedTitle("u1", "u1", "topic-1:FER").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(BffProblems.TitleNotEarnedProblem.class);
        verifyNoInteractions(commandGateway);
    }

    @Test
    void equippedTitle_withUnknownTopic_isRejectedAsNotEarned() {
        stubProgress(Map.of("topic-1", MasteryTier.DIAMANT.xpThreshold()));

        assertThatThrownBy(() -> service.updateEquippedTitle("u1", "u1", "topic-9:DIAMANT").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(BffProblems.TitleNotEarnedProblem.class);
        verifyNoInteractions(commandGateway);
    }

    @Test
    void equippedTitle_withMalformedKey_isRejectedWithoutReadingProgression() {
        assertThatThrownBy(() -> service.updateEquippedTitle("u1", "u1", "topic-1").join())
                .isInstanceOf(BffProblems.InvalidEquippedTitleProblem.class);
        verifyNoInteractions(progressionLookup);
        verifyNoInteractions(commandGateway);
    }

    @Test
    void equippedTitle_withNull_clearsItWithoutReadingProgression() {
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.updateEquippedTitle("u1", "u1", null).join();

        verify(commandGateway).send(new ProfileCommand.UpdateEquippedTitleCommand("u1", "u1", null));
        verifyNoInteractions(progressionLookup);
    }

    private void stubProgress(Map<String, Integer> xpByTopic) {
        Map<String, Integer> gamesByTopic = new java.util.HashMap<>();
        xpByTopic.keySet().forEach(topicId -> gamesByTopic.put(topicId, 1));
        stubProgress(xpByTopic, gamesByTopic);
    }

    private void stubProgress(Map<String, Integer> xpByTopic,
                              Map<String, Integer> gamesByTopic) {
        when(progressionLookup.get("u1")).thenReturn(CompletableFuture.completedFuture(
                PlayerProgress.empty("u1").toBuilder()
                        .xpByTopic(xpByTopic)
                        .gamesByTopic(gamesByTopic)
                        .build()));
    }
}
