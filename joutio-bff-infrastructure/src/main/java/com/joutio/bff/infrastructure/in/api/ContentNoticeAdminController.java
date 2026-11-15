package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.notice.ContentNotice;
import com.joutio.bff.application.notice.ContentNoticeService;
import com.joutio.bff.infrastructure.in.api.request.DecideContentNoticeRequest;
import com.joutio.microservice.security.SecurityHelper;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /api/admin/content-notices} — file des notifications de contenu de la console (page « Notifications ») et
 * décision motivée. L'action (retirer l'image, fermer le sujet) passe par les endpoints admin média et sujets.
 */
@RestController
@RequestMapping("/api/admin/content-notices")
@PreAuthorize("hasRole('ADMIN')")
public class ContentNoticeAdminController {

    private final ContentNoticeService service;

    public ContentNoticeAdminController(ContentNoticeService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ContentNotice>> list(
            @RequestParam(required = false) ContentNotice.ContentNoticeStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.list(status, page, size));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Long>> summary() {
        return ResponseEntity.ok(Map.of("received", service.count(ContentNotice.ContentNoticeStatus.RECEIVED)));
    }

    @PostMapping("/{id}/decision")
    public ResponseEntity<ContentNotice> decide(@PathVariable UUID id, @Valid @RequestBody DecideContentNoticeRequest request) {
        return ResponseEntity.ok(service.decide(id, request.decision(), request.reason(), SecurityHelper.getUserId()));
    }
}
