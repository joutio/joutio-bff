package com.joutio.bff.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Télémétrie produit et erreurs navigateur, **100 % interne** : les événements deviennent des
 * compteurs Prometheus et des logs structurés (Alloy → Loki), jamais un appel à un SaaS.
 *
 * <p>Garde-fous : allowlist d'événements, valeurs de tags bornées, aucune propriété libre dans
 * les labels, rate-limit mémoire par joueur et par type. Les erreurs clients sont tronquées et
 * ne doivent contenir aucune donnée personnelle (le client n'envoie que le diagnostic).</p>
 */
@Service
public class TelemetryService {

    public enum RecordResult {
        ACCEPTED,
        INVALID,
        RATE_LIMITED
    }

    private static final Logger logger = LoggerFactory.getLogger(TelemetryService.class);

    private static final Set<String> EVENTS = Set.of(
            "onboarding_started",
            "onboarding_completed",
            "onboarding_skipped",
            "play_mode_opened",
            "duel_start_requested",
            "matchmaking_expired",
            "result_viewed",
            "paywall_viewed"
    );
    private static final Set<String> MODES = Set.of(
            "public", "bot", "challenge", "private"
    );
    private static final Set<String> OUTCOMES = Set.of(
            "win", "loss", "draw"
    );
    private static final Set<String> CLIENT_ERROR_TYPES = Set.of(
            "JS_ERROR", "UNHANDLED_REJECTION", "REACT_RENDER", "API_ERROR"
    );
    private static final int EVENTS_PER_MINUTE = 120;
    private static final int ERRORS_PER_MINUTE = 20;
    private static final int MAX_STACK_LENGTH = 4000;
    private static final int MAX_ROUTE_LENGTH = 120;

    private final MeterRegistry meterRegistry;
    private final Cache<String, AtomicInteger> windows = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(10_000)
            .build();

    public TelemetryService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public RecordResult recordEvent(String playerId, String name, Map<String, String> properties) {
        if (!EVENTS.contains(name)) {
            return RecordResult.INVALID;
        }
        if (!allow("event:" + playerId, EVENTS_PER_MINUTE)) {
            return RecordResult.RATE_LIMITED;
        }

        Map<String, String> safe = properties == null ? Map.of() : properties;
        Counter.builder("joutio.product.events")
                .description("Événements produit émis par le client web")
                .tags(
                        "event", name,
                        "mode", safeMode(safe.get("mode")),
                        "first", safeBoolean(safe.get("first")),
                        "outcome", safeOutcome(safe.get("outcome")),
                        "bot", safeBoolean(safe.get("bot")),
                        "auto", safeBoolean(safe.get("auto")))
                .register(meterRegistry)
                .increment();
        return RecordResult.ACCEPTED;
    }

    public RecordResult recordClientError(String playerId,
                                          String type,
                                          String message,
                                          String stack,
                                          String route,
                                          String buildId,
                                          String source) {
        if (!allow("error:" + playerId, ERRORS_PER_MINUTE)) {
            return RecordResult.RATE_LIMITED;
        }

        String safeType = CLIENT_ERROR_TYPES.contains(type) ? type : "UNKNOWN";
        String safeRoute = normalizeRoute(route);
        String safeMessage = truncate(message, 500);
        String safeStack = truncate(stack, MAX_STACK_LENGTH);

        Counter.builder("joutio.client.errors")
                .description("Erreurs remontées par le client web")
                .tags("type", safeType, "route", safeRoute)
                .register(meterRegistry)
                .increment();

        logger.warn("client_error type={} route={} build={} source={} message={} stack={}",
                safeType,
                safeRoute,
                truncate(buildId, 64),
                truncate(source, 120),
                safeMessage,
                safeStack);
        return RecordResult.ACCEPTED;
    }

    private boolean allow(String bucket, int limit) {
        AtomicInteger counter = windows.get(bucket, _ -> new AtomicInteger());
        return counter.incrementAndGet() <= limit;
    }

    private static String safeMode(String value) {
        if (value == null) return "";
        String mode = value.toLowerCase(Locale.ROOT);
        return MODES.contains(mode) ? mode : "unknown";
    }

    private static String safeOutcome(String value) {
        if (value == null) return "";
        String outcome = value.toLowerCase(Locale.ROOT);
        return OUTCOMES.contains(outcome) ? outcome : "unknown";
    }

    private static String safeBoolean(String value) {
        if (value == null) return "";
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)
                ? value.toLowerCase(Locale.ROOT)
                : "";
    }

    /** Normalise les identifiants variables pour éviter l'explosion de cardinalité des routes. */
    static String normalizeRoute(String route) {
        if (route == null || route.isBlank()) return "";
        String normalized = route
                .replaceAll("(?i)/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "/{id}")
                .replaceAll("/\\d+", "/{id}");
        return truncate(normalized, MAX_ROUTE_LENGTH);
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        String singleLine = value.replace('\n', ' ').replace('\r', ' ');
        return singleLine.length() <= max ? singleLine : singleLine.substring(0, max);
    }
}
