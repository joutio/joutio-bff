package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.TelemetryService;
import com.joutio.bff.infrastructure.in.api.request.ClientErrorRequest;
import com.joutio.bff.infrastructure.in.api.request.ProductEventRequest;
import com.joutio.microservice.security.SecurityHelper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/telemetry} — collecte best-effort de l'activation produit et des erreurs navigateur.
 * Aucun SaaS : les données partent en métriques Prometheus et logs Alloy/Loki/Grafana.
 */
@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @PostMapping("/events")
    public ResponseEntity<Void> event(@Valid @RequestBody ProductEventRequest request) {
        return respond(telemetryService.recordEvent(
                SecurityHelper.getUserId(), request.name(), request.properties()));
    }

    @PostMapping("/client-errors")
    public ResponseEntity<Void> clientError(@Valid @RequestBody ClientErrorRequest request) {
        return respond(telemetryService.recordClientError(
                SecurityHelper.getUserId(),
                request.type(),
                request.message(),
                request.stack(),
                request.route(),
                request.buildId(),
                request.source()));
    }

    private static ResponseEntity<Void> respond(TelemetryService.RecordResult result) {
        return switch (result) {
            case ACCEPTED -> ResponseEntity.accepted().build();
            case INVALID -> ResponseEntity.badRequest().build();
            case RATE_LIMITED -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        };
    }
}
