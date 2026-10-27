package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.ProfileViewService;
import com.joutio.bff.infrastructure.in.api.response.PresenceView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/**
 * {@code /api/presence} — présence d'un joueur ({@code 404} si le joueur ne s'est jamais
 * connecté ; l'absence vaut « hors ligne » côté client).
 */
@RestController
@RequestMapping("/api/presence")
public class PresenceController {

    private final ProfileViewService profileViewService;

    public PresenceController(ProfileViewService profileViewService) {
        this.profileViewService = profileViewService;
    }

    @GetMapping("/{userId}")
    public CompletableFuture<ResponseEntity<PresenceView>> presence(@PathVariable String userId) {
        return profileViewService.presence(userId)
                .thenApply(presence -> presence
                        .map(ResponseEntity::ok)
                        .orElseGet(() -> ResponseEntity.notFound().build()));
    }
}
