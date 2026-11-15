package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.notice.ContentNoticeService;
import com.joutio.bff.infrastructure.in.api.request.ContentNoticeRequest;
import com.joutio.microservice.security.SecurityHelper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code /api/public/content-notices} — formulaire de notification de contenu illicite (landing « Signaler un contenu »,
 * app), **sans authentification** (exception actée du 2026-11-15, chaîne {@code PublicApiSecurityConfiguration}).
 * Répond {@code 202} avec l'identifiant de la notification, à rappeler dans tout échange.
 */
@RestController
@RequestMapping("/api/public/content-notices")
public class ContentNoticeController {

    private final ContentNoticeService service;

    public ContentNoticeController(ContentNoticeService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> submit(@Valid @RequestBody ContentNoticeRequest request, HttpServletRequest http) {
        var submission = new ContentNoticeService.Submission(request.name(), request.email(), request.quality(),
                request.contentUrl(), request.category(), request.description(), request.rightsEvidence(),
                request.goodFaith(), request.website());
        String reporter = SecurityHelper.findUserId().orElse(null);
        var id = service.submit(submission, reporter, clientIp(http));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("id", id.toString()));
    }

    /** IP du client derrière l'ingress ({@code X-Forwarded-For}), sinon l'adresse de la connexion. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
