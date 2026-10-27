package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.RoomViewService;
import com.joutio.bff.infrastructure.in.api.request.CreateBotRoomRequest;
import com.joutio.bff.infrastructure.in.api.request.CreateRoomRequest;
import com.joutio.bff.infrastructure.in.api.response.EventEnvelopeResponse;
import com.joutio.bff.infrastructure.in.api.response.RoomView;
import com.joutio.bff.infrastructure.out.messaging.mapper.RoomEventNotificationMapper;
import com.joutio.matchmaking.domain.event.RoomEvent;
import com.joutio.matchmaking.domain.query.RoomQuery;
import com.joutio.microservice.core.domain.model.notification.EventEnvelope;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.microservice.core.infrastructure.in.api.ResponseEntityBuilder;
import com.joutio.microservice.core.infrastructure.in.api.response.IdResponse;
import com.joutio.microservice.security.SecurityHelper;
import jakarta.validation.Valid;
import org.axonframework.queryhandling.QueryGateway;
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
 * {@code /api/rooms} — salles : création, consultation, apparition et annulation.
 * Le lien de partage est composé côté client : {@code /join/{roomId}}.
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private static final String ENDPOINT = "/api/rooms";

    private final RoomViewService roomViewService;
    private final QueryGateway queryGateway;

    public RoomController(RoomViewService roomViewService, QueryGateway queryGateway) {
        this.roomViewService = roomViewService;
        this.queryGateway = queryGateway;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<IdResponse>> create(@Valid @RequestBody CreateRoomRequest request) {
        return roomViewService
                .create(SecurityHelper.getUserId(), request)
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

    /** Apparition dans la salle (présence temps réel, idempotent) — commande unique d'entrée. */
    @PostMapping("/{roomId}/join")
    public CompletableFuture<ResponseEntity<Void>> join(@PathVariable String roomId) {
        return roomViewService
                .join(roomId, SecurityHelper.getUserId())
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    @PostMapping("/{roomId}/leave")
    public CompletableFuture<ResponseEntity<Void>> leave(@PathVariable String roomId) {
        return roomViewService
                .leave(roomId, SecurityHelper.getUserId())
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    @PostMapping("/{roomId}/cancel")
    public CompletableFuture<ResponseEntity<Void>> cancel(@PathVariable String roomId) {
        return roomViewService
                .cancel(roomId, SecurityHelper.getUserId())
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    /** Relance (« wizz ») de l'adversaire absent — cadence 10 s par joueur, gardée par l'agrégat. */
    @PostMapping("/{roomId}/wizz")
    public CompletableFuture<ResponseEntity<Void>> wizz(@PathVariable String roomId) {
        return roomViewService
                .wizz(roomId, SecurityHelper.getUserId())
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    @GetMapping("/{roomId}/notifications")
    public CompletableFuture<ResponseEntity<List<EventEnvelopeResponse>>> notifications(@PathVariable String roomId) {
        return queryGateway
                .query(new RoomQuery.GetRoomEventsQuery(roomId),
                        QueryResponseTypes.multipleInstancesOf(EventEnvelope.class))
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
        return RoomEventNotificationMapper.toNotification(event)
                .map(notification -> EventEnvelopeResponse.of(
                        envelope.aggregateId(),
                        envelope.sequenceNumber(),
                        envelope.timestamp(),
                        notification.type().name(),
                        notification));
    }
}
