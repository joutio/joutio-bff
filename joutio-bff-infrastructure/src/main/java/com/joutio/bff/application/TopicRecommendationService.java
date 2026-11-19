package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.response.TopicCardView;
import com.joutio.game.domain.model.GameRules;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.theme.domain.model.QuestionStatus;
import com.joutio.theme.domain.model.Topic;
import com.joutio.theme.domain.model.TopicCategory;
import com.joutio.theme.domain.model.TopicPage;
import com.joutio.theme.domain.model.TopicSort;
import com.joutio.theme.domain.model.TopicStatus;
import com.joutio.theme.domain.query.TopicQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Recommandation de sujets populaires **jouables** à partir des centres d'intérêt.
 *
 * <ul>
 *   <li>{@link #recommend} (rail « Pour toi » de l'accueil) : un sujet par univers en priorité
 *   (diversité), puis complément dans l'ordre des intérêts ; les sujets suivis sont exclus pour ne
 *   pas dupliquer le rail « Tes sujets suivis ».</li>
 *   <li>{@link #recommendPerCategory} (premier thème de l'onboarding) : jusqu'à N sujets par
 *   univers, univers après univers, sans compléter un univers par un autre.</li>
 * </ul>
 *
 * <p>Les deux lisent les mêmes pages populaires, afin que le premier thème proposé et le rail
 * « Pour toi » restent cohérents.</p>
 */
@Service
public class TopicRecommendationService {

    private static final int MAX_INTERESTS = 3;
    private static final int MAX_LIMIT = 20;
    private static final int QUERY_FLOOR = 10;

    private final QueryGateway queryGateway;

    public TopicRecommendationService(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    public CompletableFuture<List<TopicCardView>> recommend(
            List<TopicCategory> interests,
            Set<String> excludedTopicIds,
            int limit) {
        List<TopicCategory> categories = normalize(interests);
        if (categories.isEmpty() || limit <= 0) {
            return CompletableFuture.completedFuture(List.of());
        }
        int cappedLimit = Math.min(limit, MAX_LIMIT);
        return popularPages(categories, cappedLimit)
                .thenApply(pages -> pick(pages, excludedTopicIds, cappedLimit));
    }

    /**
     * Jusqu'à {@code perCategory} sujets jouables par univers, groupés dans l'ordre des intérêts
     * (soit {@code perCategory × 3} au plus). Un univers sans sujet jouable n'est pas complété.
     */
    public CompletableFuture<List<TopicCardView>> recommendPerCategory(
            List<TopicCategory> interests,
            int perCategory) {
        List<TopicCategory> categories = normalize(interests);
        if (categories.isEmpty() || perCategory <= 0) {
            return CompletableFuture.completedFuture(List.of());
        }
        return popularPages(categories, perCategory)
                .thenApply(pages -> pages.stream()
                        .flatMap(page -> playable(page).stream().limit(perCategory))
                        .map(topic -> TopicViews.toCard(topic, false))
                        .toList());
    }

    /** Une page populaire par univers, dans l'ordre des intérêts. */
    private CompletableFuture<List<TopicPage>> popularPages(List<TopicCategory> categories, int wanted) {
        int pageSize = Math.min(50, Math.max(wanted, QUERY_FLOOR));
        List<CompletableFuture<TopicPage>> pages = categories.stream()
                .map(category -> queryGateway.query(
                        new TopicQuery.GetTopicPageQuery(
                                null, category, TopicSort.POPULAR, 0, pageSize),
                        QueryResponseTypes.instanceOf(TopicPage.class)))
                .toList();
        return CompletableFuture.allOf(pages.toArray(CompletableFuture[]::new))
                .thenApply(_ -> pages.stream().map(CompletableFuture::join).toList());
    }

    private static List<TopicCategory> normalize(List<TopicCategory> interests) {
        if (interests == null || interests.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<TopicCategory> unique = new LinkedHashSet<>();
        for (TopicCategory interest : interests) {
            if (interest != null) {
                unique.add(interest);
            }
        }
        return unique.stream().limit(MAX_INTERESTS).toList();
    }

    private static List<TopicCardView> pick(List<TopicPage> pages,
                                           Set<String> excludedTopicIds,
                                           int limit) {
        Set<String> seen = excludedTopicIds == null
                ? new HashSet<>()
                : new HashSet<>(excludedTopicIds);
        List<Topic> picked = new ArrayList<>();

        // 1) Un sujet par univers, dans l'ordre des intérêts (diversité).
        for (TopicPage page : pages) {
            playable(page).stream()
                    .filter(topic -> !seen.contains(topic.topicId()))
                    .findFirst()
                    .ifPresent(topic -> {
                        seen.add(topic.topicId());
                        picked.add(topic);
                    });
        }

        // 2) Complément dans l'ordre des pages, jusqu'à la limite.
        for (TopicPage page : pages) {
            if (picked.size() >= limit) {
                break;
            }
            for (Topic topic : playable(page)) {
                if (picked.size() >= limit) {
                    break;
                }
                if (seen.add(topic.topicId())) {
                    picked.add(topic);
                }
            }
        }

        return picked.stream()
                .limit(limit)
                .map(topic -> TopicViews.toCard(topic, false))
                .toList();
    }

    private static List<Topic> playable(TopicPage page) {
        if (page == null || page.topics() == null) {
            return List.of();
        }
        return page.topics().stream()
                .filter(TopicRecommendationService::isPlayable)
                .toList();
    }

    private static boolean isPlayable(Topic topic) {
        if (topic.status() != TopicStatus.PUBLISHED) {
            return false;
        }
        int approved = topic.questionsCounter() == null
                ? 0
                : topic.questionsCounter().getOrDefault(QuestionStatus.APPROVED, 0);
        return approved >= GameRules.TOTAL_ROUNDS;
    }
}
