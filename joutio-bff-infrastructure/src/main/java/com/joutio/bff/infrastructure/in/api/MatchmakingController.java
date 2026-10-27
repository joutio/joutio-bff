package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.MatchmakingViewService;
import com.joutio.bff.infrastructure.in.api.request.CreateMatchmakingRequest;
import com.joutio.bff.infrastructure.in.api.response.EventEnvelopeResponse;
import com.joutio.bff.infrastructure.in.api.response.MatchmakingTicketView;
import com.joutio.bff.infrastructure.out.messaging.mapper.MatchmakingEventNotificationMapper;
import com.joutio.matchmaking.domain.event.MatchmakingEvent;
import com.joutio.matchmaking.domain.query.MatchmakingQuery;
import com.joutio.microservice.core.domain.model.notification.EventEnvelope;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
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

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /api/matchmaking/tickets} — appariement public (« Défier le monde »).
 */
@RestController
@RequestMapping("/api/matchmaking/tickets")
public class MatchmakingController {

    private static final String ENDPOINT = "/api/matchmaking/tickets";

    private final MatchmakingViewService matchmakingViewService;
    private final QueryGateway queryGateway;

    public MatchmakingController(MatchmakingViewService matchmakingViewService, QueryGateway queryGateway) {
        this.matchmakingViewService = matchmakingViewService;
        this.queryGateway = queryGateway;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<MatchmakingTicketView>> enqueue(
            @Valid @RequestBody CreateMatchmakingRequest request) {
        return matchmakingViewService
                .enqueue(SecurityHelper.getUserId(), request.topicId())
                .thenApply(ticket -> ResponseEntity
                        .created(URI.create(ENDPOINT + "/" + ticket.ticketId()))
                        .body(ticket));
    }

    @GetMapping("/{ticketId}")
    public CompletableFuture<ResponseEntity<MatchmakingTicketView>> get(@PathVariable String ticketId) {
        return matchmakingViewService.get(ticketId).thenApply(ResponseEntity::ok);
    }

    @PostMapping("/{ticketId}/cancel")
    public CompletableFuture<ResponseEntity<Void>> cancel(@PathVariable String ticketId) {
        return matchmakingViewService
                .cancel(SecurityHelper.getUserId(), ticketId)
                .thenApply(_ -> ResponseEntity.ok().build());
    }

    @GetMapping("/{ticketId}/notifications")
    public CompletableFuture<ResponseEntity<List<EventEnvelopeResponse>>> notifications(@PathVariable String ticketId) {
        return queryGateway
                .query(new MatchmakingQuery.GetMatchmakingEventsQuery(ticketId),
                        QueryResponseTypes.multipleInstancesOf(EventEnvelope.class))
                .thenApply(envelopes -> envelopes.stream()
                        .map(MatchmakingController::toResponse)
                        .flatMap(Optional::stream)
                        .toList())
                .thenApply(ResponseEntity::ok);
    }

    private static Optional<EventEnvelopeResponse> toResponse(EventEnvelope envelope) {
        if (!(envelope.payload() instanceof MatchmakingEvent event)) {
            return Optional.empty();
        }
        return MatchmakingEventNotificationMapper.toNotification(event)
                .map(notification -> EventEnvelopeResponse.of(
                        envelope.aggregateId(),
                        envelope.sequenceNumber(),
                        envelope.timestamp(),
                        notification.type().name(),
                        notification));
    }
}
