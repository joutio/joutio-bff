package com.joutio.bff.application;

import com.joutio.identity.domain.command.UserCommand;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

/**
 * Données de compte du joueur courant portées par {@code joutio-identity} : date de naissance privée
 * ({@code design/specs/screens/editer-profil.md} § 5). La commande est préparée ici : aucun I/O dans les handlers d'identity.
 */
@Service
public class AccountService {

    private final CommandGateway commandGateway;
    private final IdentityLookup identityLookup;

    public AccountService(CommandGateway commandGateway, IdentityLookup identityLookup) {
        this.commandGateway = commandGateway;
        this.identityLookup = identityLookup;
    }

    public CompletableFuture<Void> setBirthDate(String userId, LocalDate birthDate) {
        return commandGateway.send(new UserCommand.SetBirthDateCommand(userId, birthDate, userId, Instant.now()))
                .thenRun(() -> identityLookup.invalidate(userId));
    }
}
