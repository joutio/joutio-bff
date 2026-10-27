package com.joutio.bff.application;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushSubscriptionServiceTest {

    private final PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);
    private final PushSubscriptionService service = new PushSubscriptionService(repository);

    @Test
    void register_rebinds_existing_endpoint_and_preserves_created_at() {
        Instant createdAt = Instant.parse("2026-01-01T10:00:00Z");
        PushSubscription existing = new PushSubscription("endpoint-1", "user-1", "old", "old",
                "Chrome", createdAt, createdAt);
        when(repository.find("endpoint-1")).thenReturn(Optional.of(existing));

        service.register("user-2", "endpoint-1", "p256dh", "auth", "Firefox");

        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
        verify(repository).save(captor.capture());
        PushSubscription saved = captor.getValue();
        assertThat(saved.userId()).isEqualTo("user-2");
        assertThat(saved.p256dh()).isEqualTo("p256dh");
        assertThat(saved.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void unregister_delegates_with_owner_guard() {
        service.unregister("user-1", "endpoint-1");

        verify(repository).deleteByEndpointAndUserId("endpoint-1", "user-1");
    }

    @Test
    void subscriptions_of_delegates() {
        when(repository.findByUserId("user-1")).thenReturn(List.of());

        assertThat(service.subscriptionsOf("user-1")).isEmpty();
    }
}
