package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.ChallengeViewService;
import com.joutio.bff.infrastructure.in.api.request.CreateChallengeRequest;
import com.joutio.bff.infrastructure.in.api.response.ChallengeView;
import com.joutio.microservice.core.infrastructure.in.api.ResponseEntityBuilder;
import com.joutio.microservice.core.infrastructure.in.api.response.IdResponse;
import com.joutio.microservice.security.SecurityHelper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /api/challenges} — défi nominatif : création, consultation, acceptation/refus/annulation.
 * La salle temps réel est créée à l'acceptation (saga matchmaking).
 */
@RestController
@RequestMapping("/api/challenges")
public class ChallengeController {

    private static final String ENDPOINT = "/api/challenges";

    private final ChallengeViewService challengeViewService;

    public ChallengeController(ChallengeViewService challengeViewService) {
        this.challengeViewService = challengeViewService;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<IdResponse>> create(
            @Valid @RequestBody CreateChallengeRequest request) {
        return challengeViewService
                .create(SecurityHelper.getUserId(), request)
                .thenApply(challengeId -> ResponseEntityBuilder.creation(ENDPOINT, challengeId));
    }

    @GetMapping("/mine")
    public CompletableFuture<ResponseEntity<List<ChallengeView>>> mine() {
        return challengeViewService.mine(SecurityHelper.getUserId()).thenApply(ResponseEntity::ok);
    }

    @GetMapping("/{challengeId}")
    public CompletableFuture<ResponseEntity<ChallengeView>> get(@PathVariable String challengeId) {
        return challengeViewService.get(challengeId).thenApply(ResponseEntity::ok);
    }

    /** L'invité accepte : la salle est créée par la saga (`roomId` apparaît dans la vue). */
    @PostMapping("/{challengeId}/accept")
    public CompletableFuture<ResponseEntity<Void>> accept(@PathVariable String challengeId) {
        return challengeViewService
                .accept(challengeId, SecurityHelper.getUserId())
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    @PostMapping("/{challengeId}/decline")
    public CompletableFuture<ResponseEntity<Void>> decline(@PathVariable String challengeId) {
        return challengeViewService
                .decline(challengeId, SecurityHelper.getUserId())
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    /** Le lanceur annule un défi resté sans réponse. */
    @PostMapping("/{challengeId}/cancel")
    public CompletableFuture<ResponseEntity<Void>> cancel(@PathVariable String challengeId) {
        return challengeViewService
                .cancel(challengeId, SecurityHelper.getUserId())
                .thenApply(_ -> ResponseEntity.ok().build());
    }
}
