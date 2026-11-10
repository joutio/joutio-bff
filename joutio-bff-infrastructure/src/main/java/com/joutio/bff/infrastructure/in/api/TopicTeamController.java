package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.infrastructure.in.api.response.TopicTodoView;

import com.joutio.bff.application.TopicTeamService;
import com.joutio.bff.infrastructure.in.api.request.PageParams;
import com.joutio.bff.infrastructure.in.api.response.*;
import com.joutio.microservice.security.SecurityHelper;
import com.joutio.theme.domain.model.TopicTeamRole;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/topics")
public class TopicTeamController {
    private final TopicTeamService service;

    public TopicTeamController(TopicTeamService service) {
        this.service = service;
    }

    public record MemberRequest(@NotNull TopicTeamRole role) {}

    @GetMapping("/contributed")
    public CompletableFuture<PageResponse<TopicCardView>> contributed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        PageParams.validate(page, size, 100);
        return service.contributed(SecurityHelper.getUserId(), page, size);
    }

    /** Mes sujets › À traiter : actions en attente sur les sujets que le joueur gère. */
    @GetMapping("/contributed/todo")
    public CompletableFuture<List<TopicTodoView>> todo() {
        return service.todo(SecurityHelper.getUserId());
    }

    @GetMapping("/{id}/workshop")
    public CompletableFuture<TopicWorkshopView> workshop(@PathVariable String id) {
        return service.workshop(id, SecurityHelper.getUserId());
    }

    @GetMapping("/{id}/team")
    public CompletableFuture<List<TopicTeamMemberView>> team(@PathVariable String id) {
        return service.team(id, SecurityHelper.getUserId());
    }

    @PutMapping("/{id}/team/{userId}")
    public CompletableFuture<ResponseEntity<TopicTeamMemberView>> set(
            @PathVariable String id,
            @PathVariable String userId,
            @Valid @RequestBody MemberRequest request) {
        return service.set(id, SecurityHelper.getUserId(), userId, request.role())
                .thenApply(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}/team/{userId}")
    public CompletableFuture<ResponseEntity<Void>> remove(
            @PathVariable String id, @PathVariable String userId) {
        return service.remove(id, SecurityHelper.getUserId(), userId)
                .thenApply(_ -> ResponseEntity.noContent().build());
    }
}
