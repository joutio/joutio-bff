package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.QuestionAdminService;
import com.joutio.bff.application.QuestionReportService;
import com.joutio.bff.infrastructure.in.api.request.AddQuestionTranslationRequest;
import com.joutio.bff.infrastructure.in.api.request.ArchiveQuestionRequest;
import com.joutio.bff.infrastructure.in.api.request.CreateQuestionRequest;
import com.joutio.bff.infrastructure.in.api.request.ReinstateQuestionRequest;
import com.joutio.bff.infrastructure.in.api.request.ResolveQuestionReportsRequest;
import com.joutio.bff.infrastructure.in.api.request.RestoreQuestionRequest;
import com.joutio.bff.infrastructure.in.api.request.SuspendQuestionRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateQuestionAnswersRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateQuestionCorrectAnswerRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateQuestionImageUrlRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateQuestionTextRequest;
import com.joutio.bff.infrastructure.in.api.response.AdminQuestionDetailView;
import com.joutio.bff.infrastructure.in.api.response.AdminQuestionReportResolutionView;
import com.joutio.bff.infrastructure.in.api.response.AdminQuestionReportSummaryView;
import com.joutio.bff.infrastructure.in.api.response.AdminQuestionSummaryView;
import com.joutio.bff.infrastructure.in.api.response.AdminQuestionView;
import com.joutio.bff.infrastructure.in.api.response.AdminTopicOptionView;
import com.joutio.bff.infrastructure.in.api.response.CreatedQuestionView;
import com.joutio.bff.infrastructure.in.api.response.PageResponse;
import com.joutio.microservice.security.SecurityHelper;
import com.joutio.theme.domain.model.QuestionAdminFilter;
import com.joutio.theme.domain.model.QuestionAdminSort;
import com.joutio.theme.domain.model.QuestionDifficulty;
import com.joutio.theme.domain.model.QuestionModerationFilter;
import com.joutio.theme.domain.model.QuestionReportSort;
import com.joutio.theme.domain.model.QuestionReportStatus;
import com.joutio.theme.domain.model.QuestionReportSummaryFilter;
import com.joutio.theme.domain.model.QuestionStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /api/admin} — administration du catalogue de questions, de la file de signalements et
 * des actions de modération. Surface réservée à {@code ROLE_ADMIN}.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class QuestionAdminController {

    private final QuestionAdminService questionAdminService;
    private final QuestionReportService questionReportService;

    public QuestionAdminController(QuestionAdminService questionAdminService,
                                   QuestionReportService questionReportService) {
        this.questionAdminService = questionAdminService;
        this.questionReportService = questionReportService;
    }

    @GetMapping("/questions")
    public CompletableFuture<ResponseEntity<PageResponse<AdminQuestionView>>> questions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String topicId,
            @RequestParam(required = false) QuestionStatus status,
            @RequestParam(required = false) QuestionDifficulty difficulty,
            @RequestParam(required = false) QuestionModerationFilter moderation,
            @RequestParam(required = false) Boolean reportedOnly,
            @RequestParam(defaultValue = "RECENT_UPDATED") QuestionAdminSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        QuestionAdminFilter filter = new QuestionAdminFilter(
                q, topicId, status, difficulty, moderation, reportedOnly);
        return questionAdminService.list(filter, sort, safePage(page), safeSize(size))
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/questions/{questionId}")
    public CompletableFuture<ResponseEntity<AdminQuestionDetailView>> question(
            @PathVariable String questionId) {
        return questionAdminService.detail(questionId).thenApply(ResponseEntity::ok);
    }

    @GetMapping("/question-topics")
    public CompletableFuture<ResponseEntity<List<AdminTopicOptionView>>> topics() {
        return questionAdminService.topics().thenApply(ResponseEntity::ok);
    }

    @GetMapping("/question-reports")
    public CompletableFuture<ResponseEntity<PageResponse<AdminQuestionSummaryView>>> reports(
            @RequestParam(defaultValue = "OPEN") QuestionReportStatus status,
            @RequestParam(required = false) String topicId,
            @RequestParam(required = false) Boolean suspended,
            @RequestParam(defaultValue = "RECENT_REPORT") QuestionReportSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        QuestionReportSummaryFilter filter = new QuestionReportSummaryFilter(status, topicId, suspended);
        return questionAdminService.queue(filter, sort, safePage(page), safeSize(size))
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/question-reports/summary")
    public CompletableFuture<ResponseEntity<AdminQuestionReportSummaryView>> reportsSummary() {
        return questionAdminService.summary().thenApply(ResponseEntity::ok);
    }

    @PostMapping("/topics/{topicId}/questions")
    public CompletableFuture<ResponseEntity<CreatedQuestionView>> createQuestion(
            @PathVariable String topicId,
            @Valid @RequestBody CreateQuestionRequest request) {
        return questionAdminService.create(topicId, SecurityHelper.getUserId(), request)
                .thenApply(questionId -> ResponseEntity
                        .created(URI.create("/api/admin/questions/" + questionId))
                        .body(new CreatedQuestionView(questionId)));
    }

    @PostMapping("/questions/{questionId}/translations")
    public CompletableFuture<ResponseEntity<Void>> addTranslation(
            @PathVariable String questionId,
            @Valid @RequestBody AddQuestionTranslationRequest request) {
        return questionAdminService
                .addTranslation(questionId, SecurityHelper.getUserId(), request)
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    @PutMapping("/questions/{questionId}/text")
    public CompletableFuture<ResponseEntity<Void>> updateText(
            @PathVariable String questionId,
            @Valid @RequestBody UpdateQuestionTextRequest request) {
        return questionAdminService
                .updateText(questionId, SecurityHelper.getUserId(), request)
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @PutMapping("/questions/{questionId}/answers")
    public CompletableFuture<ResponseEntity<Void>> updateAnswers(
            @PathVariable String questionId,
            @Valid @RequestBody UpdateQuestionAnswersRequest request) {
        return questionAdminService
                .updateAnswers(questionId, SecurityHelper.getUserId(), request)
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @PutMapping("/questions/{questionId}/correct-answer")
    public CompletableFuture<ResponseEntity<Void>> updateCorrectAnswer(
            @PathVariable String questionId,
            @Valid @RequestBody UpdateQuestionCorrectAnswerRequest request) {
        return questionAdminService
                .updateCorrectAnswer(questionId, SecurityHelper.getUserId(), request)
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @PutMapping("/questions/{questionId}/image-url")
    public CompletableFuture<ResponseEntity<Void>> updateImageUrl(
            @PathVariable String questionId,
            @Valid @RequestBody UpdateQuestionImageUrlRequest request) {
        return questionAdminService
                .updateImageUrl(questionId, SecurityHelper.getUserId(), request)
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @PostMapping("/questions/{questionId}/approve")
    public CompletableFuture<ResponseEntity<Void>> approve(@PathVariable String questionId) {
        return questionAdminService
                .approve(questionId, SecurityHelper.getUserId())
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    @PostMapping("/questions/{questionId}/reject")
    public CompletableFuture<ResponseEntity<Void>> reject(
            @PathVariable String questionId,
            @Valid @RequestBody(required = false) com.joutio.bff.infrastructure.in.api.request.RejectQuestionRequest request) {
        String reason = request == null ? null : request.reason();
        return questionAdminService
                .reject(questionId, SecurityHelper.getUserId(), reason)
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    @PostMapping("/questions/{questionId}/suspend")
    public CompletableFuture<ResponseEntity<Void>> suspend(
            @PathVariable String questionId,
            @Valid @RequestBody(required = false) SuspendQuestionRequest request) {
        return questionAdminService
                .suspend(questionId, SecurityHelper.getUserId(), request)
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    @PostMapping("/questions/{questionId}/reinstate")
    public CompletableFuture<ResponseEntity<Void>> reinstate(
            @PathVariable String questionId,
            @Valid @RequestBody(required = false) ReinstateQuestionRequest request) {
        return questionAdminService
                .reinstate(questionId, SecurityHelper.getUserId(), request)
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    @PostMapping("/questions/{questionId}/archive")
    public CompletableFuture<ResponseEntity<Void>> archive(
            @PathVariable String questionId,
            @Valid @RequestBody(required = false) ArchiveQuestionRequest request) {
        return questionAdminService
                .archive(questionId, SecurityHelper.getUserId(), request)
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    @PostMapping("/questions/{questionId}/restore")
    public CompletableFuture<ResponseEntity<Void>> restore(
            @PathVariable String questionId,
            @Valid @RequestBody(required = false) RestoreQuestionRequest request) {
        return questionAdminService
                .restore(questionId, SecurityHelper.getUserId(), request)
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    @PostMapping("/question-reports/{reportId}/resolve")
    public CompletableFuture<ResponseEntity<AdminQuestionReportResolutionView>> resolveReport(
            @PathVariable String reportId,
            @Valid @RequestBody ResolveQuestionReportsRequest request) {
        return questionReportService
                .resolveAdmin(reportId, SecurityHelper.getUserId(), request)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/questions/{questionId}/reports/resolve")
    public CompletableFuture<ResponseEntity<AdminQuestionReportResolutionView>> resolveReports(
            @PathVariable String questionId,
            @Valid @RequestBody ResolveQuestionReportsRequest request) {
        return questionReportService
                .resolveAdminAll(questionId, SecurityHelper.getUserId(), request)
                .thenApply(ResponseEntity::ok);
    }

    private static int safeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private static int safePage(int page) {
        return Math.max(page, 0);
    }
}
