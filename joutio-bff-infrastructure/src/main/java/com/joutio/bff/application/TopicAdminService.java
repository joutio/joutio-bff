package com.joutio.bff.application;

import com.joutio.theme.domain.command.TopicCommand;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Administration des sujets (2026-11-15) : badge « Officiel » et fermeture sur notification de contenu illicite.
 * L'accès est réservé à {@code ROLE_ADMIN} par le contrôleur ; l'agrégat ne vérifie que ses invariants.
 */
@Service
public class TopicAdminService {

    private final CommandGateway commandGateway;

    public TopicAdminService(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    public CompletableFuture<Void> setOfficial(String topicId, boolean official, String adminId) {
        return commandGateway.send(new TopicCommand.SetTopicOfficialCommand(topicId, official, adminId)).thenApply(_ -> null);
    }

    /** Ferme le sujet : brouillon, publication bloquée ; l'auteur reçoit le motif dans sa boîte de réception. */
    public CompletableFuture<Void> close(String topicId, String reason, String adminId) {
        return commandGateway.send(new TopicCommand.CloseTopicCommand(topicId, reason.trim(), adminId)).thenApply(_ -> null);
    }

    public CompletableFuture<Void> reopen(String topicId, String adminId) {
        return commandGateway.send(new TopicCommand.ReopenTopicCommand(topicId, adminId)).thenApply(_ -> null);
    }
}
