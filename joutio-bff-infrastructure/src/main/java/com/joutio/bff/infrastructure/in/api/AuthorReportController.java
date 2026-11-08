package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.QuestionReportService;
import com.joutio.microservice.security.SecurityHelper;

import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/question-reports")
public class AuthorReportController {
    private final QuestionReportService service;

    public AuthorReportController(QuestionReportService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public CompletableFuture<QuestionReportService.ReportDetail> detail(@PathVariable String id) {
        return service.ownerReport(id, SecurityHelper.getUserId());
    }
}
