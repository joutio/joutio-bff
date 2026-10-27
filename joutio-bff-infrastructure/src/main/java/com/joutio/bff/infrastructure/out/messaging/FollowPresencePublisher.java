package com.joutio.bff.infrastructure.out.messaging;

import com.joutio.bff.application.FollowLookup;
import com.joutio.bff.application.ProfileLookup;
import com.joutio.bff.infrastructure.in.api.response.FollowPresenceView;
import com.joutio.profile.domain.event.PresenceEvent;
import com.joutio.profile.domain.model.Profile;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Fan-out **éphémère** de présence : quand un joueur passe en ligne, chaque abonné connecté
 * reçoit un message sur {@code /topic/follow-presence/{followerId}} (« X est en ligne »).
 * Aucune persistance, aucun passage par l'inbox de {@code joutio-notification}.
 * Groupe dédié pour ne pas bloquer la diffusion de présence standard
 * ({@code presence-notification}) si la composition sociale/profil échoue.
 */
@Service
@ProcessingGroup("follow-presence-notification")
public class FollowPresencePublisher {

    private static final Logger logger = LoggerFactory.getLogger(FollowPresencePublisher.class);
    private static final String DESTINATION_PREFIX = "/topic/follow-presence/";

    private final SimpMessagingTemplate messagingTemplate;
    private final FollowLookup followLookup;
    private final ProfileLookup profileLookup;

    public FollowPresencePublisher(SimpMessagingTemplate messagingTemplate,
                                   FollowLookup followLookup,
                                   ProfileLookup profileLookup) {
        this.messagingTemplate = messagingTemplate;
        this.followLookup = followLookup;
        this.profileLookup = profileLookup;
    }

    @EventHandler
    public void onPlayerWentOnline(PresenceEvent.PlayerWentOnlineEvent event) {
        String actorId = event.userId();
        CompletableFuture<Profile> profileFuture = profileLookup
                .get(actorId)
                .exceptionally(error -> {
                    logger.debug("Profil indisponible pour la présence éphémère: actorId={}", actorId, error);
                    return null;
                });
        followLookup.followerIds(actorId)
                .thenAcceptBoth(profileFuture, (followers, profile) -> {
                    FollowPresenceView view = new FollowPresenceView(
                            actorId,
                            profile == null ? null : profile.pseudonym(),
                            profile == null ? null : profile.avatarOptions(),
                            event.at());
                    followers.forEach(followerId -> messagingTemplate.convertAndSend(
                            DESTINATION_PREFIX + followerId, view));
                    logger.debug("Présence éphémère diffusée: actorId={}, abonnes={}",
                            actorId, followers.size());
                })
                .exceptionally(error -> {
                    logger.warn("Fan-out de présence éphémère en échec: actorId={}", actorId, error);
                    return null;
                });
    }
}
