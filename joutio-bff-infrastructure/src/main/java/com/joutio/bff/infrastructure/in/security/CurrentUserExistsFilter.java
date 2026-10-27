package com.joutio.bff.infrastructure.in.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joutio.bff.application.IdentityLookup;
import com.joutio.identity.domain.exception.UserProblems;
import com.joutio.identity.domain.model.UserAccountStatus;
import com.joutio.microservice.MicroserviceProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Rejette les JWT encore valides dont l'utilisateur n'existe plus ou est suspendu côté identity.
 *
 * <ul>
 *   <li>compte inexistant ⇒ {@code 401 urn:joutio:authentification:unknownUser} ;</li>
 *   <li>compte suspendu ⇒ {@code 403 urn:joutio:authorization:accountSuspended} avec
 *       {@code suspendedUntil} ;</li>
 *   <li>identity injoignable ⇒ fail-open (la disponibilité prime).</li>
 * </ul>
 *
 * <p>La réponse porte les en-têtes CORS de la configuration du service : le filtre court-circuite
 * <b>avant</b> le {@code CorsFilter} servlet, sans eux le navigateur masque la réponse.</p>
 */
@Component
public class CurrentUserExistsFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(CurrentUserExistsFilter.class);

    private static final long LOOKUP_TIMEOUT_MS = 3_000;
    private static final String USER_ID_CLAIM = "user_id";

    private final IdentityLookup identityLookup;
    private final ObjectMapper objectMapper;
    private final CorsConfiguration corsConfiguration;

    public CurrentUserExistsFilter(IdentityLookup identityLookup,
                                   ObjectMapper objectMapper,
                                   MicroserviceProperties properties) {
        this.identityLookup = identityLookup;
        this.objectMapper = objectMapper;
        this.corsConfiguration = corsConfiguration(properties);
    }

    /** Mêmes origines/credentials que {@code CorsAutoConfiguration} du SDK (source des headers). */
    private static CorsConfiguration corsConfiguration(MicroserviceProperties properties) {
        MicroserviceProperties.Cors cors = properties.cors();
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowCredentials(cors.allowCredentials());
        if (cors.allowCredentials() && cors.allowedOrigins().contains("*")) {
            configuration.addAllowedOriginPattern("*");
        } else {
            cors.allowedOrigins().forEach(configuration::addAllowedOrigin);
        }
        return configuration;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication) || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = jwtAuthentication.getToken().getClaimAsString(USER_ID_CLAIM);
        if (userId == null || userId.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        LookupResult result = lookup(userId);
        if (result.notFound()) {
            logger.warn("Stale session rejected: user {} no longer exists in identity", userId);
            SecurityContextHolder.clearContext();
            writeUnauthorized(request, response);
            return;
        }
        if (result.status() != null && result.status().deleted()) {
            logger.warn("Deleted session rejected: user {}", userId);
            SecurityContextHolder.clearContext();
            writeUnauthorized(request, response);
            return;
        }
        if (result.status() != null && result.status().suspended(Instant.now())) {
            logger.warn("Suspended session rejected: user {}", userId);
            SecurityContextHolder.clearContext();
            writeForbidden(request, response, result.status());
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** {@code null} = indéterminé (identity injoignable ou trop lent) : fail-open. */
    private LookupResult lookup(String userId) {
        try {
            UserAccountStatus status = identityLookup.status(userId)
                    .get(LOOKUP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return new LookupResult(status, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Identity lookup interrupted for {} (fail-open)", userId);
        } catch (ExecutionException e) {
            if (hasCause(e, UserProblems.UserNotFoundProblem.class)) {
                return new LookupResult(null, true);
            }
            logger.warn("Identity lookup failed for {} (fail-open): {}", userId, e.toString());
        } catch (TimeoutException e) {
            logger.warn("Identity lookup timed out for {} (fail-open)", userId);
        }
        return new LookupResult(null, false);
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        applyCorsHeaders(request, response);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\", error_description=\"unknown user\"");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "urn:joutio:authentification:unknownUser");
        body.put("title", "Session invalide");
        body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        body.put("detail", "L'utilisateur associé à ce jeton n'existe plus. Veuillez vous reconnecter.");
        body.put("instance", request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }

    private void writeForbidden(HttpServletRequest request,
                                HttpServletResponse response,
                                UserAccountStatus status) throws IOException {
        applyCorsHeaders(request, response);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "urn:joutio:authorization:accountSuspended");
        body.put("title", "Compte suspendu");
        body.put("status", HttpServletResponse.SC_FORBIDDEN);
        body.put("detail", "Ce compte est suspendu. Consulte l'email reçu pour connaître le motif et l'échéance.");
        body.put("instance", request.getRequestURI());
        if (status.suspendedUntil() != null) {
            body.put("suspendedUntil", status.suspendedUntil().toString());
        }
        objectMapper.writeValue(response.getWriter(), body);
    }

    /** Sans ces en-têtes, le navigateur masque la 401/403 (CORS) et le web ne purge pas sa session. */
    private void applyCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null) {
            return;
        }
        String resolved = corsConfiguration.checkOrigin(origin);
        if (resolved == null) {
            return;
        }
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, resolved);
        response.addHeader(HttpHeaders.VARY, HttpHeaders.ORIGIN);
        if (Boolean.TRUE.equals(corsConfiguration.getAllowCredentials())) {
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }
    }

    private record LookupResult(UserAccountStatus status, boolean notFound) {
    }
}
