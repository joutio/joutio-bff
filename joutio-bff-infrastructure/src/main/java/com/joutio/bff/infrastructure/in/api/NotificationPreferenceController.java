package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.NotificationViewService;
import com.joutio.bff.infrastructure.in.api.request.UpdateNotificationPreferenceRequest;
import com.joutio.bff.infrastructure.in.api.response.NotificationPreferenceView;
import com.joutio.microservice.security.SecurityHelper;
import com.joutio.notification.domain.model.NotificationCategory;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /api/notification-preferences} — préférences persistées du joueur courant.
 */
@RestController
@RequestMapping("/api/notification-preferences")
public class NotificationPreferenceController {

    private final NotificationViewService notificationViewService;

    public NotificationPreferenceController(NotificationViewService notificationViewService) {
        this.notificationViewService = notificationViewService;
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<List<NotificationPreferenceView>>> list() {
        return notificationViewService
                .preferences(SecurityHelper.getUserId())
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/{category}")
    public CompletableFuture<ResponseEntity<Void>> update(
            @PathVariable NotificationCategory category,
            @Valid @RequestBody UpdateNotificationPreferenceRequest request) {
        return notificationViewService
                .updatePreference(SecurityHelper.getUserId(), category, request.enabled())
                .thenApply(_ -> ResponseEntity.noContent().build());
    }
}
