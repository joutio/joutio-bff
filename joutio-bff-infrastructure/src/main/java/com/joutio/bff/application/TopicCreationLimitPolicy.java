package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.microservice.core.domain.constant.JoutioConstants;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.theme.domain.model.Question;
import com.joutio.theme.domain.model.QuestionStatus;
import com.joutio.theme.domain.model.Topic;
import com.joutio.theme.domain.model.TopicPage;
import com.joutio.theme.domain.model.TopicStatus;
import com.joutio.theme.domain.query.QuestionQuery;
import com.joutio.theme.domain.query.TopicQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Plafonds de création appliqués côté serveur (premium/specs/entitlements.md § 4-5) :
 *
 * <ul>
 *   <li><b>Sujets publiés</b> par créateur : 1 gratuit, 10 Premium (vérifié à la publication ;
 *       les brouillons restent libres).</li>
 *   <li><b>Questions par sujet</b> : 30 gratuit, 150 Premium, selon le plan du <b>créateur</b> du
 *       sujet, quel que soit l'auteur. Comptent les questions en attente, approuvées ou
 *       suspendues ; ni les archivées ni les rejetées.</li>
 *   <li><b>Gel</b> : un créateur non Premium qui dépasse le plafond gratuit de sujets publiés (après
 *       expiration de Premium) ne peut plus modifier aucun de ses sujets publiés ni leurs questions,
 *       jusqu'à dépublier ou repasser Premium. Les brouillons, la dépublication, l'archivage et la
 *       suppression restent possibles ; rien n'est dépublié d'office.</li>
 * </ul>
 *
 * <p>Les sujets du compte système (seed) ne sont pas plafonnés.</p>
 */
@Service
public class TopicCreationLimitPolicy {

    /** Statuts qui occupent une place dans le plafond de questions. */
    static final Set<QuestionStatus> COUNTED =
            EnumSet.of(QuestionStatus.PENDING, QuestionStatus.APPROVED, QuestionStatus.SUSPENDED);
    private static final int CREATOR_PAGE_SIZE = 500;

    /** Plafonds résolus pour un plan ({@link Integer#MAX_VALUE} = illimité). */
    public record CreationLimits(int publishedTopics, int questionsPerTopic) {
    }

    private final QueryGateway queryGateway;
    private final PremiumAccessPolicy premiumAccessPolicy;
    private final CreationLimitsProperties properties;

    public TopicCreationLimitPolicy(QueryGateway queryGateway,
                                    PremiumAccessPolicy premiumAccessPolicy,
                                    CreationLimitsProperties properties) {
        this.queryGateway = queryGateway;
        this.premiumAccessPolicy = premiumAccessPolicy;
        this.properties = properties;
    }

    public CreationLimits limits(boolean premium) {
        return new CreationLimits(
                limit(premium ? properties.premiumPublishedTopics() : properties.publishedTopics()),
                limit(premium ? properties.premiumQuestionsPerTopic() : properties.questionsPerTopic()));
    }

    /** Publication : place libre parmi les sujets publiés du créateur et taille du sujet. */
    public CompletableFuture<Void> ensureCanPublish(String topicId) {
        return topic(topicId).thenCompose(topic -> {
            if (exempt(topic)) {
                return done();
            }
            return premiumAccessPolicy.premium(topic.creatorId()).thenCompose(premium -> {
                CreationLimits limits = limits(premium);
                return publishedCount(topic.creatorId(), topicId)
                        .thenCombine(questionCount(topicId), (published, questions) -> {
                            if (published >= limits.publishedTopics()) {
                                throw new BffProblems.TopicQuotaExceededProblem(limits.publishedTopics());
                            }
                            if (questions > limits.questionsPerTopic()) {
                                throw new BffProblems.QuestionQuotaExceededProblem(limits.questionsPerTopic());
                            }
                            return null;
                        });
            });
        });
    }

    /**
     * Ajout d'une question (création, restauration, nouvelle soumission) : sujet non gelé et place
     * libre dans le plafond du créateur.
     */
    public CompletableFuture<Void> ensureCanAddQuestion(String topicId) {
        return topic(topicId).thenCompose(topic -> {
            if (exempt(topic)) {
                return done();
            }
            return premiumAccessPolicy.premium(topic.creatorId()).thenCompose(premium ->
                    ensureNotFrozen(topic, premium)
                            .thenCompose(_ -> questionCount(topicId))
                            .thenAccept(count -> {
                                int limit = limits(premium).questionsPerTopic();
                                if (count >= limit) {
                                    throw new BffProblems.QuestionQuotaExceededProblem(limit);
                                }
                            }));
        });
    }

    /** Édition d'un sujet ou d'une question existante : refusée si le sujet est gelé. */
    public CompletableFuture<Void> ensureEditable(String topicId) {
        return topic(topicId).thenCompose(topic -> {
            if (exempt(topic) || topic.status() != TopicStatus.PUBLISHED) {
                return done();
            }
            return premiumAccessPolicy.premium(topic.creatorId())
                    .thenCompose(premium -> ensureNotFrozen(topic, premium));
        });
    }

    private CompletableFuture<Void> ensureNotFrozen(Topic topic, boolean premium) {
        if (premium || topic.status() != TopicStatus.PUBLISHED) {
            return done();
        }
        int freeLimit = limits(false).publishedTopics();
        return publishedCount(topic.creatorId(), null).thenAccept(published -> {
            if (published > freeLimit) {
                throw new BffProblems.TopicOverQuotaProblem(topic.topicId(), freeLimit, published);
            }
        });
    }

    private CompletableFuture<Topic> topic(String topicId) {
        return queryGateway.query(new TopicQuery.GetTopicByIdQuery(topicId),
                QueryResponseTypes.instanceOf(Topic.class));
    }

    /** Sujets publiés du créateur, hors {@code excludedTopicId} (le sujet en cours de publication). */
    private CompletableFuture<Integer> publishedCount(String creatorId, String excludedTopicId) {
        return queryGateway.query(new TopicQuery.GetTopicsByCreatorQuery(creatorId, 0, CREATOR_PAGE_SIZE),
                        QueryResponseTypes.instanceOf(TopicPage.class))
                .thenApply(page -> (int) page.topics().stream()
                        .filter(topic -> topic.status() == TopicStatus.PUBLISHED)
                        .filter(topic -> !topic.topicId().equals(excludedTopicId))
                        .count());
    }

    private CompletableFuture<Integer> questionCount(String topicId) {
        return queryGateway.query(new QuestionQuery.GetQuestionsByTopicIdQuery(topicId),
                        QueryResponseTypes.multipleInstancesOf(Question.class))
                .thenApply(TopicCreationLimitPolicy::countQuestions);
    }

    static int countQuestions(List<Question> questions) {
        return (int) questions.stream().filter(question -> COUNTED.contains(question.status())).count();
    }

    private static boolean exempt(Topic topic) {
        return JoutioConstants.SYSTEM_USER_ID.equals(topic.creatorId());
    }

    private static CompletableFuture<Void> done() {
        return CompletableFuture.completedFuture(null);
    }

    private static int limit(int configured) {
        return configured <= 0 ? Integer.MAX_VALUE : configured;
    }
}
