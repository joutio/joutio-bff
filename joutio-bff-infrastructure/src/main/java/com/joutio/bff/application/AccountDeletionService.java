package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.identity.domain.command.UserCommand;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Suppression de compte : demande du code OTP à identity, vérification, puis commande de
 * suppression. L'identité et les autres services terminent le nettoyage via l'événement
 * {@code UserAccountDeletedEvent}.
 */
@Service
public class AccountDeletionService {

    private static final Logger logger = LoggerFactory.getLogger(AccountDeletionService.class);
    private static final long DELETION_WAIT_MS = 5_000;

    private final MeService meService;
    private final IdentityAuthClient identityAuthClient;
    private final CommandGateway commandGateway;
    private final IdentityLookup identityLookup;

    public AccountDeletionService(MeService meService,
                                  IdentityAuthClient identityAuthClient,
                                  CommandGateway commandGateway,
                                  IdentityLookup identityLookup) {
        this.meService = meService;
        this.identityAuthClient = identityAuthClient;
        this.commandGateway = commandGateway;
        this.identityLookup = identityLookup;
    }

    public void requestCode(String userId) {
        String email = meService.me(userId).join().email();
        if (email != null && !email.isBlank()) {
            identityAuthClient.requestAccountDeletionCode(email);
        }
    }

    public void delete(String userId, String code) {
        String email = meService.me(userId).join().email();
        if (email == null || email.isBlank()) {
            throw new BffProblems.InvalidAccountDeletionCodeProblem();
        }
        try {
            identityAuthClient.verifyAccountDeletionCode(email, code);
        } catch (IdentityAuthClient.AccountDeletionCodeRejected rejected) {
            throw new BffProblems.InvalidAccountDeletionCodeProblem();
        }

        commandGateway.send(new UserCommand.DeleteAccountCommand(
                        userId,
                        UUID.randomUUID().toString(),
                        Instant.now()))
                .join();
        // La réinscription avec le même email ne doit pas entrer en collision avec l'ancienne
        // projection encore ACTIVE : attendre (brièvement) que le statut DELETED soit projeté.
        awaitDeletionProjected(userId);
    }

    private void awaitDeletionProjected(String userId) {
        long deadline = System.nanoTime() + DELETION_WAIT_MS * 1_000_000L;
        while (System.nanoTime() < deadline) {
            identityLookup.invalidate(userId);
            try {
                var status = identityLookup.status(userId).join();
                if (status.deleted()) {
                    return;
                }
            } catch (RuntimeException exception) {
                // Projection absente ou temporairement indisponible : réessayer.
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        logger.warn("Account deletion projection not observed within {} ms for user {}",
                DELETION_WAIT_MS, userId);
    }

}
