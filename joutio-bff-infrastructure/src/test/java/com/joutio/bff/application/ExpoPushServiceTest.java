package com.joutio.bff.application;

import com.joutio.notification.domain.event.NotificationEvent.NotificationCreatedEvent;
import com.joutio.notification.domain.model.NotificationType;
import com.joutio.notification.domain.model.NotificationCategory;
import com.joutio.bff.infrastructure.in.api.response.NotificationPreferenceView;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ExpoPushServiceTest {
    private final ExpoPushStore store = mock(ExpoPushStore.class);
    private final ExpoPushGateway gateway = mock(ExpoPushGateway.class);
    private final NotificationViewService notifications = mock(NotificationViewService.class);
    private final ExpoPushService service = new ExpoPushService(store, gateway, notifications, true);
    private NotificationCreatedEvent event(NotificationType type, Instant expires) {
        return new NotificationCreatedEvent("n", "u", type, "a", "s", null, null, expires, Instant.now());
    }
    private ExpoPushStore.Delivery delivery(String ticket, int attempts, Instant expires) {
        return new ExpoPushStore.Delivery("d", "i", "token", new ExpoPushStore.Notice("n", "u", "WIZZ", "s", null, expires), ticket, attempts);
    }
    private void queued(ExpoPushStore.Delivery d) {
        when(store.claim(any())).thenReturn(Optional.of(d), Optional.empty());
        when(store.eligible(d)).thenReturn(true);
        when(notifications.preferences("u")).thenReturn(CompletableFuture.completedFuture(List.of()));
    }
    @Test void only_live_challenges_and_wizz_are_enqueued() {
        service.enqueue(event(NotificationType.FOLLOW, Instant.now().plusSeconds(60)));
        service.enqueue(event(NotificationType.WIZZ, Instant.now().minusSeconds(1)));
        verifyNoInteractions(store);
        var e = event(NotificationType.CHALLENGE_RECEIVED, Instant.now().plusSeconds(60));
        service.enqueue(e);
        verify(store).enqueue(e);
    }
    @Test void disabled_channel_neither_enqueues_nor_drains() {
        var disabled = new ExpoPushService(store,gateway,notifications,false);
        disabled.enqueue(event(NotificationType.WIZZ, Instant.now().plusSeconds(60)));
        disabled.drain();
        verifyNoInteractions(store,gateway);
    }
    @Test void accepted_ticket_is_persisted_for_receipt_check() {
        var d=delivery(null,1,Instant.now().plusSeconds(60)); queued(d);
        when(gateway.send(d)).thenReturn(new ExpoPushGateway.Result(ExpoPushGateway.Status.ACCEPTED,"ticket"));
        service.drain();
        verify(store).ticket(eq("d"),eq("ticket"),any());
    }
    @Test void rotated_or_reassigned_installation_is_never_sent() {
        var d=delivery(null,1,Instant.now().plusSeconds(60)); queued(d);
        when(store.eligible(d)).thenReturn(false);
        service.drain(); verifyNoInteractions(gateway); verify(store).finish("d");
    }
    @Test void expired_invitation_is_never_sent() {
        var d=delivery(null,1,Instant.now().minusSeconds(1)); queued(d);
        service.drain(); verifyNoInteractions(gateway); verify(store).finish("d");
    }
    @Test void room_preference_is_rechecked_before_delivery() {
        var d=delivery(null,1,Instant.now().plusSeconds(60)); queued(d);
        when(notifications.preferences("u")).thenReturn(CompletableFuture.completedFuture(List.of(new NotificationPreferenceView(NotificationCategory.ROOM,false))));
        service.drain(); verifyNoInteractions(gateway); verify(store).finish("d");
    }
    @Test void invalid_token_in_receipt_is_removed_even_after_expiration() {
        var d=delivery("ticket",1,Instant.now().minusSeconds(60)); queued(d);
        when(gateway.receipt("ticket")).thenReturn(new ExpoPushGateway.Result(ExpoPushGateway.Status.INVALID_TOKEN,null));
        service.drain(); verify(store).disableToken("token"); verify(store).finish("d");
    }
    @Test void recovered_leases_cannot_send_after_the_attempt_limit() {
        var d=delivery(null,6,Instant.now().plusSeconds(60)); queued(d);
        service.drain(); verifyNoInteractions(gateway); verify(store).finish("d");
    }
    @Test void transient_failure_retries_then_stops_at_limit() {
        var d=delivery(null,1,Instant.now().plusSeconds(60)); queued(d);
        when(gateway.send(d)).thenReturn(new ExpoPushGateway.Result(ExpoPushGateway.Status.RETRY,null));
        service.drain(); verify(store).retry(eq("d"),any());
        var last=delivery(null,5,Instant.now().plusSeconds(60)); queued(last);
        when(gateway.send(last)).thenReturn(new ExpoPushGateway.Result(ExpoPushGateway.Status.RETRY,null));
        service.drain(); verify(store).finish("d");
    }
}
