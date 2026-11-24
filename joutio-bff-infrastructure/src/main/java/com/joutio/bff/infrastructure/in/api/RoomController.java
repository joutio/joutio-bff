package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.RoomViewService;
import com.joutio.bff.infrastructure.in.api.request.CreateBotRoomRequest;
import com.joutio.bff.infrastructure.in.api.request.CreateChallengeRequest;
import com.joutio.bff.infrastructure.in.api.request.CreateRoomRequest;
import com.joutio.bff.infrastructure.in.api.response.EventEnvelopeResponse;
import com.joutio.bff.infrastructure.in.api.response.RoomView;
import com.joutio.bff.infrastructure.out.messaging.mapper.RoomEventResponseMapper;
import com.joutio.matchmaking.domain.event.RoomEvent;
import com.joutio.microservice.core.domain.model.event.EventEnvelope;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /api/rooms} — salles : création (défi, salon partagé, bot), consultation, réponse à une invitation, relance
 * et annulation. La présence passe par le canal STOMP {@code /topic/rooms/{roomId}/presence}
 * ({@code PresenceChannelListener}).
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private static final String ENDPOINT = "/api/rooms";

    private final RoomViewService roomViewService;

    public RoomController(RoomViewService roomViewService) {
        this.roomViewService = roomViewService;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<IdResponse>> create(@Valid @RequestBody CreateRoomRequest request) {
        return roomViewService
                .create(SecurityHelper.getUserId(), request)
                .thenApply(roomId -> ResponseEntityBuilder.creation(ENDPOINT, roomId));
    }

    /** Défi (ou revanche si {@code gameId}) : la salle naît {@code INVITED}, l'invité accepte ou refuse. */
    @PostMapping("/challenge")
    public CompletableFuture<ResponseEntity<IdResponse>> createChallenge(
            @Valid @RequestBody CreateChallengeRequest request) {
        return roomViewService
                .createChallenge(SecurityHelper.getUserId(), request)
                .thenApply(roomId -> ResponseEntityBuilder.creation(ENDPOINT, roomId));
    }

    /** Salle contre le bot : le bot est joint automatiquement par la saga, l'humain apparaît ensuite. */
    @PostMapping("/bot")
    public CompletableFuture<ResponseEntity<IdResponse>> createBot(@Valid @RequestBody CreateBotRoomRequest request) {
        return roomViewService
                .createBot(SecurityHelper.getUserId(), request)
                .thenApply(roomId -> ResponseEntityBuilder.creation(ENDPOINT, roomId));
    }

    @GetMapping("/mine")
    public CompletableFuture<ResponseEntity<List<RoomView>>> mine() {
        return roomViewService.mine(SecurityHelper.getUserId()).thenApply(ResponseEntity::ok);
    }

    @GetMapping("/{roomId}")
    public CompletableFuture<ResponseEntity<RoomView>> get(@PathVariable String roomId) {
        return roomViewService.get(roomId, SecurityHelper.getUserId()).thenApply(ResponseEntity::ok);
    }

    @PostMapping("/{roomId}/cancel")
    public CompletableFuture<ResponseEntity<Void>> cancel(@PathVariable String roomId) {
        return roomViewService
                .cancel(roomId, SecurityHelper.getUserId())
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    /** L'invité accepte le défi : la salle s'ouvre aux deux joueurs. */
    @PostMapping("/{roomId}/accept")
    public CompletableFuture<ResponseEntity<Void>> accept(@PathVariable String roomId) {
        return roomViewService
                .accept(roomId, SecurityHelper.getUserId())
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    @PostMapping("/{roomId}/decline")
    public CompletableFuture<ResponseEntity<Void>> decline(@PathVariable String roomId) {
        return roomViewService
                .decline(roomId, SecurityHelper.getUserId())
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    /** Relance : invitation renvoyée (10 min) ou adversaire absent appelé (« wizz », 10 s), gardée par l'agrégat. */
    @PostMapping("/{roomId}/remind")
    public CompletableFuture<ResponseEntity<Void>> remind(@PathVariable String roomId) {
        return roomViewService
                .remind(roomId, SecurityHelper.getUserId())
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    @GetMapping("/{roomId}/events")
    public CompletableFuture<ResponseEntity<List<EventEnvelopeResponse>>> events(@PathVariable String roomId) {
        return roomViewService
                .events(roomId)
                .thenApply(envelopes -> envelopes.stream()
                        .map(RoomController::toResponse)
                        .flatMap(Optional::stream)
                        .toList())
                .thenApply(ResponseEntity::ok);
    }

    private static Optional<EventEnvelopeResponse> toResponse(EventEnvelope envelope) {
        if (!(envelope.payload() instanceof RoomEvent event)) {
            return Optional.empty();
        }
        return RoomEventResponseMapper.toResponse(event)
                .map(response -> EventEnvelopeResponse.of(
                        envelope.aggregateId(),
                        envelope.sequenceNumber(),
                        envelope.timestamp(),
                        response.type().name(),
                        response));
    }
}
