package com.joutio.bff.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryServiceTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final TelemetryService service = new TelemetryService(registry);

    @Test
    void records_allowed_product_event_with_bounded_tags() {
        TelemetryService.RecordResult result = service.recordEvent(
                "user-1",
                "duel_start_requested",
                Map.of("mode", "public", "first", "true"));

        assertThat(result).isEqualTo(TelemetryService.RecordResult.ACCEPTED);
        assertThat(registry.get("joutio.product.events")
                .tags(
                        "event", "duel_start_requested",
                        "mode", "public",
                        "first", "true",
                        "outcome", "",
                        "bot", "",
                        "auto", "")
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void records_onboarding_events() {
        assertThat(service.recordEvent("user-1", "onboarding_started", Map.of()))
                .isEqualTo(TelemetryService.RecordResult.ACCEPTED);
        assertThat(service.recordEvent("user-1", "onboarding_completed", Map.of("topics", "3")))
                .isEqualTo(TelemetryService.RecordResult.ACCEPTED);
        assertThat(service.recordEvent("user-1", "onboarding_skipped", Map.of()))
                .isEqualTo(TelemetryService.RecordResult.ACCEPTED);
    }

    @Test
    void rejects_unknown_event_without_creating_a_counter() {
        TelemetryService.RecordResult result = service.recordEvent(
                "user-1", "event_libre", Map.of());

        assertThat(result).isEqualTo(TelemetryService.RecordResult.INVALID);
        assertThat(registry.find("joutio.product.events").counters()).isEmpty();
    }

    @Test
    void normalizes_client_error_route_and_counts_it() {
        TelemetryService.RecordResult result = service.recordClientError(
                "user-1",
                "JS_ERROR",
                "boom",
                "stack",
                "/players/123e4567-e89b-12d3-a456-426614174000",
                "build-1",
                "window.error");

        assertThat(result).isEqualTo(TelemetryService.RecordResult.ACCEPTED);
        assertThat(registry.get("joutio.client.errors")
                .tags("type", "JS_ERROR", "route", "/players/{id}")
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void rate_limits_events_per_player() {
        for (int i = 0; i < 120; i++) {
            assertThat(service.recordEvent("user-1", "play_mode_opened", Map.of()))
                    .isEqualTo(TelemetryService.RecordResult.ACCEPTED);
        }

        assertThat(service.recordEvent("user-1", "play_mode_opened", Map.of()))
                .isEqualTo(TelemetryService.RecordResult.RATE_LIMITED);
        assertThat(service.recordEvent("user-2", "play_mode_opened", Map.of()))
                .isEqualTo(TelemetryService.RecordResult.ACCEPTED);
    }
}
