package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.theme.domain.model.*;
import com.joutio.theme.domain.query.TopicQuery;

import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class TopicAccessService {
    private final QueryGateway queries;
    private final PremiumAccessPolicy premiumAccessPolicy;

    public TopicAccessService(QueryGateway queries, PremiumAccessPolicy premiumAccessPolicy) {
        this.queries = queries;
        this.premiumAccessPolicy = premiumAccessPolicy;
    }

    public CompletableFuture<Boolean> premium(String userId) {
        return premiumAccessPolicy.premium(userId);
    }

    public CompletableFuture<TopicPermissions> permissions(String topicId, String userId) {
        return queries.query(
                        new TopicQuery.GetTopicByIdQuery(topicId),
                        QueryResponseTypes.instanceOf(Topic.class))
                .thenCompose(
                        topic ->
                                queries.query(
                                                new TopicQuery.GetTopicTeamQuery(topicId),
                                                QueryResponseTypes.instanceOf(TopicTeam.class))
                                        .thenCombine(
                                                premium(userId),
                                                (team, premium) ->
                                                        TopicPermissions.forMember(
                                                                userId.equals(topic.creatorId())
                                                                        ? TopicTeamRole.CREATOR
                                                                        : team.members()
                                                                                .get(userId),
                                                                premium)));
    }

    public CompletableFuture<TopicPermissions> requireMember(String topicId, String userId) {
        return permissions(topicId, userId)
                .thenApply(
                        p -> {
                            if (!p.canContribute())
                                throw new BffProblems.NotTopicOwnerProblem(topicId);
                            return p;
                        });
    }

    public CompletableFuture<Void> requireManager(String topicId, String userId) {
        return permissions(topicId, userId)
                .thenAccept(
                        p -> {
                            if (!p.canManage()) throw new BffProblems.NotTopicOwnerProblem(topicId);
                        });
    }

    public CompletableFuture<Void> requireEditor(Question question, String userId) {
        return permissions(question.topicId(), userId)
                .thenAccept(
                        p -> {
                            if (!p.canEdit(question, userId))
                                throw new BffProblems.NotTopicOwnerProblem(question.topicId());
                        });
    }

    public static CompletableFuture<Void> requirePlayable(QueryGateway queries, String topicId) {
        return queries.query(
                        new TopicQuery.GetTopicByIdQuery(topicId),
                        QueryResponseTypes.instanceOf(Topic.class))
                .thenAccept(
                        topic -> {
                            if (topic.status() != TopicStatus.PUBLISHED)
                                throw new com.joutio.theme.domain.exception.TopicProblems
                                        .TopicUnavailableProblem(topicId);
                        });
    }
}
