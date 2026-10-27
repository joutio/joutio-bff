package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.application.FollowLookup;
import com.joutio.bff.application.ProfileLookup;
import com.joutio.bff.infrastructure.in.api.response.FollowPresenceView;
import com.joutio.profile.domain.event.PresenceEvent;
import com.joutio.profile.domain.model.Profile;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FollowPresencePublisherTest {

    private static final String ACTOR_ID = "actor-1";
    private static final Instant AT = Instant.parse("2026-10-05T12:00:00Z");

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final FollowLookup followLookup = mock(FollowLookup.class);
    private final ProfileLookup profileLookup = mock(ProfileLookup.class);
    private final FollowPresencePublisher publisher =
            new FollowPresencePublisher(messagingTemplate, followLookup, profileLookup);

    @Test
    void publishesToEveryFollowerWithActorIdentity() {
        when(followLookup.followerIds(ACTOR_ID))
                .thenReturn(CompletableFuture.completedFuture(List.of("follower-1", "follower-2")));
        when(profileLookup.get(ACTOR_ID)).thenReturn(CompletableFuture.completedFuture(
                Profile.builder().userId(ACTOR_ID).pseudonym("Bob").avatarOptions("avatar-json").build()));

        publisher.onPlayerWentOnline(new PresenceEvent.PlayerWentOnlineEvent(ACTOR_ID, AT));

        FollowPresenceView expected = new FollowPresenceView(ACTOR_ID, "Bob", "avatar-json", AT);
        verify(messagingTemplate).convertAndSend("/topic/follow-presence/follower-1", expected);
        verify(messagingTemplate).convertAndSend("/topic/follow-presence/follower-2", expected);
    }

    @Test
    void sendsNothingWithoutFollowers() {
        when(followLookup.followerIds(ACTOR_ID)).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(profileLookup.get(ACTOR_ID)).thenReturn(CompletableFuture.completedFuture(null));

        publisher.onPlayerWentOnline(new PresenceEvent.PlayerWentOnlineEvent(ACTOR_ID, AT));

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void fallsBackToAnonymousWhenProfileIsUnavailable() {
        when(followLookup.followerIds(ACTOR_ID))
                .thenReturn(CompletableFuture.completedFuture(List.of("follower-1")));
        when(profileLookup.get(ACTOR_ID))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("profil absent")));

        publisher.onPlayerWentOnline(new PresenceEvent.PlayerWentOnlineEvent(ACTOR_ID, AT));

        verify(messagingTemplate).convertAndSend(
                "/topic/follow-presence/follower-1",
                new FollowPresenceView(ACTOR_ID, null, null, AT));
    }

    @Test
    void swallowsSocialFailure() {
        when(followLookup.followerIds(ACTOR_ID))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("social indisponible")));
        when(profileLookup.get(ACTOR_ID)).thenReturn(CompletableFuture.completedFuture(null));

        assertThatCode(() ->
                publisher.onPlayerWentOnline(new PresenceEvent.PlayerWentOnlineEvent(ACTOR_ID, AT)))
                .doesNotThrowAnyException();
        verifyNoInteractions(messagingTemplate);
    }
}
