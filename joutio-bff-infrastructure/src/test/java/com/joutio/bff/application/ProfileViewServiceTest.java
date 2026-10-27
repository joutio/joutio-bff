package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.response.ActivityViewResponse;
import com.joutio.bff.infrastructure.in.api.response.BadgeCollectionView;
import com.joutio.bff.infrastructure.in.api.response.PlayerProfileView;
import com.joutio.bff.infrastructure.in.api.response.TitlesView;
import com.joutio.bff.infrastructure.in.api.response.TopicStatView;
import com.joutio.microservice.core.domain.constant.JoutioConstants;
import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.profile.domain.model.ActivityDay;
import com.joutio.profile.domain.model.ActivityView;
import com.joutio.profile.domain.model.Badge;
import com.joutio.profile.domain.model.MasteryTier;
import com.joutio.profile.domain.model.PlayerProgress;
import com.joutio.profile.domain.model.Profile;
import com.joutio.profile.domain.model.TopicProgress;
import com.joutio.profile.domain.query.ActivityQuery;
import com.joutio.profile.domain.query.ProgressionQuery;
import com.joutio.theme.domain.model.Topic;
import com.joutio.theme.domain.query.TopicQuery;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Composition du donut « Sujets joués » : progression par thème enrichie des informations
 * éditoriales du sujet, sujets introuvables ignorés.
 */
class ProfileViewServiceTest {

    private final QueryGateway queryGateway = mock(QueryGateway.class);
    private final ProfileLookup profileLookup = mock(ProfileLookup.class);
    private final ProgressionLookup progressionLookup = mock(ProgressionLookup.class);
    private final FollowLookup followLookup = mock(FollowLookup.class);
    private final IdentityLookup identityLookup = mock(IdentityLookup.class);
    private final ProfileViewService service =
            new ProfileViewService(queryGateway, profileLookup, progressionLookup, followLookup, identityLookup);

    @Test
    void topics_enrich_progressions_and_skip_missing_topics() {
        when(queryGateway.query(
                any(ProgressionQuery.GetTopicProgressionsQuery.class),
                ArgumentMatchers.<ResponseType<List<TopicProgress>>>any()))
                .thenReturn(CompletableFuture.completedFuture(List.of(
                        new TopicProgress("topic-1", 200, 7),
                        new TopicProgress("missing", 50, 2))));
        when(queryGateway.query(
                any(TopicQuery.GetTopicsByIdsQuery.class),
                ArgumentMatchers.<ResponseType<List<Topic>>>any()))
                .thenReturn(CompletableFuture.completedFuture(List.of(
                        Topic.builder()
                                .topicId("topic-1")
                                .names(Map.of(Language.FR, "DotA"))
                                .emoji("🎮")
                                .color("#f00")
                                .imageUrl("https://example.com/img.png")
                                .build())));

        List<TopicStatView> view = service.topics("me").join();

        assertThat(view).hasSize(1);
        assertThat(view.get(0).topicId()).isEqualTo("topic-1");
        assertThat(view.get(0).names()).containsEntry(Language.FR, "DotA");
        assertThat(view.get(0).emoji()).isEqualTo("🎮");
        assertThat(view.get(0).games()).isEqualTo(7);
        assertThat(view.get(0).xp()).isEqualTo(200);
    }

    @Test
    void topics_skip_topic_lookup_when_no_progressions() {
        when(queryGateway.query(
                any(ProgressionQuery.GetTopicProgressionsQuery.class),
                ArgumentMatchers.<ResponseType<List<TopicProgress>>>any()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        List<TopicStatView> view = service.topics("me").join();

        assertThat(view).isEmpty();
    }

    @Test
    void titles_expose_reached_tiers_sorted_and_carry_the_equipped_key() {
        when(profileLookup.get("user-1")).thenReturn(CompletableFuture.completedFuture(
                Profile.builder().userId("user-1").equippedTitle("topic-2:PLATINE").build()));
        when(progressionLookup.get("user-1")).thenReturn(CompletableFuture.completedFuture(
                PlayerProgress.empty("user-1").toBuilder()
                        .xpByTopic(Map.of(
                                "topic-1", MasteryTier.OR.xpThreshold(),
                                "topic-2", MasteryTier.PLATINE.xpThreshold(),
                                "topic-3", 120,     // Fer : titre débloqué dès une partie humaine
                                "topic-4", MasteryTier.BRONZE.xpThreshold()))
                        // XP seule, aucune partie humaine : topic-4 est exclu
                        .gamesByTopic(Map.of("topic-1", 1, "topic-2", 1, "topic-3", 1))
                        .build()));
        when(queryGateway.query(
                any(TopicQuery.GetTopicsByIdsQuery.class),
                ArgumentMatchers.<ResponseType<List<Topic>>>any()))
                .thenReturn(CompletableFuture.completedFuture(List.of(
                        Topic.builder().topicId("topic-1").names(Map.of(Language.FR, "Histoire")).build(),
                        Topic.builder().topicId("topic-2").names(Map.of(Language.FR, "Java")).build(),
                        Topic.builder().topicId("topic-3").names(Map.of(Language.FR, "DotA")).build())));

        TitlesView view = service.titles("user-1").join();

        assertThat(view.equippedKey()).isEqualTo("topic-2:PLATINE");
        assertThat(view.titles()).extracting(TitlesView.TitleView::key)
                .containsExactly(
                        "topic-2:PLATINE",
                        "topic-1:OR", "topic-2:OR",
                        "topic-1:ARGENT", "topic-2:ARGENT",
                        "topic-1:BRONZE", "topic-2:BRONZE",
                        "topic-3:FER", "topic-1:FER", "topic-2:FER");
        assertThat(view.titles()).extracting(TitlesView.TitleView::tier)
                .containsExactly(
                        "PLATINE",
                        "OR", "OR",
                        "ARGENT", "ARGENT",
                        "BRONZE", "BRONZE",
                        "FER", "FER", "FER");
        assertThat(view.titles().get(0).topic().names()).containsEntry(Language.FR, "Java");
        // Le palier d'entrée porte sa progression réelle (120 XP avant Bronze).
        assertThat(view.titles().get(7).nextTier()).isEqualTo("BRONZE");
        assertThat(view.titles().get(7).tierRemainingXp())
                .isEqualTo(MasteryTier.BRONZE.xpThreshold() - 120);
    }

    @Test
    void titles_include_a_lower_tier_that_is_still_equipped() {
        when(profileLookup.get("user-1")).thenReturn(CompletableFuture.completedFuture(
                Profile.builder().userId("user-1").equippedTitle("topic-2:BRONZE").build()));
        when(progressionLookup.get("user-1")).thenReturn(CompletableFuture.completedFuture(
                PlayerProgress.empty("user-1").toBuilder()
                        .xpByTopic(Map.of("topic-2", MasteryTier.PLATINE.xpThreshold()))
                        .gamesByTopic(Map.of("topic-2", 1))
                        .build()));
        when(queryGateway.query(
                any(TopicQuery.GetTopicsByIdsQuery.class),
                ArgumentMatchers.<ResponseType<List<Topic>>>any()))
                .thenReturn(CompletableFuture.completedFuture(List.of(
                        Topic.builder().topicId("topic-2").names(Map.of(Language.FR, "Java")).build())));

        TitlesView view = service.titles("user-1").join();

        // Le titre porté peut être un palier dépassé : il doit rester résolvable par le client.
        assertThat(view.equippedKey()).isEqualTo("topic-2:BRONZE");
        assertThat(view.titles()).extracting(TitlesView.TitleView::key)
                .contains("topic-2:BRONZE", "topic-2:PLATINE");
        assertThat(view.titles()).hasSize(5);
    }

    @Test
    void titles_include_a_topic_played_against_a_human_even_at_zero_xp() {
        when(profileLookup.get("user-1")).thenReturn(CompletableFuture.completedFuture(
                Profile.builder().userId("user-1").build()));
        when(progressionLookup.get("user-1")).thenReturn(CompletableFuture.completedFuture(
                PlayerProgress.empty("user-1").toBuilder()
                        // Partie humaine terminée à 0 XP (ex. défaite 0–0) : le titre Fer existe.
                        .xpByTopic(Map.of("topic-1", 0))
                        .gamesByTopic(Map.of("topic-1", 1))
                        .build()));
        when(queryGateway.query(
                any(TopicQuery.GetTopicsByIdsQuery.class),
                ArgumentMatchers.<ResponseType<List<Topic>>>any()))
                .thenReturn(CompletableFuture.completedFuture(List.of(
                        Topic.builder().topicId("topic-1").names(Map.of(Language.FR, "Histoire")).build())));

        TitlesView view = service.titles("user-1").join();

        assertThat(view.titles()).extracting(TitlesView.TitleView::key)
                .containsExactly("topic-1:FER");
        assertThat(view.titles().getFirst().tier()).isEqualTo("FER");
    }

    @Test
    void titles_skip_the_topic_lookup_when_no_topic_is_played() {
        when(profileLookup.get("user-1")).thenReturn(CompletableFuture.completedFuture(
                Profile.builder().userId("user-1").build()));
        when(progressionLookup.get("user-1")).thenReturn(CompletableFuture.completedFuture(
                PlayerProgress.empty("user-1").toBuilder()
                        // Aucune partie humaine terminée (ex. entraînement bot) : aucun titre.
                        .xpByTopic(Map.of("topic-1", 0))
                        .build()));

        TitlesView view = service.titles("user-1").join();

        assertThat(view.equippedKey()).isNull();
        assertThat(view.titles()).isEmpty();
    }

    @Test
    void daily_streak_progress_resets_after_a_missed_day() {
        when(progressionLookup.get("user-1")).thenReturn(CompletableFuture.completedFuture(
                PlayerProgress.empty("user-1").toBuilder()
                        .currentDayStreak(5)
                        .bestDayStreak(10)
                        .lastActiveDate(LocalDate.now().minusDays(2))
                        .build()));

        BadgeCollectionView view = service.badges("user-1").join();

        assertThat(item(view, Badge.ASSIDUITY_BRONZE).progress()).isZero();
    }

    @Test
    void activity_maps_the_streak_calendar() {
        when(queryGateway.query(
                any(ActivityQuery.GetActivityQuery.class),
                ArgumentMatchers.<ResponseType<ActivityView>>any()))
                .thenReturn(CompletableFuture.completedFuture(new ActivityView(
                        "user-1",
                        3,
                        7,
                        LocalDate.parse("2026-03-03"),
                        2,
                        List.of(
                                new ActivityDay(LocalDate.parse("2026-03-02"), 1),
                                new ActivityDay(LocalDate.parse("2026-03-03"), 2)))));

        ActivityViewResponse view = service.activity("user-1",
                LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-03")).join();

        assertThat(view.currentStreak()).isEqualTo(3);
        assertThat(view.days()).hasSize(2);
        assertThat(view.days().get(1).games()).isEqualTo(2);
    }

    @Test
    void badges_expose_the_whole_catalogue_with_capped_progress() {
        int bestTopicXp = MasteryTier.OR.xpThreshold();
        when(progressionLookup.get("user-1")).thenReturn(CompletableFuture.completedFuture(
                PlayerProgress.empty("user-1").toBuilder()
                        .gamesPlayed(12)
                        .wins(3)
                        .perfectDuels(4)
                        .fastAnswersTotal(80)
                        .currentDayStreak(5)
                        .bestDayStreak(10)
                        .lastActiveDate(LocalDate.now())
                        .followersGained(7)
                        .challengesSent(2)
                        .imageCorrectTotal(20)
                        .xpByTopic(Map.of("topic-1", bestTopicXp))
                        .badges(Set.of(Badge.FIRST_WIN, Badge.DUELIST_BRONZE))
                        .build()));

        BadgeCollectionView view = service.badges("user-1").join();

        assertThat(view.earnedCount()).isEqualTo(2);
        assertThat(view.totalCount()).isEqualTo(Badge.values().length);
        assertThat(view.families()).extracting(BadgeCollectionView.FamilyView::family)
                .containsExactly("VOLUME", "VICTORY", "MASTERY", "EXPLOIT", "SERIES",
                        "ASSIDUITY", "SOCIAL", "CHALLENGES", "VISION", "PRECISION", "SPEED",
                        "EXPLORATION", "COLLECTION");

        // Volume : acquis au palier bronze (plafonné), en cours pour l'argent.
        assertThat(item(view, Badge.DUELIST_BRONZE).earned()).isTrue();
        assertThat(item(view, Badge.DUELIST_BRONZE).progress())
                .isEqualTo(Math.min(12, Badge.DUELIST_BRONZE.target()));
        assertThat(item(view, Badge.DUELIST_ARGENT).earned()).isFalse();
        assertThat(item(view, Badge.DUELIST_ARGENT).progress())
                .isEqualTo(Math.min(12, Badge.DUELIST_ARGENT.target()));
        assertThat(item(view, Badge.WINNER_BRONZE).progress())
                .isEqualTo(Math.min(3, Badge.WINNER_BRONZE.target()));

        // Maîtrise : meilleur sujet du joueur, plafonné au seuil du palier bronze.
        assertThat(item(view, Badge.MASTER_BRONZE).progress())
                .isEqualTo(Math.min(bestTopicXp, Badge.MASTER_BRONZE.target()));

        // Collection : nombre de badges acquis ; exploits : faits ponctuels.
        assertThat(item(view, Badge.COLLECTOR_BRONZE).progress())
                .isEqualTo(Math.min(2, Badge.COLLECTOR_BRONZE.target()));
        assertThat(item(view, Badge.FIRST_WIN).progress()).isEqualTo(1);
        assertThat(item(view, Badge.PERFECT).progress()).isZero();

        // Précision / Rapidité : compteurs d'exploits cumulés, plafonnés au seuil du palier.
        assertThat(item(view, Badge.PRECISION_BRONZE).progress())
                .isEqualTo(Math.min(4, Badge.PRECISION_BRONZE.target()));
        assertThat(item(view, Badge.SPEED_BRONZE).progress())
                .isEqualTo(Math.min(80, Badge.SPEED_BRONZE.target()));

        // Assiduité : série **courante** (5), pas le record historique (10) — une série
        // interrompue doit faire repartir la progression du palier suivant.
        assertThat(item(view, Badge.ASSIDUITY_BRONZE).progress())
                .isEqualTo(Math.min(5, Badge.ASSIDUITY_BRONZE.target()));

        // Social / Défis : compteurs alimentés par les autres services (abonnés, défis lancés).
        assertThat(item(view, Badge.SOCIAL_BRONZE).progress())
                .isEqualTo(Math.min(7, Badge.SOCIAL_BRONZE.target()));
        assertThat(item(view, Badge.CHALLENGES_ARGENT).progress())
                .isEqualTo(Math.min(2, Badge.CHALLENGES_ARGENT.target()));

        // Vision : bonnes réponses cumulées sur les questions illustrées.
        assertThat(item(view, Badge.VISION_ARGENT).progress())
                .isEqualTo(Math.min(20, Badge.VISION_ARGENT.target()));
    }

    @Test
    void bot_profile_exposes_only_minimal_identity() {
        when(profileLookup.get(JoutioConstants.SYSTEM_USER_ID)).thenReturn(
                CompletableFuture.completedFuture(Profile.builder()
                        .userId(JoutioConstants.SYSTEM_USER_ID)
                        .pseudonym("QuizMeUp Bot")
                        .build()));

        PlayerProfileView view = service.profileView("user-1", JoutioConstants.SYSTEM_USER_ID).join();

        assertThat(view.bot()).isTrue();
        assertThat(view.pseudonym()).isEqualTo("QuizMeUp Bot");
        assertThat(view.bio()).isNull();
        assertThat(view.country()).isNull();
        assertThat(view.presence()).isNull();
        assertThat(view.stats().played()).isZero();
        assertThat(view.followersCount()).isZero();
        assertThat(view.followingCount()).isZero();
        assertThat(view.suspended()).isFalse();
        assertThat(view.progression().rating()).isEqualTo(200);
    }

    private static BadgeCollectionView.BadgeItemView item(BadgeCollectionView view, Badge badge) {
        return view.families().stream()
                .flatMap(family -> family.badges().stream())
                .filter(candidate -> candidate.code().equals(badge.name()))
                .findFirst()
                .orElseThrow();
    }
}
