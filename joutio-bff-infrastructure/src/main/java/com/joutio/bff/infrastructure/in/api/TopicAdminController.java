package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.TopicAdminService;
import com.joutio.bff.infrastructure.in.api.request.CloseTopicRequest;
import com.joutio.bff.infrastructure.in.api.request.SetTopicOfficialRequest;
import com.joutio.microservice.security.SecurityHelper;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/**
 * {@code /api/admin/topics} — badge « Officiel » et fermeture des sujets sur notification (LCEN/DSA). Surface
 * réservée à {@code ROLE_ADMIN} ; consommée par la console (page Questions, page Notifications).
 */
@RestController
@RequestMapping("/api/admin/topics")
@PreAuthorize("hasRole('ADMIN')")
public class TopicAdminController {

    private final TopicAdminService topicAdminService;

    public TopicAdminController(TopicAdminService topicAdminService) {
        this.topicAdminService = topicAdminService;
    }

    @PutMapping("/{topicId}/official")
    public CompletableFuture<ResponseEntity<Void>> official(
            @PathVariable String topicId, @RequestBody SetTopicOfficialRequest request) {
        return topicAdminService
                .setOfficial(topicId, request.official(), SecurityHelper.getUserId())
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{topicId}/close")
    public CompletableFuture<ResponseEntity<Void>> close(
            @PathVariable String topicId, @Valid @RequestBody CloseTopicRequest request) {
        return topicAdminService
                .close(topicId, request.reason(), SecurityHelper.getUserId())
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{topicId}/reopen")
    public CompletableFuture<ResponseEntity<Void>> reopen(@PathVariable String topicId) {
        return topicAdminService.reopen(topicId, SecurityHelper.getUserId()).thenApply(_ -> ResponseEntity.noContent().build());
    }
}
