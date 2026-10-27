package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.QuestionReportService;
import com.joutio.bff.infrastructure.in.api.request.ReportQuestionRequest;
import com.joutio.bff.infrastructure.in.api.request.ResolveQuestionReportsRequest;
import com.joutio.bff.infrastructure.in.api.response.AdminQuestionReportResolutionView;
import com.joutio.bff.infrastructure.in.api.response.OwnerQuestionReportView;
import com.joutio.bff.infrastructure.in.api.response.QuestionReportAckView;
import com.joutio.microservice.security.SecurityHelper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /api/questions/{id}/reports} — dépôt d'un signalement par un joueur, lecture et
 * clôture par le propriétaire du sujet (sans identité du signalant).
 */
@RestController
@RequestMapping("/api/questions/{questionId}/reports")
public class QuestionReportController {

    private final QuestionReportService questionReportService;

    public QuestionReportController(QuestionReportService questionReportService) {
        this.questionReportService = questionReportService;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<QuestionReportAckView>> report(
            @PathVariable String questionId,
            @Valid @RequestBody ReportQuestionRequest request) {
        return questionReportService.report(questionId, SecurityHelper.getUserId(), request)
                .thenApply(ack -> ResponseEntity
                        .status(ack.created() ? HttpStatus.CREATED : HttpStatus.OK)
                        .body(ack));
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<List<OwnerQuestionReportView>>> ownerReports(
            @PathVariable String questionId) {
        return questionReportService.ownerReports(questionId, SecurityHelper.getUserId())
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/{reportId}/resolve")
    public CompletableFuture<ResponseEntity<AdminQuestionReportResolutionView>> resolve(
            @PathVariable String questionId,
            @PathVariable String reportId,
            @Valid @RequestBody ResolveQuestionReportsRequest request) {
        return questionReportService
                .resolveOwner(questionId, reportId, SecurityHelper.getUserId(), request)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/resolve")
    public CompletableFuture<ResponseEntity<AdminQuestionReportResolutionView>> resolveAll(
            @PathVariable String questionId,
            @Valid @RequestBody ResolveQuestionReportsRequest request) {
        return questionReportService
                .resolveOwnerAll(questionId, SecurityHelper.getUserId(), request)
                .thenApply(ResponseEntity::ok);
    }
}
