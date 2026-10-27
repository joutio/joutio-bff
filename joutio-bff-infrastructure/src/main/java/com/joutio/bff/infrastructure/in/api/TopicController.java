package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.TopicAuthoringService;
import com.joutio.bff.application.TopicViewService;
import com.joutio.bff.infrastructure.in.api.request.CreateQuestionRequest;
import com.joutio.bff.infrastructure.in.api.request.CreateTopicRequest;
import com.joutio.bff.infrastructure.in.api.request.LeaderboardMonth;
import com.joutio.bff.infrastructure.in.api.request.LeaderboardPeriod;
import com.joutio.bff.infrastructure.in.api.request.LeaderboardScope;
import com.joutio.bff.infrastructure.in.api.request.PageParams;
import com.joutio.bff.infrastructure.in.api.request.UpdateTopicBannerUrlRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateTopicCategoryRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateTopicColorRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateTopicDescriptionRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateTopicEmojiRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateTopicImageUrlRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateTopicNameRequest;
import com.joutio.bff.infrastructure.in.api.response.PageResponse;
import com.joutio.bff.infrastructure.in.api.response.QuestionEditorView;
import com.joutio.bff.infrastructure.in.api.response.TopicCardView;
import com.joutio.bff.infrastructure.in.api.response.TopicFacetsView;
import com.joutio.bff.infrastructure.in.api.response.TopicLeaderboardView;
import com.joutio.bff.infrastructure.in.api.response.TopicOverviewView;
import com.joutio.microservice.core.infrastructure.in.api.ResponseEntityBuilder;
import com.joutio.microservice.core.infrastructure.in.api.response.IdResponse;
import com.joutio.microservice.security.SecurityHelper;
import com.joutio.social.domain.command.TopicFollowerCommand;
import com.joutio.social.domain.model.FollowerIds;
import com.joutio.theme.domain.model.TopicCategory;
import com.joutio.theme.domain.model.TopicSort;
import jakarta.validation.Valid;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/**
 * {@code /api/topics} — catalogue, facettes, fiche agrégée, suivi, classement et **auteur**
 * (création, édition champ par champ, publication, questions).
 */
@RestController
@RequestMapping("/api/topics")
public class TopicController {

    private static final String ENDPOINT = "/api/topics";
    private static final int MAX_PAGE_SIZE = 100;

    private final TopicViewService topicViewService;
    private final TopicAuthoringService topicAuthoringService;
    private final CommandGateway commandGateway;

    public TopicController(TopicViewService topicViewService,
                           TopicAuthoringService topicAuthoringService,
                           CommandGateway commandGateway) {
        this.topicViewService = topicViewService;
        this.topicAuthoringService = topicAuthoringService;
        this.commandGateway = commandGateway;
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<PageResponse<TopicCardView>>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TopicCategory category,
            @RequestParam(defaultValue = "false") boolean followed,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(required = false) TopicSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        PageParams.validate(page, size, MAX_PAGE_SIZE);
        return topicViewService
                .list(SecurityHelper.getUserId(), q, category, followed, mine, sort, page, size)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/facets")
    public CompletableFuture<ResponseEntity<TopicFacetsView>> facets(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean followed) {
        return topicViewService
                .facets(SecurityHelper.getUserId(), q, followed)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/{topicId}/overview")
    public CompletableFuture<ResponseEntity<TopicOverviewView>> overview(@PathVariable String topicId) {
        return topicViewService
                .overview(SecurityHelper.getUserId(), topicId)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/{topicId}/leaderboard")
    public CompletableFuture<ResponseEntity<TopicLeaderboardView>> leaderboard(
            @PathVariable String topicId,
            @RequestParam(defaultValue = "ALL_TIME") LeaderboardPeriod period,
            @RequestParam(required = false) String month,
            @RequestParam(defaultValue = "WORLD") LeaderboardScope scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        PageParams.validate(page, size, MAX_PAGE_SIZE);
        return topicViewService
                .leaderboard(SecurityHelper.getUserId(), topicId, period, LeaderboardMonth.validate(month), scope, page, size)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/{topicId}/follow")
    public CompletableFuture<ResponseEntity<Void>> follow(@PathVariable String topicId) {
        String userId = SecurityHelper.getUserId();
        return commandGateway
                .send(new TopicFollowerCommand.FollowTopicCommand(FollowerIds.topic(topicId, userId), topicId, userId))
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @DeleteMapping("/{topicId}/follow")
    public CompletableFuture<ResponseEntity<Void>> unfollow(@PathVariable String topicId) {
        String userId = SecurityHelper.getUserId();
        return commandGateway
                .send(new TopicFollowerCommand.UnfollowTopicCommand(FollowerIds.topic(topicId, userId), userId))
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<IdResponse>> create(@Valid @RequestBody CreateTopicRequest request) {
        return topicAuthoringService
                .create(SecurityHelper.getUserId(), request)
                .thenApply(topicId -> ResponseEntityBuilder.creation(ENDPOINT, topicId));
    }

    @PutMapping("/{topicId}/name")
    public CompletableFuture<ResponseEntity<Void>> updateName(
            @PathVariable String topicId,
            @Valid @RequestBody UpdateTopicNameRequest request) {
        return topicAuthoringService
                .updateName(topicId, SecurityHelper.getUserId(), request)
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{topicId}/description")
    public CompletableFuture<ResponseEntity<Void>> updateDescription(
            @PathVariable String topicId,
            @Valid @RequestBody UpdateTopicDescriptionRequest request) {
        return topicAuthoringService
                .updateDescription(topicId, SecurityHelper.getUserId(), request)
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{topicId}/category")
    public CompletableFuture<ResponseEntity<Void>> updateCategory(
            @PathVariable String topicId,
            @Valid @RequestBody UpdateTopicCategoryRequest request) {
        return topicAuthoringService
                .updateCategory(topicId, SecurityHelper.getUserId(), request)
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{topicId}/emoji")
    public CompletableFuture<ResponseEntity<Void>> updateEmoji(
            @PathVariable String topicId,
            @Valid @RequestBody UpdateTopicEmojiRequest request) {
        return topicAuthoringService
                .updateEmoji(topicId, SecurityHelper.getUserId(), request)
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{topicId}/color")
    public CompletableFuture<ResponseEntity<Void>> updateColor(
            @PathVariable String topicId,
            @Valid @RequestBody UpdateTopicColorRequest request) {
        return topicAuthoringService
                .updateColor(topicId, SecurityHelper.getUserId(), request)
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{topicId}/image-url")
    public CompletableFuture<ResponseEntity<Void>> updateImageUrl(
            @PathVariable String topicId,
            @Valid @RequestBody UpdateTopicImageUrlRequest request) {
        return topicAuthoringService
                .updateImageUrl(topicId, SecurityHelper.getUserId(), request)
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{topicId}/banner-url")
    public CompletableFuture<ResponseEntity<Void>> updateBannerUrl(
            @PathVariable String topicId,
            @Valid @RequestBody UpdateTopicBannerUrlRequest request) {
        return topicAuthoringService
                .updateBannerUrl(topicId, SecurityHelper.getUserId(), request)
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{topicId}/publish")
    public CompletableFuture<ResponseEntity<Void>> publish(@PathVariable String topicId) {
        return topicAuthoringService
                .publish(topicId, SecurityHelper.getUserId())
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    @GetMapping("/{topicId}/questions")
    public CompletableFuture<ResponseEntity<PageResponse<QuestionEditorView>>> questions(
            @PathVariable String topicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageParams.validate(page, size, MAX_PAGE_SIZE);
        return topicAuthoringService
                .questions(topicId, SecurityHelper.getUserId(), page, size)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/{topicId}/questions")
    public CompletableFuture<ResponseEntity<IdResponse>> createQuestion(
            @PathVariable String topicId,
            @Valid @RequestBody CreateQuestionRequest request) {
        return topicAuthoringService
                .createQuestion(topicId, SecurityHelper.getUserId(), request)
                .thenApply(questionId -> ResponseEntityBuilder.creation(
                        ENDPOINT + "/" + topicId + "/questions", questionId));
    }
}
