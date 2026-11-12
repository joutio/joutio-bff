package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.microservice.core.domain.constant.JoutioConstants;
import com.joutio.theme.domain.model.Question;
import com.joutio.theme.domain.model.QuestionStatus;
import com.joutio.theme.domain.model.Topic;
import com.joutio.theme.domain.model.TopicPage;
import com.joutio.theme.domain.model.TopicStatus;
import com.joutio.theme.domain.query.QuestionQuery;
import com.joutio.theme.domain.query.TopicQuery;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TopicCreationLimitPolicyTest {

    private static final String CREATOR = "creator";

    private final QueryGateway queries = mock(QueryGateway.class);
    private final PremiumAccessPolicy premium = mock(PremiumAccessPolicy.class);
    private final TopicCreationLimitPolicy policy = new TopicCreationLimitPolicy(
            queries, premium, new CreationLimitsProperties(1, 10, 30, 150));

    @Test
    void freeCreatorCanPublishFirstTopic() {
        stub(false, topic("t1", CREATOR, TopicStatus.DRAFT), List.of(), questions(30, QuestionStatus.APPROVED));
        assertThatCode(() -> policy.ensureCanPublish("t1").join()).doesNotThrowAnyException();
    }

    @Test
    void freeCreatorCannotPublishSecondTopic() {
        stub(false, topic("t2", CREATOR, TopicStatus.DRAFT),
                List.of(topic("t1", CREATOR, TopicStatus.PUBLISHED)), List.of());
        assertThatThrownBy(() -> policy.ensureCanPublish("t2").join())
                .hasCauseInstanceOf(BffProblems.TopicQuotaExceededProblem.class);
    }

    @Test
    void premiumCreatorCanPublishUpToTen() {
        List<Topic> published = new ArrayList<>();
        IntStream.range(0, 9).forEach(i -> published.add(topic("p" + i, CREATOR, TopicStatus.PUBLISHED)));
        stub(true, topic("t10", CREATOR, TopicStatus.DRAFT), published, List.of());
        assertThatCode(() -> policy.ensureCanPublish("t10").join()).doesNotThrowAnyException();
    }

    @Test
    void freeCreatorCannotPublishOversizedTopic() {
        stub(false, topic("t1", CREATOR, TopicStatus.DRAFT), List.of(), questions(31, QuestionStatus.PENDING));
        assertThatThrownBy(() -> policy.ensureCanPublish("t1").join())
                .hasCauseInstanceOf(BffProblems.QuestionQuotaExceededProblem.class);
    }

    @Test
    void questionQuotaIgnoresArchivedAndRejected() {
        List<Question> list = new ArrayList<>(questions(29, QuestionStatus.APPROVED));
        list.addAll(questions(5, QuestionStatus.ARCHIVED));
        list.addAll(questions(5, QuestionStatus.REJECTED));
        stub(false, topic("t1", CREATOR, TopicStatus.PUBLISHED),
                List.of(topic("t1", CREATOR, TopicStatus.PUBLISHED)), list);
        assertThatCode(() -> policy.ensureCanAddQuestion("t1").join()).doesNotThrowAnyException();
    }

    @Test
    void freeCreatorTopicIsFullAtThirty() {
        List<Question> list = new ArrayList<>(questions(20, QuestionStatus.APPROVED));
        list.addAll(questions(9, QuestionStatus.PENDING));
        list.addAll(questions(1, QuestionStatus.SUSPENDED));
        stub(false, topic("t1", CREATOR, TopicStatus.DRAFT), List.of(), list);
        assertThatThrownBy(() -> policy.ensureCanAddQuestion("t1").join())
                .hasCauseInstanceOf(BffProblems.QuestionQuotaExceededProblem.class);
    }

    @Test
    void premiumCreatorTopicAcceptsUpToHundredFifty() {
        stub(true, topic("t1", CREATOR, TopicStatus.PUBLISHED), List.of(), questions(149, QuestionStatus.APPROVED));
        assertThatCode(() -> policy.ensureCanAddQuestion("t1").join()).doesNotThrowAnyException();
    }

    @Test
    void expiredCreatorOverFreeLimitHasFrozenPublishedTopics() {
        stub(false, topic("t1", CREATOR, TopicStatus.PUBLISHED),
                List.of(topic("t1", CREATOR, TopicStatus.PUBLISHED), topic("t2", CREATOR, TopicStatus.PUBLISHED)),
                List.of());
        assertThatThrownBy(() -> policy.ensureEditable("t1").join())
                .hasCauseInstanceOf(BffProblems.TopicOverQuotaProblem.class);
        assertThatThrownBy(() -> policy.ensureCanAddQuestion("t1").join())
                .hasCauseInstanceOf(BffProblems.TopicOverQuotaProblem.class);
    }

    @Test
    void expiredCreatorKeepsDraftsEditable() {
        stub(false, topic("d1", CREATOR, TopicStatus.DRAFT),
                List.of(topic("t1", CREATOR, TopicStatus.PUBLISHED), topic("t2", CREATOR, TopicStatus.PUBLISHED)),
                List.of());
        assertThatCode(() -> policy.ensureEditable("d1").join()).doesNotThrowAnyException();
    }

    @Test
    void freeCreatorWithinLimitCanEdit() {
        stub(false, topic("t1", CREATOR, TopicStatus.PUBLISHED),
                List.of(topic("t1", CREATOR, TopicStatus.PUBLISHED)), List.of());
        assertThatCode(() -> policy.ensureEditable("t1").join()).doesNotThrowAnyException();
    }

    @Test
    void premiumCreatorIsNeverFrozen() {
        stub(true, topic("t1", CREATOR, TopicStatus.PUBLISHED),
                List.of(topic("t1", CREATOR, TopicStatus.PUBLISHED), topic("t2", CREATOR, TopicStatus.PUBLISHED)),
                List.of());
        assertThatCode(() -> policy.ensureEditable("t1").join()).doesNotThrowAnyException();
    }

    @Test
    void systemTopicsAreExempt() {
        stub(false, topic("seed", JoutioConstants.SYSTEM_USER_ID, TopicStatus.PUBLISHED), List.of(),
                questions(500, QuestionStatus.APPROVED));
        assertThatCode(() -> policy.ensureCanAddQuestion("seed").join()).doesNotThrowAnyException();
        assertThatCode(() -> policy.ensureEditable("seed").join()).doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    private void stub(boolean creatorPremium, Topic topic, List<Topic> creatorTopics, List<Question> questions) {
        when(premium.premium(any())).thenReturn(CompletableFuture.completedFuture(creatorPremium));
        when(queries.query(any(TopicQuery.GetTopicByIdQuery.class), any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(topic));
        when(queries.query(any(TopicQuery.GetTopicsByCreatorQuery.class), any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(new TopicPage(creatorTopics, 0, 500,
                        creatorTopics.size(), 1)));
        when(queries.query(any(QuestionQuery.GetQuestionsByTopicIdQuery.class), any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(questions));
    }

    private static Topic topic(String id, String creator, TopicStatus status) {
        return Topic.builder().topicId(id).creatorId(creator).status(status).build();
    }

    private static List<Question> questions(int count, QuestionStatus status) {
        Question[] list = new Question[count];
        for (int i = 0; i < count; i++) {
            list[i] = Question.builder().questionId(status + "-" + i).topicId("t").status(status).build();
        }
        return Arrays.asList(list);
    }
}
