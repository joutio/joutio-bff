package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.AccountDeletionService;
import com.joutio.bff.application.MeService;
import com.joutio.bff.application.ProfileUpdateService;
import com.joutio.bff.application.RewardService;
import com.joutio.bff.application.TopicRecommendationService;
import com.joutio.bff.infrastructure.in.api.response.DuelStatsView;
import com.joutio.bff.infrastructure.in.api.response.MeView;
import com.joutio.bff.infrastructure.in.api.response.ProgressionView;
import com.joutio.bff.infrastructure.in.api.response.RewardsView;
import com.joutio.bff.infrastructure.in.api.response.TopicCardView;
import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.theme.domain.model.TopicCategory;
import com.joutio.theme.domain.model.TopicStatus;
import com.joutio.microservice.core.domain.model.security.JoutioPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeControllerTest {

    private static final String USER_ID = "user-1";

    private final MeService meService = mock(MeService.class);
    private final AccountDeletionService accountDeletionService = mock(AccountDeletionService.class);
    private final ProfileUpdateService profileUpdateService = mock(ProfileUpdateService.class);
    private final RewardService rewardService = mock(RewardService.class);
    private final TopicRecommendationService topicRecommendationService =
            mock(TopicRecommendationService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MeController(
                meService, accountDeletionService, profileUpdateService, rewardService,
                topicRecommendationService)).build();
        JoutioPrincipal principal = new JoutioPrincipal() {
            @Override
            public String getUserId() {
                return USER_ID;
            }

            @Override
            public String getEmail() {
                return "user@joutio.io";
            }
        };
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(principal, null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void me_returns_current_player_view() throws Exception {
        MeView view = new MeView(USER_ID, "user@joutio.io", "Alpha", null, "FR", null, null, null,
                Language.FR,
                new ProgressionView(List.of(), 200, 200, 0, true),
                new DuelStatsView(4, 3, 1, 0, 75, 160, 2, 3), 3, 5, false, List.of(), false,
                List.of("HISTORY"));
        when(meService.me(USER_ID)).thenReturn(CompletableFuture.completedFuture(view));

        MvcResult result = mockMvc.perform(get("/api/me"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.language").value("fr"))
                .andExpect(jsonPath("$.progression.rating").value(200))
                .andExpect(jsonPath("$.rewards").isEmpty())
                .andExpect(jsonPath("$.premium").value(false))
                .andExpect(jsonPath("$.interests[0]").value("HISTORY"));
    }
    @Test
    void rewards_returnsRewardCatalogue() throws Exception {
        RewardsView view = new RewardsView(List.of(
                new RewardsView.RewardView(
                        "MICAH_AVATAR", false, null, null,
                        new RewardsView.ConditionView("ASSIDUITY_BRONZE", "Régulier", 7))));
        when(rewardService.rewards(USER_ID)).thenReturn(CompletableFuture.completedFuture(view));

        MvcResult result = mockMvc.perform(get("/api/me/rewards"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rewards[0].code").value("MICAH_AVATAR"))
                .andExpect(jsonPath("$.rewards[0].unlocked").value(false))
                .andExpect(jsonPath("$.rewards[0].condition.badgeCode").value("ASSIDUITY_BRONZE"))
                .andExpect(jsonPath("$.rewards[0].condition.target").value(7));
        verify(rewardService).rewards(USER_ID);
    }

    @Test
    void completeOnboarding_returnsUpdatedMeView() throws Exception {
        MeView view = new MeView(USER_ID, "user@joutio.io", "Alpha", null, "FR", null, null, null,
                Language.FR,
                new ProgressionView(List.of(), 200, 200, 0, true),
                new DuelStatsView(4, 3, 1, 0, 75, 160, 2, 3), 3, 5, false, List.of(), false,
                List.of());
        when(profileUpdateService.completeOnboarding(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(meService.isPremium()).thenReturn(false);
        when(meService.me(USER_ID, false)).thenReturn(CompletableFuture.completedFuture(view));

        MvcResult result = mockMvc.perform(post("/api/me/onboarding/complete"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted").value(true));
        verify(profileUpdateService).completeOnboarding(USER_ID);
    }

    @Test
    void updateInterests_replacesThePlayerInterests() throws Exception {
        when(profileUpdateService.updateInterests(
                USER_ID, USER_ID, List.of(TopicCategory.HISTORY, TopicCategory.SCIENCE)))
                .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult result = mockMvc.perform(put("/api/me/interests")
                        .contentType("application/json")
                        .content("{\"interests\":[\"HISTORY\",\"SCIENCE\"]}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isNoContent());

        verify(profileUpdateService).updateInterests(
                USER_ID, USER_ID, List.of(TopicCategory.HISTORY, TopicCategory.SCIENCE));
    }

    @Test
    void onboardingRecommendations_returnsPlayableTopics() throws Exception {
        TopicCardView card = new TopicCardView(
                "t1", Map.of(Language.FR, "Révolution"), null,
                TopicCategory.HISTORY, "Histoire", "🏛️", "#a855f7",
                null, null, 3, 7, false, TopicStatus.PUBLISHED);
        when(topicRecommendationService.recommend(any(), any(), eq(3)))
                .thenReturn(CompletableFuture.completedFuture(List.of(card)));

        MvcResult result = mockMvc.perform(get("/api/me/onboarding/recommendations")
                        .param("interests", "HISTORY,SCIENCE"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].topicId").value("t1"))
                .andExpect(jsonPath("$[0].category").value("HISTORY"));
    }

    @Test
    void requestDeletionCode_returnsAccepted() throws Exception {
        mockMvc.perform(post("/api/me/deletion-code"))
                .andExpect(status().isAccepted());

        verify(accountDeletionService).requestCode(USER_ID);
    }

    @Test
    void deleteAccount_returnsAccepted() throws Exception {
        mockMvc.perform(delete("/api/me")
                        .contentType("application/json")
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isAccepted());

        verify(accountDeletionService).delete(USER_ID, "123456");
    }
}
