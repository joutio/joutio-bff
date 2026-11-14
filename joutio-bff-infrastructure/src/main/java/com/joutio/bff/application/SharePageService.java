package com.joutio.bff.application;

import com.joutio.identity.domain.model.AccountStatus;
import com.joutio.identity.domain.query.UserQuery;
import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.theme.domain.model.Topic;
import com.joutio.theme.domain.model.TopicStatus;
import com.joutio.theme.domain.query.TopicQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Contenu public d'un lien de partage : titre, description et image d'aperçu (Open Graph), chemin de l'écran dans les
 * apps. Ne révèle jamais de donnée privée : un compte suspendu, supprimé ou inconnu, un sujet non publié donnent la
 * page générique.
 */
@Service
public class SharePageService {

    public enum Kind { PROFILE, TOPIC, ROOM }

    /** @param appPath chemin de l'écran dans l'app (web et lien universel), sans origine */
    public record SharePage(Kind kind, String title, String description, String image, String appPath) {
    }

    private static final String TAGLINE = "Joutio, le jeu de duels de culture générale.";

    private final ProfileLookup profileLookup;
    private final IdentityLookup identityLookup;
    private final QueryGateway queryGateway;
    private final ShareProperties properties;

    public SharePageService(ProfileLookup profileLookup, IdentityLookup identityLookup,
                            QueryGateway queryGateway, ShareProperties properties) {
        this.profileLookup = profileLookup;
        this.identityLookup = identityLookup;
        this.queryGateway = queryGateway;
        this.properties = properties;
    }

    public CompletableFuture<SharePage> profile(String userId) {
        return identityLookup.status(userId)
                .thenCompose(status -> {
                    if (status.status() == AccountStatus.DELETED || status.suspended(Instant.now())) {
                        return CompletableFuture.completedFuture(generic(Kind.PROFILE, "/"));
                    }
                    return profileLookup.get(userId).thenApply(profile -> new SharePage(
                            Kind.PROFILE,
                            profile.pseudonym() + " te défie sur Joutio",
                            profile.bio() != null && !profile.bio().isBlank() ? profile.bio() : "Rejoins " + profile.pseudonym() + " sur " + TAGLINE,
                            image(profile.bannerUrl()),
                            "/players/" + userId));
                })
                .exceptionally(_ -> generic(Kind.PROFILE, "/"));
    }

    public CompletableFuture<SharePage> topic(String topicId) {
        return queryGateway.query(new TopicQuery.GetTopicsByIdsQuery(List.of(topicId)), QueryResponseTypes.multipleInstancesOf(Topic.class))
                .thenApply(topics -> topics.stream()
                        .filter(topic -> topic.status() == TopicStatus.PUBLISHED)
                        .findFirst()
                        .map(topic -> {
                            String name = topic.names() == null ? null
                                    : topic.names().getOrDefault(Language.FR, topic.names().values().stream().findFirst().orElse(null));
                            return new SharePage(
                                    Kind.TOPIC,
                                    (name == null ? "Un sujet" : name) + " sur Joutio : viens me défier !",
                                    topic.description() != null && !topic.description().isBlank() ? topic.description() : TAGLINE,
                                    image(topic.bannerUrl() != null ? topic.bannerUrl() : topic.imageUrl()),
                                    "/topics/" + topic.topicId());
                        })
                        .orElseGet(() -> generic(Kind.TOPIC, "/topics")))
                .exceptionally(_ -> generic(Kind.TOPIC, "/topics"));
    }

    /** Les salons sont éphémères : page sans lecture du salon, l'app affiche son état réel. */
    public CompletableFuture<SharePage> room(String roomId) {
        return CompletableFuture.completedFuture(new SharePage(
                Kind.ROOM, "Un défi t'attend sur Joutio", "Rejoins le salon et affronte ton adversaire en direct.",
                properties.defaultImage(), "/join/" + roomId));
    }

    private SharePage generic(Kind kind, String appPath) {
        return new SharePage(kind, "Joutio", "Défie tes amis en duels de culture générale.", properties.defaultImage(), appPath);
    }

    private String image(String url) {
        return url == null || url.isBlank() ? properties.defaultImage() : url;
    }
}
