package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.TopicAuthoringService;
import com.joutio.bff.infrastructure.in.api.request.AddQuestionTranslationRequest;
import com.joutio.bff.infrastructure.in.api.request.RejectQuestionRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateQuestionAnswersRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateQuestionCorrectAnswerRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateQuestionImageUrlRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateQuestionTextRequest;
import com.joutio.microservice.security.SecurityHelper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/**
 * {@code /api/questions} — édition d'auteur et modération des questions (propriétaire du sujet
 * uniquement).
 */
@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final TopicAuthoringService topicAuthoringService;

    public QuestionController(TopicAuthoringService topicAuthoringService) {
        this.topicAuthoringService = topicAuthoringService;
    }

    @PostMapping("/{questionId}/translations")
    public CompletableFuture<ResponseEntity<Void>> addTranslation(
            @PathVariable String questionId,
            @Valid @RequestBody AddQuestionTranslationRequest request) {
        return topicAuthoringService
                .addTranslation(questionId, SecurityHelper.getUserId(), request)
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    @PutMapping("/{questionId}/text")
    public CompletableFuture<ResponseEntity<Void>> updateText(
            @PathVariable String questionId,
            @Valid @RequestBody UpdateQuestionTextRequest request) {
        return topicAuthoringService
                .updateQuestionText(questionId, SecurityHelper.getUserId(), request)
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{questionId}/answers")
    public CompletableFuture<ResponseEntity<Void>> updateAnswers(
            @PathVariable String questionId,
            @Valid @RequestBody UpdateQuestionAnswersRequest request) {
        return topicAuthoringService
                .updateQuestionAnswers(questionId, SecurityHelper.getUserId(), request)
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{questionId}/correct-answer")
    public CompletableFuture<ResponseEntity<Void>> updateCorrectAnswer(
            @PathVariable String questionId,
            @Valid @RequestBody UpdateQuestionCorrectAnswerRequest request) {
        return topicAuthoringService
                .updateQuestionCorrectAnswer(questionId, SecurityHelper.getUserId(), request)
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{questionId}/image-url")
    public CompletableFuture<ResponseEntity<Void>> updateImageUrl(
            @PathVariable String questionId,
            @Valid @RequestBody UpdateQuestionImageUrlRequest request) {
        return topicAuthoringService
                .updateQuestionImageUrl(questionId, SecurityHelper.getUserId(), request)
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{questionId}/approve")
    public CompletableFuture<ResponseEntity<Void>> approve(@PathVariable String questionId) {
        return topicAuthoringService
                .approveQuestion(questionId, SecurityHelper.getUserId())
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    @PostMapping("/{questionId}/reject")
    public CompletableFuture<ResponseEntity<Void>> reject(
            @PathVariable String questionId,
            @Valid @RequestBody(required = false) RejectQuestionRequest request) {
        String reason = request == null ? null : request.reason();
        return topicAuthoringService
                .rejectQuestion(questionId, SecurityHelper.getUserId(), reason)
                .thenApply(_ -> ResponseEntity.ok().build());
    }
}
