package com.joutio.bff.infrastructure.in.websocket;

import com.joutio.bff.application.live.LiveViews;
import com.joutio.bff.application.live.TestLiveViews.MatchStreamLiveView;
import com.joutio.bff.application.live.TestLiveViews.ScoreLiveView;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LiveViewChannelConfigurationTest {

    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final LiveViewChannelConfiguration.LiveViewChannelInterceptor interceptor;

    @SuppressWarnings("unchecked")
    LiveViewChannelConfigurationTest() {
        ObjectProvider<SimpMessagingTemplate> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(messaging);
        ObjectProvider<LiveViews> views = mock(ObjectProvider.class);
        when(views.getObject()).thenReturn(new LiveViews(List.of(new ScoreLiveView(), new MatchStreamLiveView()),
                mock(QueryGateway.class)));
        interceptor = new LiveViewChannelConfiguration.LiveViewChannelInterceptor(views, provider);
    }

    @Test
    void lets_through_allowed_subscriptions_and_other_destinations() {
        var own = frame(StompCommand.SUBSCRIBE, "/topic/live/score/u1", "u1");
        var stream = frame(StompCommand.SUBSCRIBE, "/topic/matches/m1", "u1");
        var other = frame(StompCommand.SUBSCRIBE, "/topic/games/g1", "u1");
        var unsubscribe = frame(StompCommand.UNSUBSCRIBE, "/topic/live/score/u2", "u1");

        assertThat(interceptor.preSend(own, null)).isSameAs(own);
        assertThat(interceptor.preSend(stream, null)).isSameAs(stream);
        assertThat(interceptor.preSend(other, null)).isSameAs(other);
        assertThat(interceptor.preSend(unsubscribe, null)).isSameAs(unsubscribe);
        verify(messaging, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void drops_a_forbidden_subscription_and_tells_the_player() {
        assertThat(interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/live/score/u2", "u1"), null)).isNull();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSendToUser(eq("u1"), eq("/queue/live"), payload.capture());
        Map<String, Object> body = (Map<String, Object>) payload.getValue();
        assertThat(body).containsEntry("destination", "/topic/live/score/u2");
        assertThat((Map<String, Object>) body.get("problem")).containsEntry("type", "urn:joutio:bff:live:forbidden");
    }

    @Test
    @SuppressWarnings("unchecked")
    void drops_an_unknown_live_view() {
        assertThat(interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/live/nope/u1", "u1"), null)).isNull();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSendToUser(eq("u1"), eq("/queue/live"), payload.capture());
        assertThat((Map<String, Object>) ((Map<String, Object>) payload.getValue()).get("problem"))
                .containsEntry("type", "urn:joutio:bff:live:unknownView");
    }

    @Test
    void drops_anonymous_subscriptions_and_client_publication_silently() {
        assertThat(interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/live/score/u1", null), null)).isNull();
        assertThat(interceptor.preSend(frame(StompCommand.SEND, "/topic/live/score/u1", "u1"), null)).isNull();
        assertThat(interceptor.preSend(frame(StompCommand.SEND, "/topic/matches/m1", "u1"), null)).isNull();
        verify(messaging, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void tracks_subscriptions_once_the_broker_handled_them() {
        LiveViewSubscriptions subscriptions = mock(LiveViewSubscriptions.class);
        var tracker = tracker(subscriptions);

        tracker.afterMessageHandled(simp(SimpMessageType.SUBSCRIBE, "s1", "sub-1", "/topic/live/score/u1"), null,
                broker(), null);
        tracker.afterMessageHandled(simp(SimpMessageType.UNSUBSCRIBE, "s1", "sub-1", null), null, broker(), null);
        tracker.afterMessageHandled(simp(SimpMessageType.DISCONNECT, "s1", null, null), null, broker(), null);

        verify(subscriptions).subscribed("s1", "sub-1", "/topic/live/score/u1");
        verify(subscriptions).unsubscribed("s1", "sub-1");
        verify(subscriptions).disconnected("s1");
    }

    @Test
    void ignores_other_handlers_and_failures() {
        LiveViewSubscriptions subscriptions = mock(LiveViewSubscriptions.class);
        var tracker = tracker(subscriptions);
        var subscribe = simp(SimpMessageType.SUBSCRIBE, "s1", "sub-1", "/topic/live/score/u1");

        tracker.afterMessageHandled(subscribe, null, mock(MessageHandler.class), null);
        tracker.afterMessageHandled(subscribe, null, broker(), new IllegalStateException("boom"));

        verifyNoInteractions(subscriptions);
    }

    @SuppressWarnings("unchecked")
    private static LiveViewChannelConfiguration.LiveViewBrokerTracker tracker(LiveViewSubscriptions subscriptions) {
        ObjectProvider<LiveViewSubscriptions> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(subscriptions);
        return new LiveViewChannelConfiguration.LiveViewBrokerTracker(provider);
    }

    private static MessageHandler broker() {
        return new SimpleBrokerMessageHandler(mock(SubscribableChannel.class), mock(MessageChannel.class),
                mock(SubscribableChannel.class), List.of("/topic"));
    }

    private static Message<byte[]> simp(SimpMessageType type, String session, String subscription,
                                        String destination) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(type);
        headers.setSessionId(session);
        if (subscription != null) headers.setSubscriptionId(subscription);
        if (destination != null) headers.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    private static Message<byte[]> frame(StompCommand command, String destination, String userId) {
        var accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        if (userId != null) accessor.setUser(() -> userId);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
