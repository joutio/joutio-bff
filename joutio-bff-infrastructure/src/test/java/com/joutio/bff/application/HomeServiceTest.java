package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.response.HomeView;
import com.joutio.bff.infrastructure.in.api.response.TopicCardView;
import com.joutio.game.domain.model.TopicPopularity;
import com.joutio.game.domain.query.GameQuery;
import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.profile.domain.model.Profile;
import com.joutio.theme.domain.model.Topic;
import com.joutio.theme.domain.model.TopicPage;
import com.joutio.theme.domain.model.TopicSort;
import com.joutio.theme.domain.model.TopicStatus;
import com.joutio.theme.domain.query.TopicQuery;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Top 10 de l'accueil : les sujets joués dans les 24 h gardent la tête, puis le relais all-time
 * complète la liste à 10 sans doublon. Quand 10 sujets récents existent déjà, la requête
 * all-time n'est pas émise.
 */
@SuppressWarnings("unchecked")
class HomeServiceTest {

    private final QueryGateway queryGateway = mock(QueryGateway.class);
    private final FollowLookup followLookup = mock(FollowLookup.class);
    private final ProfileLookup profileLookup = mock(ProfileLookup.class);
    private final TopicRecommendationService topicRecommendationService =
            mock(TopicRecommendationService.class);
    private final HomeService service = new HomeService(
            queryGateway, followLookup, profileLookup, topicRecommendationService);

    @BeforeEach
    void setUp() {
        when(followLookup.followedTopicIds("me", FollowLookup.MAX_LIST_SIZE))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(profileLookup.get("me"))
                .thenReturn(CompletableFuture.completedFuture(
                        Profile.builder().userId("me").build()));
    }

    @Test
    void completesTrendingWithMostPlayedWhenFewerThanTenRecentTopics() {
        List<String> recentIds = List.of("t1", "t2", "t3", "t4", "t5", "t6", "t7");
        List<Topic> mostPlayed = topics("t5", "t6", "t7", "t8", "t9", "t10", "t11", "t12", "t13", "t14");
        AtomicBoolean fallbackQueried = new AtomicBoolean(false);

        stubQueries(recentIds, mostPlayed, fallbackQueried);

        HomeView view = service.home("me").join();

        assertThat(view.trendingTopics())
                .extracting(TopicCardView::topicId)
                .containsExactly("t1", "t2", "t3", "t4", "t5", "t6", "t7", "t8", "t9", "t10");
        assertThat(fallbackQueried).isTrue();
    }

    @Test
    void skipsMostPlayedFallbackWhenTenRecentTopicsExist() {
        List<String> recentIds = List.of("t1", "t2", "t3", "t4", "t5", "t6", "t7", "t8", "t9", "t10");
        AtomicBoolean fallbackQueried = new AtomicBoolean(false);

        stubQueries(recentIds, List.of(), fallbackQueried);

        HomeView view = service.home("me").join();

        assertThat(view.trendingTopics())
                .extracting(TopicCardView::topicId)
                .containsExactlyElementsOf(recentIds);
        assertThat(fallbackQueried).isFalse();
    }

    @Test
    void addsRecommendedTopicsFromPersistedInterests() {
        when(profileLookup.get("me")).thenReturn(CompletableFuture.completedFuture(
                Profile.builder().userId("me").interests(List.of("HISTORY")).build()));
        TopicCardView recommendation = new TopicCardView(
                "h1", Map.of(Language.FR, "Histoire"), null,
                com.joutio.theme.domain.model.TopicCategory.HISTORY, "Histoire",
                "🏛️", "#a855f7", null, null, 2, 7, false,
                com.joutio.theme.domain.model.TopicStatus.PUBLISHED);
        when(topicRecommendationService.recommend(
                List.of(com.joutio.theme.domain.model.TopicCategory.HISTORY),
                Set.of(), 10))
                .thenReturn(CompletableFuture.completedFuture(List.of(recommendation)));
        stubQueries(List.of(), List.of(), new AtomicBoolean(false));

        HomeView view = service.home("me").join();

        assertThat(view.recommendedTopics())
                .extracting(TopicCardView::topicId)
                .containsExactly("h1");
    }

    private void stubQueries(List<String> recentIds, List<Topic> mostPlayed, AtomicBoolean fallbackQueried) {
        List<TopicPopularity> popular = recentIds.stream()
                .map(id -> new TopicPopularity(id, 1))
                .toList();
        Map<String, Topic> recentById = topics(recentIds.toArray(String[]::new)).stream()
                .collect(Collectors.toMap(Topic::topicId, Function.identity()));

        when(queryGateway.query(
                ArgumentMatchers.<Object>any(),
                ArgumentMatchers.<ResponseType<Object>>any()))
                .thenAnswer(invocation -> {
                    Object query = invocation.getArgument(0);
                    Object result;
                    if (query instanceof GameQuery.GetPopularTopicsQuery) {
                        result = popular;
                    } else if (query instanceof TopicQuery.GetTopicsByIdsQuery byIds) {
                        result = byIds.topicIds().stream()
                                .map(recentById::get)
                                .filter(java.util.Objects::nonNull)
                                .toList();
                    } else if (query instanceof TopicQuery.GetTopicPageQuery page) {
                        if (page.sort() == TopicSort.MOST_PLAYED) {
                            fallbackQueried.set(true);
                            result = TopicPage.builder().topics(mostPlayed).build();
                        } else {
                            result = TopicPage.builder().topics(List.of()).build();
                        }
                    } else {
                        throw new AssertionError("Unexpected query: " + query);
                    }
                    return CompletableFuture.completedFuture(result);
                });
    }

    private static List<Topic> topics(String... ids) {
        return Arrays.stream(ids)
                .map(id -> Topic.builder()
                        .topicId(id)
                        .names(Map.of(Language.FR, id))
                        .status(TopicStatus.PUBLISHED)
                        .build())
                .toList();
    }
}
