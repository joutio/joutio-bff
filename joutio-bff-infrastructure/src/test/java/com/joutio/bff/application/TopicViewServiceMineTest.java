package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.response.TopicCardView;
import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.theme.domain.model.Topic;
import com.joutio.theme.domain.model.TopicPage;
import com.joutio.theme.domain.model.TopicStatus;
import com.joutio.theme.domain.query.TopicQuery;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * « Mes thèmes › Mes sujets » (`mine=true`) : seuls mes sujets publiés remontent dans le catalogue ; brouillons et
 * archives restent dans l'onglet Création.
 */
@SuppressWarnings("unchecked")
class TopicViewServiceMineTest {

    private final QueryGateway queryGateway = mock(QueryGateway.class);
    private final FollowLookup followLookup = mock(FollowLookup.class);
    private final TopicViewService service = new TopicViewService(
            queryGateway,
            followLookup,
            mock(LeaderboardScopeService.class),
            mock(IdentityLookup.class),
            mock(TopicAccessService.class));

    @Test
    void mineListsOnlyPublishedTopics() {
        List<Topic> mine = List.of(
                topic("published", TopicStatus.PUBLISHED, 3),
                topic("draft", TopicStatus.DRAFT, 2),
                topic("archived", TopicStatus.ARCHIVED, 1));
        when(followLookup.followedTopicIds("me", FollowLookup.MAX_LIST_SIZE))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(queryGateway.query(
                ArgumentMatchers.<Object>any(),
                ArgumentMatchers.<ResponseType<Object>>any()))
                .thenAnswer(invocation -> {
                    assertThat(invocation.getArgument(0, Object.class))
                            .isInstanceOf(TopicQuery.GetTopicsByCreatorQuery.class);
                    return CompletableFuture.completedFuture(
                            new TopicPage(mine, 0, mine.size(), mine.size(), 1));
                });

        var page = service.list("me", null, null, false, true, null, 0, 20).join();

        assertThat(page.content()).extracting(TopicCardView::topicId).containsExactly("published");
        assertThat(page.totalElements()).isEqualTo(1);
    }

    private static Topic topic(String id, TopicStatus status, int day) {
        return Topic.builder()
                .topicId(id)
                .names(Map.of(Language.FR, id))
                .status(status)
                .creatorId("me")
                .createdAt(Instant.parse("2026-11-0" + day + "T00:00:00Z"))
                .build();
    }
}
