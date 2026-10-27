package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.PushSubscriptionService;
import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.request.PushSubscriptionRequest;
import com.joutio.bff.infrastructure.in.api.response.VapidKeyView;
import com.joutio.bff.infrastructure.out.push.WebPushProperties;
import com.joutio.microservice.security.SecurityHelper;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/push} — Web Push du navigateur : clé publique VAPID, enregistrement idempotent de
 * l'abonnement (actor = JWT), désabonnement.
 */
@RestController
@RequestMapping("/api/push")
public class PushController {

    private static final int MAX_USER_AGENT_LENGTH = 255;

    private final PushSubscriptionService subscriptionService;
    private final WebPushProperties properties;

    public PushController(PushSubscriptionService subscriptionService, WebPushProperties properties) {
        this.subscriptionService = subscriptionService;
        this.properties = properties;
    }

    @GetMapping("/vapid-public-key")
    public ResponseEntity<VapidKeyView> vapidPublicKey() {
        if (!properties.configured()) {
            throw new BffProblems.PushDisabledProblem();
        }
        return ResponseEntity.ok(new VapidKeyView(properties.vapid().publicKey()));
    }

    @PutMapping("/subscriptions")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody PushSubscriptionRequest request,
                                          @RequestHeader(value = HttpHeaders.USER_AGENT, required = false)
                                          String userAgent) {
        subscriptionService.register(
                SecurityHelper.getUserId(),
                request.endpoint(),
                request.keys().p256dh(),
                request.keys().auth(),
                truncate(userAgent));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/subscriptions")
    public ResponseEntity<Void> unsubscribe(@RequestParam String endpoint) {
        subscriptionService.unregister(SecurityHelper.getUserId(), endpoint);
        return ResponseEntity.noContent().build();
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_USER_AGENT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_USER_AGENT_LENGTH);
    }
}
