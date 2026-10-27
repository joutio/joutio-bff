package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.FollowLookup;
import com.joutio.bff.application.ProfileUpdateService;
import com.joutio.bff.application.ProfileViewService;
import com.joutio.bff.infrastructure.in.api.problem.BffProblemExceptionHandler;
import com.joutio.bff.infrastructure.in.api.response.ActivityViewResponse;
import com.joutio.bff.infrastructure.in.api.response.BadgeCollectionView;
import com.joutio.bff.infrastructure.in.api.response.DuelStatsView;
import com.joutio.bff.infrastructure.in.api.response.PageResponse;
import com.joutio.bff.infrastructure.in.api.response.PlayerProfileView;
import com.joutio.bff.infrastructure.in.api.response.ProgressionView;
import com.joutio.bff.infrastructure.in.api.response.TitlesView;
import com.joutio.bff.infrastructure.in.api.response.TopicRefView;
import com.joutio.bff.infrastructure.in.api.response.TopicStatView;
import com.joutio.game.domain.model.GameHistoryOutcome;
import com.joutio.game.domain.model.GameHistorySort;
import com.joutio.game.domain.model.GamePlayerType;
import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.social.domain.command.UserFollowerCommand;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileControllerTest {

    private static final String USER_ID = "user-1";

    private final ProfileViewService profileViewService = mock(ProfileViewService.class);
    private final ProfileUpdateService profileUpdateService = mock(ProfileUpdateService.class);
    private final FollowLookup followLookup = mock(FollowLookup.class);
    private final CommandGateway commandGateway = mock(CommandGateway.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ProfileController(profileViewService, profileUpdateService, followLookup, commandGateway))
                .setControllerAdvice(new BffProblemExceptionHandler())
                .build();
        TestSecurity.authenticate(USER_ID);
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    void profile_returns_enriched_view() throws Exception {
        PlayerProfileView view = new PlayerProfileView("user-2", "Bravo", null, "FR", null, null,
                null,
                false, false, null,
                new ProgressionView(List.of(), 200, 200, 0, true),
                new DuelStatsView(4, 3, 1, 0, 75, 160, 2, 3), 5, 7, false, false);
        when(profileViewService.profileView(USER_ID, "user-2"))
                .thenReturn(CompletableFuture.completedFuture(view));

        MvcResult result = mockMvc.perform(get("/api/profiles/user-2"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-2"))
                .andExpect(jsonPath("$.stats.wins").value(3))
                .andExpect(jsonPath("$.followersCount").value(5));
    }

    @Test
    void games_returns_page() throws Exception {
        when(profileViewService.games("user-2", null, null, null, null, GameHistorySort.RECENT, 0, 20))
                .thenReturn(CompletableFuture.completedFuture(PageResponse.of(List.of(), 0, 20, 0)));

        MvcResult result = mockMvc.perform(get("/api/profiles/user-2/games"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void games_forwards_filters_and_sort() throws Exception {
        when(profileViewService.games("user-2", "topic-1", null, GamePlayerType.BOT,
                GameHistoryOutcome.WIN, GameHistorySort.SCORE, 2, 10))
                .thenReturn(CompletableFuture.completedFuture(PageResponse.of(List.of(), 2, 10, 0)));

        MvcResult result = mockMvc.perform(get("/api/profiles/user-2/games")
                        .param("topicId", "topic-1")
                        .param("opponentType", "BOT")
                        .param("outcome", "WIN")
                        .param("sort", "SCORE")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        verify(profileViewService).games("user-2", "topic-1", null, GamePlayerType.BOT,
                GameHistoryOutcome.WIN, GameHistorySort.SCORE, 2, 10);
    }

    @Test
    void topics_returns_topic_stats() throws Exception {
        when(profileViewService.topics("user-2"))
                .thenReturn(CompletableFuture.completedFuture(List.of(
                        new TopicStatView("topic-1", Map.of(Language.FR, "DotA"), "🎮", "#f00",
                                "https://example.com/img.png", 7, 200))));

        MvcResult result = mockMvc.perform(get("/api/profiles/user-2/topics"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].topicId").value("topic-1"))
                .andExpect(jsonPath("$[0].games").value(7))
                .andExpect(jsonPath("$[0].xp").value(200));
    }

    @Test
    void unfollow_sends_command_with_actor() throws Exception {
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture("user-1:user-2"));

        MvcResult result = mockMvc.perform(delete("/api/profiles/user-2/follow"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent());
        verify(commandGateway).send(new UserFollowerCommand.UnfollowUserCommand("user-1:user-2", "user-1"));
    }

    @Test
    void badges_returns_the_whole_catalogue_with_progress() throws Exception {
        when(profileViewService.badges("user-2")).thenReturn(CompletableFuture.completedFuture(
                new BadgeCollectionView(2, 88, List.of(new BadgeCollectionView.FamilyView(
                        "VOLUME", List.of(new BadgeCollectionView.BadgeItemView(
                                "DUELIST_BRONZE", "Duelliste", "BRONZE", "HUMAN_DUELS", 3,
                                true, 3)))))));

        MvcResult result = mockMvc.perform(get("/api/profiles/user-2/badges"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.earnedCount").value(2))
                .andExpect(jsonPath("$.families[0].family").value("VOLUME"))
                .andExpect(jsonPath("$.families[0].badges[0].code").value("DUELIST_BRONZE"))
                .andExpect(jsonPath("$.families[0].badges[0].earned").value(true))
                .andExpect(jsonPath("$.families[0].badges[0].progress").value(3));
    }

    @Test
    void titles_returns_equipped_key_and_unlocked_titles() throws Exception {
        when(profileViewService.titles("user-2")).thenReturn(CompletableFuture.completedFuture(
                new TitlesView("topic-1:OR", List.of(new TitlesView.TitleView(
                        "topic-1:OR", "topic-1", "OR", 40, 1_400, "PLATINE",
                        new TopicRefView("topic-1", Map.of(Language.FR, "Histoire"),
                                null, null, null, null, null))))));

        MvcResult result = mockMvc.perform(get("/api/profiles/user-2/titles"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equippedKey").value("topic-1:OR"))
                .andExpect(jsonPath("$.titles[0].tier").value("OR"))
                .andExpect(jsonPath("$.titles[0].key").value("topic-1:OR"))
                .andExpect(jsonPath("$.titles[0].topic.names.fr").value("Histoire"));
    }

    @Test
    void updateTitle_sendsCommandWithActor() throws Exception {
        when(profileUpdateService.updateEquippedTitle(USER_ID, USER_ID, "topic-1:OR"))
                .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(put("/api/profiles/user-1/title")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titleKey\":\"topic-1:OR\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent());
        verify(profileUpdateService).updateEquippedTitle(USER_ID, USER_ID, "topic-1:OR");
    }

    @Test
    void updateTitle_withoutKey_clearsIt() throws Exception {
        when(profileUpdateService.updateEquippedTitle(USER_ID, USER_ID, null))
                .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(put("/api/profiles/user-1/title")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent());
        verify(profileUpdateService).updateEquippedTitle(USER_ID, USER_ID, null);
    }

    @Test
    void activity_returns_the_streak_calendar_for_an_explicit_window() throws Exception {
        when(profileViewService.activity("user-2", LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31")))
                .thenReturn(CompletableFuture.completedFuture(new ActivityViewResponse(
                        "user-2",
                        3,
                        7,
                        LocalDate.parse("2026-01-31"),
                        5,
                        List.of(new ActivityViewResponse.Day(LocalDate.parse("2026-01-31"), 2)))));

        MvcResult result = mockMvc.perform(get("/api/profiles/user-2/activity")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(3))
                .andExpect(jsonPath("$.longestStreak").value(7))
                .andExpect(jsonPath("$.days[0].games").value(2));
    }

    @Test
    void activity_rejects_a_window_longer_than_one_year() throws Exception {
        mockMvc.perform(get("/api/profiles/user-2/activity")
                        .param("from", "2024-01-01")
                        .param("to", "2026-01-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:joutio:bff:profile:invalidActivityRange"));
    }

    @Test
    void updatePseudonym_sendsCommandWithActor() throws Exception {
        when(profileUpdateService.updatePseudonym(USER_ID, USER_ID, "Alicia"))
                .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(put("/api/profiles/user-1/pseudonym")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pseudonym\":\"Alicia\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent());
        verify(profileUpdateService).updatePseudonym(USER_ID, USER_ID, "Alicia");
    }

    @Test
    void updateBio_sendsCommandWithActor() throws Exception {
        when(profileUpdateService.updateBio(USER_ID, USER_ID, "Full stack"))
                .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(put("/api/profiles/user-1/bio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\":\"Full stack\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent());
        verify(profileUpdateService).updateBio(USER_ID, USER_ID, "Full stack");
    }

    @Test
    void updateCountry_sendsCommandWithActor() throws Exception {
        when(profileUpdateService.updateCountry(USER_ID, USER_ID, "FR"))
                .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(put("/api/profiles/user-1/country")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"country\":\"FR\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent());
        verify(profileUpdateService).updateCountry(USER_ID, USER_ID, "FR");
    }

    @Test
    void updateAvatarOptions_sendsCommandWithActor() throws Exception {
        when(profileUpdateService.updateAvatarOptions(USER_ID, USER_ID, "{\"hair\":\"full\"}"))
                .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(put("/api/profiles/user-1/avatar-options")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarOptions\":\"{\\\"hair\\\":\\\"full\\\"}\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent());
        verify(profileUpdateService).updateAvatarOptions(USER_ID, USER_ID, "{\"hair\":\"full\"}");
    }

    @Test
    void updateBanner_sendsCommandWithActor() throws Exception {
        when(profileUpdateService.updateBanner(USER_ID, USER_ID, "https://media.example.test/banner.jpg"))
                .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(put("/api/profiles/user-1/banner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bannerUrl\":\"https://media.example.test/banner.jpg\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent());
        verify(profileUpdateService).updateBanner(USER_ID, USER_ID, "https://media.example.test/banner.jpg");
    }

    @Test
    void updateBanner_withInvalidScheme_isRejected() throws Exception {
        mockMvc.perform(put("/api/profiles/user-1/banner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bannerUrl\":\"ftp://example.test/banner.jpg\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(profileUpdateService);
    }

    @Test
    void updateLanguage_sendsCommandWithActor() throws Exception {
        when(profileUpdateService.updateLanguage(USER_ID, USER_ID, Language.EN))
                .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(put("/api/profiles/user-1/language")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"en\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent());
        verify(profileUpdateService).updateLanguage(USER_ID, USER_ID, Language.EN);
    }

    @Test
    void updateLanguage_withUnsupportedLanguage_isRejected() throws Exception {
        mockMvc.perform(put("/api/profiles/user-1/language")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"de\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(profileUpdateService);
    }
}
