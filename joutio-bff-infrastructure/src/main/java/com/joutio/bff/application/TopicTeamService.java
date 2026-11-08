package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.response.*;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.theme.domain.command.TopicCommand;
import com.joutio.theme.domain.model.*;
import com.joutio.theme.domain.query.TopicQuery;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class TopicTeamService {
    private final QueryGateway queries;
    private final CommandGateway commands;
    private final TopicAccessService access;
    private final ProfileLookup profiles;

    public TopicTeamService(
            QueryGateway queries,
            CommandGateway commands,
            TopicAccessService access,
            ProfileLookup profiles) {
        this.queries = queries;
        this.commands = commands;
        this.access = access;
        this.profiles = profiles;
    }

    public CompletableFuture<TopicWorkshopView> workshop(String topicId, String userId) {
        return access.requireMember(topicId, userId)
                .thenCompose(
                        p ->
                                queries.query(
                                                new TopicQuery.GetTopicByIdQuery(topicId),
                                                QueryResponseTypes.instanceOf(Topic.class))
                                        .thenApply(
                                                topic ->
                                                        new TopicWorkshopView(
                                                                TopicViews.toCard(topic, false),
                                                                p)));
    }

    public CompletableFuture<PageResponse<TopicCardView>> contributed(
            String userId, int page, int size) {
        return queries.query(
                        new TopicQuery.GetContributedTopicsQuery(userId, page, size),
                        QueryResponseTypes.instanceOf(TopicPage.class))
                .thenApply(
                        result ->
                                PageResponse.of(
                                        result.topics().stream()
                                                .map(t -> TopicViews.toCard(t, false))
                                                .toList(),
                                        result.page(),
                                        result.size(),
                                        result.totalElements()));
    }

    public CompletableFuture<List<TopicTeamMemberView>> team(String topicId, String userId) {
        return access.requireMember(topicId, userId)
                .thenCompose(
                        _ ->
                                queries.query(
                                        new TopicQuery.GetTopicTeamQuery(topicId),
                                        QueryResponseTypes.instanceOf(TopicTeam.class)))
                .thenCompose(
                        team -> {
                            var members =
                                    team.members().entrySet().stream()
                                            .map(
                                                    entry ->
                                                            profiles.get(entry.getKey())
                                                                    .thenCombine(
                                                                            access.premium(
                                                                                    entry.getKey()),
                                                                            (profile, premium) ->
                                                                                    new TopicTeamMemberView(
                                                                                            entry
                                                                                                    .getKey(),
                                                                                            profile
                                                                                                    .pseudonym(),
                                                                                            profile
                                                                                                    .avatarOptions(),
                                                                                            entry
                                                                                                    .getValue(),
                                                                                            premium)))
                                            .toList();
                            return CompletableFuture.allOf(
                                            members.toArray(CompletableFuture[]::new))
                                    .thenApply(
                                            _ ->
                                                    members.stream()
                                                            .map(CompletableFuture::join)
                                                            .toList());
                        });
    }

    public CompletableFuture<TopicTeamMemberView> set(
            String topicId, String actor, String userId, TopicTeamRole role) {
        return access.permissions(topicId, actor)
                .thenCompose(
                        p -> {
                            if (!p.canManageTeam())
                                throw new BffProblems.NotTopicOwnerProblem(topicId);
                            if (!p.canAddMembers()) throw new BffProblems.PremiumRequiredProblem();
                            if (role == null || role == TopicTeamRole.CREATOR)
                                throw new BffProblems.NotTopicOwnerProblem(topicId);
                            return access.premium(userId)
                                    .thenCompose(
                                            premium -> {
                                                if (role == TopicTeamRole.ADMIN && !premium)
                                                    throw new BffProblems.PremiumRequiredProblem();
                                                return profiles.get(userId)
                                                        .thenCompose(
                                                                profile ->
                                                                        commands.send(
                                                                                        new TopicCommand
                                                                                                .SetTopicMemberCommand(
                                                                                                topicId,
                                                                                                actor,
                                                                                                userId,
                                                                                                role))
                                                                                .thenApply(
                                                                                        _ ->
                                                                                                new TopicTeamMemberView(
                                                                                                        userId,
                                                                                                        profile
                                                                                                                .pseudonym(),
                                                                                                        profile
                                                                                                                .avatarOptions(),
                                                                                                        role,
                                                                                                        premium)));
                                            });
                        });
    }

    public CompletableFuture<Void> remove(String topicId, String actor, String userId) {
        return access.requireManager(topicId, actor)
                .thenCompose(
                        _ ->
                                commands.send(
                                        new TopicCommand.RemoveTopicMemberCommand(
                                                topicId, actor, userId)))
                .thenAccept(_ -> {});
    }
}
