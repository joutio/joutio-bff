package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.infrastructure.in.api.response.ServerTimeView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * {@code /api/clock} — horloge serveur (synchronisation des chronos de l'arène).
 */
@RestController
@RequestMapping("/api/clock")
public class ClockController {

    @GetMapping
    public ResponseEntity<ServerTimeView> clock() {
        Instant now = Instant.now();
        return ResponseEntity.ok(new ServerTimeView(now, now.toEpochMilli()));
    }
}
