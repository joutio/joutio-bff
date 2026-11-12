package com.joutio.bff.application;

import com.joutio.identity.domain.model.PremiumSource;
import com.joutio.identity.domain.model.UserAccountStatus;
import com.joutio.identity.domain.model.UserEntitlements;
import com.joutio.identity.domain.model.UserPremium;
import com.joutio.identity.domain.query.UserQuery;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import com.joutio.microservice.security.SecurityHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Droit Premium de la requête courante.
 *
 * <p>L'admin ({@code ROLE_ADMIN} du JWT) est Premium par défaut. Pour les autres, le droit est
 * porté par {@code UserAggregate} (identity) et lu via {@link IdentityLookup} : déjà interrogé à
 * chaque requête par {@code CurrentUserExistsFilter}, son cache est purgé sur les événements
 * {@code UserPremiumGranted/RevokedEvent}. Un achat ou un retrait est donc visible sans attendre
 * le renouvellement du jeton (premium/specs/entitlements.md § 3).</p>
 *
 * <p>Fail-closed : identity injoignable ⇒ pas de Premium (aucune ressource Premium n'est
 * accordée sur une lecture incertaine).</p>
 */
@Component
public class PremiumAccessPolicy {

    private static final Logger logger = LoggerFactory.getLogger(PremiumAccessPolicy.class);

    static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final long LOOKUP_TIMEOUT_MS = 2_000;

    /** Droit Premium résolu : {@code premiumUntil}/{@code source} nuls si aucun droit accordé. */
    public record PremiumStatus(boolean premium, Instant premiumUntil, PremiumSource source) {
        static final PremiumStatus NONE = new PremiumStatus(false, null, null);
    }

    private final IdentityLookup identityLookup;
    private final QueryGateway queryGateway;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public PremiumAccessPolicy(IdentityLookup identityLookup, QueryGateway queryGateway) {
        this(identityLookup, queryGateway, Clock.systemUTC());
    }

    PremiumAccessPolicy(IdentityLookup identityLookup, QueryGateway queryGateway, Clock clock) {
        this.identityLookup = identityLookup;
        this.queryGateway = queryGateway;
        this.clock = clock;
    }

    /**
     * Droit Premium d'un joueur quelconque (admin compris), relu dans identity sans cache :
     * utilisable hors du thread HTTP et pour un tiers (créateur d'un sujet, invité d'une équipe).
     */
    public CompletableFuture<Boolean> premium(String userId) {
        return queryGateway.query(
                        new UserQuery.GetUserEntitlementsQuery(userId),
                        QueryResponseTypes.instanceOf(UserEntitlements.class))
                .thenApply(UserEntitlements::premium);
    }

    /** Doit être appelé sur le thread HTTP (lit le {@code SecurityContext}). */
    public boolean isPremium() {
        return status().premium();
    }

    /** Doit être appelé sur le thread HTTP (lit le {@code SecurityContext}). */
    public PremiumStatus status() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return PremiumStatus.NONE;
        }
        boolean admin = isAdmin(authentication);
        PremiumStatus granted = SecurityHelper.findUserId()
                .map(this::lookup)
                .orElse(PremiumStatus.NONE);
        return admin ? new PremiumStatus(true, granted.premiumUntil(), granted.source()) : granted;
    }

    static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_ADMIN::equals);
    }

    PremiumStatus lookup(String userId) {
        try {
            UserAccountStatus status = identityLookup.status(userId)
                    .get(LOOKUP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return toStatus(status, clock.instant());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Premium lookup interrupted for {} (fail-closed)", userId);
        } catch (ExecutionException | TimeoutException e) {
            logger.warn("Premium lookup failed for {} (fail-closed): {}", userId, e.toString());
        }
        return PremiumStatus.NONE;
    }

    static PremiumStatus toStatus(UserAccountStatus status, Instant now) {
        if (status == null) {
            return PremiumStatus.NONE;
        }
        UserPremium premium = status.premium();
        return new PremiumStatus(
                status.premiumActive(now),
                premium == null ? null : premium.premiumUntil(),
                premium == null ? null : premium.source());
    }
}
