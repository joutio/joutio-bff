package com.joutio.bff.infrastructure.in.websocket;

import com.joutio.bff.application.live.LiveViews;
import com.joutio.bff.application.live.VersionedLiveView;
import com.joutio.bff.infrastructure.in.api.problem.BffProblems.LiveViewForbiddenProblem;
import com.joutio.bff.infrastructure.in.api.problem.BffProblems.LiveViewNotFoundProblem;
import com.joutio.microservice.core.domain.exception.BaseProblem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contrôle d'accès des vues vivantes, avant que le broker n'enregistre l'abonnement (il diffuse ensuite à tous les
 * abonnés de la destination).
 *
 * <ul>
 *   <li>{@code SUBSCRIBE} refusé (anonyme, vue inconnue sous {@code /topic/live/}, règle de la vue) : la trame est
 *   abandonnée, aucun abonnement n'existe, et le problème est poussé au joueur sur {@code /user/queue/live}. La
 *   connexion et ses autres abonnements restent ouverts.</li>
 *   <li>{@code SEND} vers une vue vivante : abandonné, seul le BFF y publie.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class LiveViewChannelConfiguration implements WebSocketMessageBrokerConfigurer {

    public static final String ERROR_QUEUE = "/queue/live";

    private final ObjectProvider<LiveViews> liveViews;
    private final ObjectProvider<SimpMessagingTemplate> messagingTemplate;

    /** Fournisseurs paresseux : les configurers STOMP sont créés avant le bus de requêtes et le broker. */
    public LiveViewChannelConfiguration(ObjectProvider<LiveViews> liveViews,
                                        ObjectProvider<SimpMessagingTemplate> messagingTemplate) {
        this.liveViews = liveViews;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new LiveViewChannelInterceptor(liveViews, messagingTemplate));
    }

    static final class LiveViewChannelInterceptor implements ChannelInterceptor {

        private static final Logger logger = LoggerFactory.getLogger(LiveViewChannelInterceptor.class);

        private final ObjectProvider<LiveViews> liveViews;
        private final ObjectProvider<SimpMessagingTemplate> messagingTemplate;

        LiveViewChannelInterceptor(ObjectProvider<LiveViews> liveViews, ObjectProvider<SimpMessagingTemplate> messagingTemplate) {
            this.liveViews = liveViews;
            this.messagingTemplate = messagingTemplate;
        }

        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor frame = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            if (frame == null || frame.getDestination() == null) {
                return message;
            }
            String destination = frame.getDestination();
            var target = liveViews.getObject().resolve(destination);
            boolean live = target.isPresent() || destination.startsWith(VersionedLiveView.DESTINATION_PREFIX);
            if (!live) {
                return message;
            }
            if (StompCommand.SEND.equals(frame.getCommand())) {
                return null;
            }
            if (!StompCommand.SUBSCRIBE.equals(frame.getCommand())) {
                return message;
            }
            Principal user = frame.getUser();
            BaseProblem refusal;
            if (target.isEmpty()) {
                refusal = new LiveViewNotFoundProblem(destination);
            } else if (user == null || !target.get().view().canSubscribe(user.getName(), target.get().key())) {
                refusal = new LiveViewForbiddenProblem(destination, target.get().key());
            } else {
                return message;
            }
            logger.info("Live view subscription refused: destination={}, userId={}, type={}",
                    destination, user == null ? null : user.getName(), refusal.getType());
            if (user != null) {
                Map<String, Object> problem = new LinkedHashMap<>();
                problem.put("type", refusal.getType());
                problem.put("title", refusal.getTitle());
                problem.put("detail", refusal.getDetail());
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("destination", destination);
                payload.put("problem", problem);
                messagingTemplate.getObject().convertAndSendToUser(user.getName(), ERROR_QUEUE, payload);
            }
            return null;
        }
    }
}
