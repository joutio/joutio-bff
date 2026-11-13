package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.response.PlayerCardView;
import com.joutio.bff.infrastructure.in.api.response.ReferralView;
import com.joutio.identity.domain.command.UserCommand;
import com.joutio.identity.domain.exception.ReferralProblems;
import com.joutio.identity.domain.model.ReferralRules;
import com.joutio.identity.domain.model.ReferralSummary;
import com.joutio.identity.domain.model.AccountStatus;
import com.joutio.identity.domain.query.UserQuery;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.profile.domain.model.PlayerProgress;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

/**
 * Données de compte du joueur courant portées par {@code joutio-identity} : date de naissance privée et parrainage
 * ({@code design/specs/screens/editer-profil.md} § 5, {@code partage.md} § 6). Les commandes sont préparées ici
 * (parrain résolu depuis le code, compte actif) : aucun I/O dans les handlers d'identity.
 */
@Service
public class AccountService {

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;
    private final IdentityLookup identityLookup;
    private final ProfileLookup profileLookup;
    private final ProgressionLookup progressionLookup;

    public AccountService(CommandGateway commandGateway,
                          QueryGateway queryGateway,
                          IdentityLookup identityLookup,
                          ProfileLookup profileLookup,
                          ProgressionLookup progressionLookup) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
        this.identityLookup = identityLookup;
        this.profileLookup = profileLookup;
        this.progressionLookup = progressionLookup;
    }

    public CompletableFuture<Void> setBirthDate(String userId, LocalDate birthDate) {
        return commandGateway.send(new UserCommand.SetBirthDateCommand(userId, birthDate, userId, Instant.now()))
                .thenRun(() -> identityLookup.invalidate(userId));
    }

    public CompletableFuture<ReferralView> referral(String userId) {
        CompletableFuture<ReferralSummary> summaryFuture = queryGateway.query(
                new UserQuery.GetReferralSummaryQuery(userId), QueryResponseTypes.instanceOf(ReferralSummary.class));
        CompletableFuture<PlayerProgress> progressFuture = progressionLookup.get(userId)
                .exceptionally(_ -> PlayerProgress.empty(userId));
        return summaryFuture.thenCombine(progressFuture, (summary, progress) -> new Object[]{summary, progress})
                .thenCompose(parts -> {
                    ReferralSummary summary = (ReferralSummary) parts[0];
                    PlayerProgress progress = (PlayerProgress) parts[1];
                    return sponsorCard(summary.referredBy()).thenApply(sponsor -> new ReferralView(
                            summary.code(), summary.invited(), progress.referralsQualified(), sponsor));
                });
    }

    /**
     * Applique un code de parrainage. Code inconnu, compte du parrain supprimé ou suspendu : même refus
     * ({@code urn:joutio:identity:invalidReferralCode}), sans révéler l'état du compte.
     */
    public CompletableFuture<Void> applyReferral(String userId, String rawCode) {
        String code = ReferralRules.normalizeCode(rawCode);
        if (code == null) {
            return CompletableFuture.failedFuture(new ReferralProblems.InvalidReferralCodeProblem(userId));
        }
        return queryGateway.query(new UserQuery.FindUserIdByReferralCodeQuery(code), QueryResponseTypes.instanceOf(String.class))
                .thenCompose(referrerId -> {
                    if (referrerId == null || referrerId.isBlank()) {
                        throw new ReferralProblems.InvalidReferralCodeProblem(userId);
                    }
                    if (referrerId.equals(userId)) {
                        throw new ReferralProblems.SelfReferralProblem(userId);
                    }
                    return identityLookup.status(referrerId)
                            .exceptionally(_ -> null)
                            .thenCompose(status -> {
                                if (status == null || status.status() == AccountStatus.DELETED || status.suspended(Instant.now())) {
                                    throw new ReferralProblems.InvalidReferralCodeProblem(userId);
                                }
                                return commandGateway.<Object>send(
                                        new UserCommand.ApplyReferralCommand(userId, referrerId, Instant.now()));
                            });
                })
                .thenRun(() -> identityLookup.invalidate(userId));
    }

    private CompletableFuture<PlayerCardView> sponsorCard(String sponsorId) {
        if (sponsorId == null) {
            return CompletableFuture.completedFuture(null);
        }
        return profileLookup.get(sponsorId)
                .thenCombine(progressionLookup.get(sponsorId).exceptionally(_ -> PlayerProgress.empty(sponsorId)),
                        (profile, progress) -> {
                            var progression = ProgressionViews.toView(progress);
                            return new PlayerCardView(profile.userId(), profile.pseudonym(), profile.avatarOptions(),
                                    progression.rating(), progression.provisional(), true, null, false);
                        })
                .exceptionally(_ -> null);
    }
}
