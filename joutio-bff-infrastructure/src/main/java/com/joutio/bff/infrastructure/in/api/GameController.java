package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.GameViewService;
import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.request.AnswerQuestionRequest;
import com.joutio.bff.infrastructure.in.api.response.ActiveGameView;
import com.joutio.bff.infrastructure.in.api.response.EventEnvelopeResponse;
import com.joutio.bff.infrastructure.in.api.response.GameResultView;
import com.joutio.bff.infrastructure.out.messaging.mapper.GameEventNotificationMapper;
import com.joutio.game.domain.event.GameEvent;
import com.joutio.microservice.core.domain.model.notification.EventEnvelope;
import com.joutio.microservice.security.SecurityHelper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /api/games} — arène : réponse, abandon, historique de notifications. La création d'une
 * partie passe par la salle ({@code POST /api/rooms} ou {@code POST /api/rooms/bot}).
 */
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameViewService gameViewService;

    public GameController(GameViewService gameViewService) {
        this.gameViewService = gameViewService;
    }

    /**
     * Collection des parties en cours du joueur (reprise), les plus récentes d'abord.
     * Seul le filtre {@code active=true} est exposé (l'historique vit sous
     * {@code /api/profiles/{id}/games}) ; collection vide si aucune partie — jamais de 404.
     */
    @GetMapping
    public CompletableFuture<ResponseEntity<List<ActiveGameView>>> activeGames(
            @RequestParam(name = "active", defaultValue = "false") boolean active) {
        if (!active) {
            throw new BffProblems.InvalidGameListRequestProblem(
                    "Seules les parties actives sont exposées : utiliser ?active=true");
        }
        return gameViewService.activeGames(SecurityHelper.getUserId()).thenApply(ResponseEntity::ok);
    }

    @PostMapping("/{gameId}/answer")
    public CompletableFuture<ResponseEntity<Void>> answer(@PathVariable String gameId,
                                                          @Valid @RequestBody AnswerQuestionRequest request) {
        return gameViewService
                .answer(gameId, SecurityHelper.getUserId(), request.choice())
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    @PostMapping("/{gameId}/abandon")
    public CompletableFuture<ResponseEntity<Void>> abandon(@PathVariable String gameId) {
        return gameViewService
                .abandon(gameId, SecurityHelper.getUserId())
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    @PostMapping("/{gameId}/cancel")
    public CompletableFuture<ResponseEntity<Void>> cancel(@PathVariable String gameId) {
        return gameViewService
                .cancel(gameId)
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    /** Résultat d'une partie terminée : score, détail, récompense XP et progression du joueur. */
    @GetMapping("/{gameId}/result")
    public CompletableFuture<ResponseEntity<GameResultView>> result(@PathVariable String gameId) {
        return gameViewService
                .result(gameId, SecurityHelper.getUserId())
                .thenApply(ResponseEntity::ok);
    }

    /**
     * Historique des notifications d'une partie (même contrat que le push WebSocket) : le client
     * bootstrap son read model puis déduplique par {@code sequenceNumber}. Le service game mappe
     * son event store en {@code EventEnvelope} typés ; le BFF produit le contrat web
     * ({@link EventEnvelopeResponse}).
     */
    @GetMapping("/{gameId}/notifications")
    public CompletableFuture<ResponseEntity<List<EventEnvelopeResponse>>> notifications(
            @PathVariable String gameId) {
        return gameViewService
                .events(gameId, SecurityHelper.getUserId())
                .thenApply(envelopes -> envelopes.stream()
                        .map(GameController::toResponse)
                        .flatMap(Optional::stream)
                        .toList())
                .thenApply(ResponseEntity::ok);
    }

    private static Optional<EventEnvelopeResponse> toResponse(EventEnvelope envelope) {
        if (!(envelope.payload() instanceof GameEvent event)) {
            return Optional.empty();
        }
        return GameEventNotificationMapper.toNotification(event)
                .map(notification -> EventEnvelopeResponse.of(
                        envelope.aggregateId(),
                        envelope.sequenceNumber(),
                        envelope.timestamp(),
                        notification.type().name(),
                        notification));
    }
}
