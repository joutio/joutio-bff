package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.profile.domain.command.ProfileCommand;
import com.joutio.profile.domain.model.MasteryTier;
import com.joutio.profile.domain.model.PlayerUnlocks;
import com.joutio.profile.domain.model.TitleUnlock;
import com.joutio.profile.domain.model.PlayerRewards;
import com.joutio.theme.domain.model.TopicCategory;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Écritures de profil : validations locales et commandes envoyées. */
class ProfileUpdateServiceTest {

    private final CommandGateway commandGateway = mock(CommandGateway.class);
    private final ProgressionLookup progressionLookup = mock(ProgressionLookup.class);
    private final RewardLookup rewardLookup = mock(RewardLookup.class);
    private final AvatarEntitlementPolicy avatarEntitlementPolicy = mock(AvatarEntitlementPolicy.class);
    private final PremiumAccessPolicy premiumAccessPolicy = mock(PremiumAccessPolicy.class);
    private final ProfileUpdateService service =
            new ProfileUpdateService(commandGateway, progressionLookup, rewardLookup,
                    avatarEntitlementPolicy, premiumAccessPolicy);

    @Test
    void updateAvatarChecksPremiumEntitlementBeforeDispatch() {
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(rewardLookup.get("u1")).thenReturn(
                CompletableFuture.completedFuture(PlayerRewards.empty("u1")));

        service.updateAvatarOptions("u1", "u1", "{\"style\":\"base\"}").join();

        verify(avatarEntitlementPolicy).assertCanSave("{\"style\":\"base\"}", false, false);
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

    }

    @Test
    void updateLanguageInvalidatesCacheOnSuccess() {
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.updateLanguage("u1", "u1", Language.EN).join();

    }

    @Test
    void updateInterests_sendsOrderedCodesAndInvalidatesCache() {
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.updateInterests("u1", "u1",
                java.util.List.of(TopicCategory.HISTORY, TopicCategory.SCIENCE)).join();

        verify(commandGateway).send(new ProfileCommand.UpdateProfileInterestsCommand(
                "u1", "u1", java.util.List.of("HISTORY", "SCIENCE")));
    }

    @Test
    void updateInterests_rejectsDuplicatesBeforeDispatch() {
        assertThatThrownBy(() -> service.updateInterests("u1", "u1",
                java.util.List.of(TopicCategory.HISTORY, TopicCategory.HISTORY)).join())
                .isInstanceOf(BffProblems.InvalidInterestsProblem.class);
        verifyNoInteractions(commandGateway);
    }

    @Test
    void updateBannerInvalidatesCacheOnSuccess() {
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.updateBanner("u1", "u1", "https://media.example.test/banner.jpg").join();

    }

    @Test
    void failedCommandDoesNotInvalidateCache() {
        CompletableFuture<Object> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("boom"));
        when(commandGateway.send(any())).thenReturn(failed);

        assertThatThrownBy(() -> service.updateBio("u1", "u1", "bio").join())
                .isInstanceOf(CompletionException.class);
    }

    @Test
    void equippedTitle_sendsTheCommandAndInvalidatesCacheWhenEarned() {
        stubUnlocks(title("topic-1", MasteryTier.FER), title("topic-1", MasteryTier.BRONZE),
                title("topic-1", MasteryTier.ARGENT), title("topic-1", MasteryTier.OR));
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.updateEquippedTitle("u1", "u1", "topic-1:OR").join();

        verify(commandGateway).send(new ProfileCommand.UpdateEquippedTitleCommand(
                "u1", "u1", "topic-1:OR"));
    }

    @Test
    void equippedTitle_isRejectedAndNoCommandIsSentWhenTheTierIsNotUnlocked() {
        stubUnlocks(title("topic-1", MasteryTier.FER), title("topic-1", MasteryTier.OR));

        assertThatThrownBy(() -> service.updateEquippedTitle("u1", "u1", "topic-1:PLATINE").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(BffProblems.TitleNotEarnedProblem.class);
        verifyNoInteractions(commandGateway);
    }

    @Test
    void equippedTitle_ironIsAllowedOnceUnlocked() {
        stubUnlocks(title("topic-1", MasteryTier.FER));
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.updateEquippedTitle("u1", "u1", "topic-1:FER").join();

        verify(commandGateway).send(new ProfileCommand.UpdateEquippedTitleCommand(
                "u1", "u1", "topic-1:FER"));
    }

    @Test
    void equippedTitle_ironOnUnplayedTopic_isRejectedAsNotEarned() {
        stubUnlocks();

        assertThatThrownBy(() -> service.updateEquippedTitle("u1", "u1", "topic-1:FER").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(BffProblems.TitleNotEarnedProblem.class);
        verifyNoInteractions(commandGateway);
    }

    @Test
    void equippedTitle_withUnknownTopic_isRejectedAsNotEarned() {
        stubUnlocks(title("topic-1", MasteryTier.DIAMANT));

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

    private void stubUnlocks(TitleUnlock... titles) {
        when(progressionLookup.getUnlocks("u1")).thenReturn(CompletableFuture.completedFuture(
                new PlayerUnlocks("u1", List.of(titles), List.of())));
    }

    private static TitleUnlock title(String topicId, MasteryTier tier) {
        return new TitleUnlock("u1", topicId, tier, Instant.parse("2026-11-15T10:00:00Z"));
    }
}
