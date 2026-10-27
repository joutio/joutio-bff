package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.response.TopicCardView;
import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.theme.domain.model.QuestionStatus;
import com.joutio.theme.domain.model.Topic;
import com.joutio.theme.domain.model.TopicCategory;
import com.joutio.theme.domain.model.TopicPage;
import com.joutio.theme.domain.model.TopicStatus;
import com.joutio.theme.domain.query.TopicQuery;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TopicRecommendationServiceTest {

    private final QueryGateway queryGateway = mock(QueryGateway.class);
    private final TopicRecommendationService service =
            new TopicRecommendationService(queryGateway);

    @Test
    void emptyInterestsReturnNoRecommendationAndSkipQueries() {
        assertThat(service.recommend(List.of(), Set.of(), 3).join()).isEmpty();
        assertThat(service.recommend(null, Set.of(), 3).join()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(queryGateway);
    }

    @Test
    void picksOnePlayableTopicPerCategoryThenFillsInOrder() {
        stubPages(TopicPage.builder().topics(List.of(
                topic("h1", TopicCategory.HISTORY, 7),
                topic("h2", TopicCategory.HISTORY, 6),
                topic("h3", TopicCategory.HISTORY, 7))).build(),
                TopicPage.builder().topics(List.of(
                        topic("s1", TopicCategory.SCIENCE, 7),
                        topic("s2", TopicCategory.SCIENCE, 7))).build(),
                TopicPage.builder().topics(List.of(
                        topic("f1", TopicCategory.FOOD_AND_DRINK, 7))).build());

        List<TopicCardView> cards = service.recommend(
                List.of(TopicCategory.HISTORY, TopicCategory.SCIENCE,
                        TopicCategory.FOOD_AND_DRINK),
                Set.of("followed"), 3).join();

        assertThat(cards).extracting(TopicCardView::topicId)
                .containsExactly("h1", "s1", "f1");
        assertThat(cards).allSatisfy(card -> assertThat(card.questionsCount()).isGreaterThanOrEqualTo(7));
        assertThat(cards).extracting(TopicCardView::topicId).doesNotContain("followed");
    }

    @Test
    void fillsBeyondTheFirstTopicPerCategoryWhenLimitAllows() {
        stubPages(TopicPage.builder().topics(List.of(
                topic("h1", TopicCategory.HISTORY, 7),
                topic("h2", TopicCategory.HISTORY, 7))).build(),
                TopicPage.builder().topics(List.of(
                        topic("s1", TopicCategory.SCIENCE, 7))).build());

        List<TopicCardView> cards = service.recommend(
                List.of(TopicCategory.HISTORY, TopicCategory.SCIENCE), Set.of(), 4).join();

        assertThat(cards).extracting(TopicCardView::topicId)
                .containsExactly("h1", "s1", "h2");
    }

    @Test
    void unknownOrUnplayableTopicsAreIgnored() {
        stubPages(TopicPage.builder().topics(List.of(
                topic("draft", TopicCategory.HISTORY, 7).toBuilder()
                        .status(TopicStatus.DRAFT).build(),
                topic("short", TopicCategory.HISTORY, 6))).build());

        assertThat(service.recommend(List.of(TopicCategory.HISTORY), Set.of(), 3).join())
                .isEmpty();
    }

    private void stubPages(TopicPage... pages) {
        AtomicInteger index = new AtomicInteger();
        when(queryGateway.query(
                ArgumentMatchers.<Object>any(),
                ArgumentMatchers.<ResponseType<Object>>any()))
                .thenAnswer(_ -> {
                    int next = index.getAndIncrement();
                    TopicPage page = next < pages.length ? pages[next] : TopicPage.builder().topics(List.of()).build();
                    return CompletableFuture.completedFuture(page);
                });
    }

    private static Topic topic(String id, TopicCategory category, int approved) {
        return Topic.builder()
                .topicId(id)
                .names(Map.of(Language.FR, id))
                .category(category)
                .status(TopicStatus.PUBLISHED)
                .questionsCounter(Map.of(QuestionStatus.APPROVED, approved))
                .build();
    }
}
