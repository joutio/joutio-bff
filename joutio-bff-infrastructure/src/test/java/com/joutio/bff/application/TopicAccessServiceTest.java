package com.joutio.bff.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.identity.domain.model.UserEntitlements;
import com.joutio.identity.domain.query.UserQuery;
import com.joutio.theme.domain.model.*;
import com.joutio.theme.domain.query.TopicQuery;

import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

class TopicAccessServiceTest {
    private final QueryGateway queries = mock(QueryGateway.class);
    private final TopicAccessService access = new TopicAccessService(queries, new PremiumAccessPolicy(mock(IdentityLookup.class), queries));

    private void member(TopicTeamRole role, boolean premium) {
        when(queries.query(any(TopicQuery.GetTopicByIdQuery.class), any(ResponseType.class)))
                .thenReturn(
                        CompletableFuture.completedFuture(
                                Topic.builder()
                                        .creatorId("owner")
                                        .topicId("topic")
                                        .status(TopicStatus.PUBLISHED)
                                        .build()));
        when(queries.query(any(TopicQuery.GetTopicTeamQuery.class), any(ResponseType.class)))
                .thenReturn(
                        CompletableFuture.completedFuture(
                                new TopicTeam(role == null ? Map.of() : Map.of("actor", role))));
        when(queries.query(any(UserQuery.GetUserEntitlementsQuery.class), any(ResponseType.class)))
                .thenReturn(
                        CompletableFuture.completedFuture(new UserEntitlements("actor", premium)));
    }

    @Test
    void expiredPremiumRevokesAdministrativeAccess() {
        member(TopicTeamRole.ADMIN, false);
        assertThatThrownBy(() -> access.requireManager("topic", "actor").join())
                .hasCauseInstanceOf(BffProblems.NotTopicOwnerProblem.class);
        assertThat(access.requireMember("topic", "actor").join().canContribute()).isTrue();
    }

    @Test
    void premiumAdminMayManage() {
        member(TopicTeamRole.ADMIN, true);
        access.requireManager("topic", "actor").join();
    }

    @Test
    void unrelatedPremiumUserCannotReadWorkshop() {
        member(null, true);
        assertThatThrownBy(() -> access.requireMember("topic", "actor").join())
                .hasCauseInstanceOf(BffProblems.NotTopicOwnerProblem.class);
    }

    @Test
    void writerCannotEditApprovedOrSuspendedQuestions() {
        member(TopicTeamRole.WRITER, false);
        for (var status :
                java.util.List.of(
                        QuestionStatus.APPROVED,
                        QuestionStatus.SUSPENDED,
                        QuestionStatus.ARCHIVED)) {
            var q = Question.builder().creatorId("actor").topicId("topic").status(status).build();
            assertThatThrownBy(() -> access.requireEditor(q, "actor").join())
                    .hasCauseInstanceOf(BffProblems.NotTopicOwnerProblem.class);
        }
    }

    @Test
    void draftCannotBePlayed() {
        when(queries.query(any(TopicQuery.GetTopicByIdQuery.class), any(ResponseType.class)))
                .thenReturn(
                        CompletableFuture.completedFuture(
                                Topic.builder().status(TopicStatus.DRAFT).build()));
        assertThatThrownBy(() -> TopicAccessService.requirePlayable(queries, "topic").join())
                .hasCauseInstanceOf(
                        com.joutio.theme.domain.exception.TopicProblems.TopicUnavailableProblem
                                .class);
    }
}
