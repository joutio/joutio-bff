package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.ExpoPushService;
import com.joutio.bff.infrastructure.in.api.request.ExpoPushSubscriptionRequest;
import com.joutio.microservice.security.SecurityHelper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/push/expo/subscriptions")
public class ExpoPushController {
    private final ExpoPushService service;
    public ExpoPushController(ExpoPushService service) { this.service=service; }
    @PutMapping public ResponseEntity<Void> subscribe(@Valid @RequestBody ExpoPushSubscriptionRequest request) {
        service.register(SecurityHelper.getUserId(), request.installationId(), request.token(), request.platform(), request.rematches());
        return ResponseEntity.noContent().build();
    }
    @DeleteMapping public ResponseEntity<Void> unsubscribe(@RequestParam String installationId) {
        service.unregister(SecurityHelper.getUserId(), installationId);
        return ResponseEntity.noContent().build();
    }
}
