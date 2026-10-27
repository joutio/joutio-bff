package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.request.AddQuestionTranslationRequest;
import com.joutio.bff.infrastructure.in.api.request.CreateQuestionRequest;
import com.joutio.bff.infrastructure.in.api.request.CreateTopicRequest;
import com.joutio.bff.infrastructure.in.api.request.QuestionContentRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateQuestionAnswersRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateQuestionCorrectAnswerRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateQuestionImageUrlRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateQuestionTextRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateTopicBannerUrlRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateTopicCategoryRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateTopicColorRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateTopicDescriptionRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateTopicEmojiRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateTopicImageUrlRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateTopicNameRequest;
import com.joutio.bff.infrastructure.in.api.response.PageResponse;
import com.joutio.bff.infrastructure.in.api.response.QuestionEditorView;
import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.theme.domain.command.QuestionCommand;
import com.joutio.theme.domain.command.TopicCommand;
import com.joutio.theme.domain.model.Question;
import com.joutio.theme.domain.model.QuestionChoice;
import com.joutio.theme.domain.model.QuestionContent;
import com.joutio.theme.domain.model.QuestionReportCount;
import com.joutio.theme.domain.model.Topic;
import com.joutio.theme.domain.query.QuestionQuery;
import com.joutio.theme.domain.query.QuestionReportQuery;
import com.joutio.theme.domain.query.TopicQuery;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Écritures d'auteur : création d'un sujet, mises à jour champ par champ, questions et
 * modération. Le propriétaire du sujet est vérifié ici pour les questions (l'agrégat question ne
 * connaît pas le créateur du sujet) ; pour le sujet lui-même, l'agrégat {@code TopicAggregate}
 * porte la vérification.
 */
@Service
public class TopicAuthoringService {

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;

    public TopicAuthoringService(CommandGateway commandGateway, QueryGateway queryGateway) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
    }

    public CompletableFuture<String> create(String userId, CreateTopicRequest request) {
        String topicId = UUID.randomUUID().toString();
        return commandGateway
                .send(new TopicCommand.CreateTopicCommand(
                        topicId, request.names(), request.description(), request.category(),
                        request.emoji(), request.color(), request.imageUrl(), null, userId))
                .thenApply(_ -> topicId);
    }

    public CompletableFuture<Void> updateName(String topicId, String userId, UpdateTopicNameRequest request) {
        return dispatchTopicField(topicId, userId,
                new TopicCommand.UpdateTopicNameCommand(topicId, userId, request.language(), request.name()));
    }

    public CompletableFuture<Void> updateDescription(String topicId,
                                                     String userId,
                                                     UpdateTopicDescriptionRequest request) {
        return dispatchTopicField(topicId, userId,
                new TopicCommand.UpdateTopicDescriptionCommand(topicId, userId, request.description()));
    }

    public CompletableFuture<Void> updateCategory(String topicId,
                                                  String userId,
                                                  UpdateTopicCategoryRequest request) {
        return dispatchTopicField(topicId, userId,
                new TopicCommand.UpdateTopicCategoryCommand(topicId, userId, request.category()));
    }

    public CompletableFuture<Void> updateEmoji(String topicId, String userId, UpdateTopicEmojiRequest request) {
        return dispatchTopicField(topicId, userId,
                new TopicCommand.UpdateTopicEmojiCommand(topicId, userId, request.emoji()));
    }

    public CompletableFuture<Void> updateColor(String topicId, String userId, UpdateTopicColorRequest request) {
        return dispatchTopicField(topicId, userId,
                new TopicCommand.UpdateTopicColorCommand(topicId, userId, request.color()));
    }

    public CompletableFuture<Void> updateImageUrl(String topicId,
                                                  String userId,
                                                  UpdateTopicImageUrlRequest request) {
        return dispatchTopicField(topicId, userId,
                new TopicCommand.UpdateTopicImageUrlCommand(topicId, userId, request.imageUrl()));
    }

    public CompletableFuture<Void> updateBannerUrl(String topicId,
                                                   String userId,
                                                   UpdateTopicBannerUrlRequest request) {
        return dispatchTopicField(topicId, userId,
                new TopicCommand.UpdateTopicBannerUrlCommand(topicId, userId, request.bannerUrl()));
    }

    public CompletableFuture<Void> publish(String topicId, String userId) {
        return requireOwner(topicId, userId)
                .thenCompose(_ -> commandGateway
                        .send(new TopicCommand.PublishTopicCommand(topicId, userId)))
                .thenAccept(_ -> {
                });
    }

    public CompletableFuture<String> createQuestion(String topicId,
                                                    String userId,
                                                    CreateQuestionRequest request) {
        return requireOwner(topicId, userId).thenCompose(_ -> {
            String questionId = UUID.randomUUID().toString();
            return commandGateway
                    .send(new QuestionCommand.CreateQuestionCommand(
                            questionId, topicId, toContents(request.contents()),
                            request.correctAnswer(), request.imageUrl(), userId))
                    .thenApply(__ -> questionId);
        });
    }

    public CompletableFuture<PageResponse<QuestionEditorView>> questions(String topicId,
                                                                         String userId,
                                                                         int page,
                                                                         int size) {
        return requireOwner(topicId, userId)
                .thenCompose(_ -> queryGateway.query(
                        new QuestionQuery.GetQuestionsByTopicIdQuery(topicId),
                        QueryResponseTypes.multipleInstancesOf(Question.class)))
                .thenCompose(questions -> {
                    List<String> questionIds = questions.stream().map(Question::questionId).toList();
                    if (questionIds.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                PageResponse.of(List.of(), page, size, 0));
                    }
                    return queryGateway.query(
                                    new QuestionReportQuery.GetQuestionReportCountsQuery(questionIds),
                                    QueryResponseTypes.multipleInstancesOf(QuestionReportCount.class))
                            .thenApply(counts -> {
                                Map<String, QuestionReportCount> byQuestion = counts.stream()
                                        .collect(Collectors.toMap(
                                                QuestionReportCount::questionId,
                                                Function.identity(),
                                                (first, _) -> first));
                                List<QuestionEditorView> views = questions.stream()
                                        .sorted(Comparator.comparing(Question::createdAt).reversed())
                                        .map(question -> toEditorView(question,
                                                byQuestion.get(question.questionId())))
                                        .toList();
                                int from = Math.min(page * size, views.size());
                                int to = Math.min(from + size, views.size());
                                return PageResponse.of(views.subList(from, to), page, size, views.size());
                            });
                });
    }

    public CompletableFuture<Void> addTranslation(String questionId,
                                                  String userId,
                                                  AddQuestionTranslationRequest request) {
        return requireQuestionOwner(questionId, userId)
                .thenCompose(_ -> commandGateway
                        .send(new QuestionCommand.AddQuestionTranslationCommand(
                                questionId, userId, request.language(), request.text(),
                                toAnswers(request.answers()))))
                .thenAccept(_ -> {
                });
    }

    public CompletableFuture<Void> updateQuestionText(String questionId,
                                                      String userId,
                                                      UpdateQuestionTextRequest request) {
        return requireQuestionOwner(questionId, userId)
                .thenCompose(_ -> commandGateway
                        .send(new QuestionCommand.UpdateQuestionTextCommand(
                                questionId, userId, request.language(), request.text())))
                .thenAccept(_ -> {
                });
    }

    public CompletableFuture<Void> updateQuestionAnswers(String questionId,
                                                         String userId,
                                                         UpdateQuestionAnswersRequest request) {
        return requireQuestionOwner(questionId, userId)
                .thenCompose(_ -> commandGateway
                        .send(new QuestionCommand.UpdateQuestionAnswersCommand(
                                questionId, userId, request.language(), toAnswers(request.answers()))))
                .thenAccept(_ -> {
                });
    }

    public CompletableFuture<Void> updateQuestionCorrectAnswer(String questionId,
                                                               String userId,
                                                               UpdateQuestionCorrectAnswerRequest request) {
        return requireQuestionOwner(questionId, userId)
                .thenCompose(_ -> commandGateway
                        .send(new QuestionCommand.UpdateQuestionCorrectAnswerCommand(
                                questionId, userId, request.correctAnswer())))
                .thenAccept(_ -> {
                });
    }

    public CompletableFuture<Void> updateQuestionImageUrl(String questionId,
                                                          String userId,
                                                          UpdateQuestionImageUrlRequest request) {
        return requireQuestionOwner(questionId, userId)
                .thenCompose(_ -> commandGateway
                        .send(new QuestionCommand.UpdateQuestionImageUrlCommand(
                                questionId, userId, request.imageUrl())))
                .thenAccept(_ -> {
                });
    }

    public CompletableFuture<Void> approveQuestion(String questionId, String userId) {
        return requireQuestionOwner(questionId, userId)
                .thenCompose(_ -> commandGateway
                        .send(new QuestionCommand.ApproveQuestionCommand(questionId, userId)))
                .thenAccept(_ -> {
                });
    }

    public CompletableFuture<Void> rejectQuestion(String questionId, String userId, String reason) {
        return requireQuestionOwner(questionId, userId)
                .thenCompose(_ -> commandGateway
                        .send(new QuestionCommand.RejectQuestionCommand(questionId, userId, reason)))
                .thenAccept(_ -> {
                });
    }

    private CompletableFuture<Void> dispatchTopicField(String topicId, String userId, TopicCommand command) {
        return requireOwner(topicId, userId)
                .thenCompose(_ -> commandGateway.send(command))
                .thenAccept(_ -> {
                });
    }

    private CompletableFuture<Void> requireOwner(String topicId, String userId) {
        return queryGateway
                .query(new TopicQuery.GetTopicByIdQuery(topicId),
                        QueryResponseTypes.instanceOf(Topic.class))
                .thenAccept(topic -> {
                    if (!Objects.equals(topic.creatorId(), userId)) {
                        throw new BffProblems.NotTopicOwnerProblem(topicId);
                    }
                });
    }

    private CompletableFuture<Question> requireQuestionOwner(String questionId, String userId) {
        return queryGateway
                .query(new QuestionQuery.GetQuestionByIdQuery(questionId),
                        QueryResponseTypes.instanceOf(Question.class))
                .thenCompose(question -> requireOwner(question.topicId(), userId)
                        .thenApply(_ -> question));
    }

    private static Map<Language, QuestionContent> toContents(List<QuestionContentRequest> contents) {
        Map<Language, QuestionContent> result = new EnumMap<>(Language.class);
        for (QuestionContentRequest content : contents) {
            QuestionContent mapped = new QuestionContent(
                    content.language(), content.text(), toAnswers(content.answers()));
            if (result.putIfAbsent(content.language(), mapped) != null) {
                throw new BffProblems.InvalidQuestionRequestProblem(
                        "Duplicate content for language " + content.language());
            }
        }
        return result;
    }

    private static Map<QuestionChoice, String> toAnswers(List<QuestionContentRequest.AnswerRequest> answers) {
        Map<QuestionChoice, String> result = new EnumMap<>(QuestionChoice.class);
        for (QuestionContentRequest.AnswerRequest answer : answers) {
            if (result.put(answer.choice(), answer.text()) != null) {
                throw new BffProblems.InvalidQuestionRequestProblem(
                        "Duplicate answer choice " + answer.choice());
            }
        }
        return result;
    }

    private static QuestionEditorView toEditorView(Question question, QuestionReportCount reportCount) {
        List<QuestionEditorView.ContentView> contents = question.contents().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new QuestionEditorView.ContentView(
                        entry.getKey(),
                        entry.getValue().text(),
                        entry.getValue().answers().entrySet().stream()
                                .sorted(Map.Entry.comparingByKey())
                                .map(answer -> new QuestionEditorView.AnswerView(
                                        answer.getKey(), answer.getValue()))
                                .toList()))
                .toList();

        return new QuestionEditorView(
                question.questionId(),
                contents,
                question.correctAnswer(),
                question.imageUrl(),
                question.status(),
                question.difficulty(),
                question.suspended(),
                question.suspensionReason(),
                question.suspensionNote(),
                question.suspendedAt(),
                question.reinstatedAt(),
                question.archived(),
                question.archiveNote(),
                question.archivedAt(),
                question.restoredAt(),
                reportCount == null ? 0 : reportCount.openCount(),
                reportCount == null ? 0 : reportCount.totalCount(),
                reportCount == null ? null : reportCount.lastReportedAt(),
                question.createdAt(),
                question.updatedAt());
    }
}
