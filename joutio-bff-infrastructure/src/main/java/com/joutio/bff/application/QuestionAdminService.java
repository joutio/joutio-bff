package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.request.AddQuestionTranslationRequest;
import com.joutio.bff.infrastructure.in.api.request.ArchiveQuestionRequest;
import com.joutio.bff.infrastructure.in.api.request.CreateQuestionRequest;
import com.joutio.bff.infrastructure.in.api.request.QuestionContentRequest;
import com.joutio.bff.infrastructure.in.api.request.ReinstateQuestionRequest;
import com.joutio.bff.infrastructure.in.api.request.ResolveQuestionReportsRequest;
import com.joutio.bff.infrastructure.in.api.request.RestoreQuestionRequest;
import com.joutio.bff.infrastructure.in.api.request.SuspendQuestionRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateQuestionAnswersRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateQuestionCorrectAnswerRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateQuestionImageUrlRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateQuestionTextRequest;
import com.joutio.bff.infrastructure.in.api.response.AdminQuestionDetailView;
import com.joutio.bff.infrastructure.in.api.response.AdminQuestionReportResolutionView;
import com.joutio.bff.infrastructure.in.api.response.AdminQuestionReportSummaryView;
import com.joutio.bff.infrastructure.in.api.response.AdminQuestionReportView;
import com.joutio.bff.infrastructure.in.api.response.AdminQuestionSummaryView;
import com.joutio.bff.infrastructure.in.api.response.AdminQuestionView;
import com.joutio.bff.infrastructure.in.api.response.AdminTopicOptionView;
import com.joutio.bff.infrastructure.in.api.response.PageResponse;
import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.theme.domain.command.QuestionCommand;
import com.joutio.theme.domain.model.Question;
import com.joutio.theme.domain.model.QuestionAdminFilter;
import com.joutio.theme.domain.model.QuestionAdminPage;
import com.joutio.theme.domain.model.QuestionAdminSort;
import com.joutio.theme.domain.model.QuestionChoice;
import com.joutio.theme.domain.model.QuestionContent;
import com.joutio.theme.domain.model.QuestionReport;
import com.joutio.theme.domain.model.QuestionReportCount;
import com.joutio.theme.domain.model.QuestionReportResolution;
import com.joutio.theme.domain.model.QuestionReportSort;
import com.joutio.theme.domain.model.QuestionReportStats;
import com.joutio.theme.domain.model.QuestionReportStatus;
import com.joutio.theme.domain.model.QuestionReportSummary;
import com.joutio.theme.domain.model.QuestionReportSummaryFilter;
import com.joutio.theme.domain.model.QuestionReportSummaryPage;
import com.joutio.theme.domain.model.QuestionStatus;
import com.joutio.theme.domain.model.QuestionSuspensionReason;
import com.joutio.theme.domain.model.Topic;
import com.joutio.theme.domain.model.TopicStatus;
import com.joutio.theme.domain.query.QuestionQuery;
import com.joutio.theme.domain.query.QuestionReportQuery;
import com.joutio.theme.domain.query.TopicQuery;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Administration du catalogue de questions : lecture globale, création, édition, modération
 * éditoriale, suspension/réactivation, archivage/restauration et synthèse des signalements.
 */
@Service
public class QuestionAdminService {

    private final QueryGateway queryGateway;
    private final CommandGateway commandGateway;
    private final QuestionReportService questionReportService;

    public QuestionAdminService(QueryGateway queryGateway,
                                CommandGateway commandGateway,
                                QuestionReportService questionReportService) {
        this.queryGateway = queryGateway;
        this.commandGateway = commandGateway;
        this.questionReportService = questionReportService;
    }

    public CompletableFuture<PageResponse<AdminQuestionView>> list(QuestionAdminFilter filter,
                                                                    QuestionAdminSort sort,
                                                                    int page,
                                                                    int size) {
        return queryGateway.query(
                        new QuestionQuery.GetQuestionAdminPageQuery(filter, sort, page, size),
                        QueryResponseTypes.instanceOf(QuestionAdminPage.class))
                .thenCompose(adminPage -> enrich(adminPage.questions(), page, size, adminPage.totalElements()));
    }

    public CompletableFuture<PageResponse<AdminQuestionSummaryView>> queue(QuestionReportSummaryFilter filter,
                                                                           QuestionReportSort sort,
                                                                           int page,
                                                                           int size) {
        return queryGateway.query(
                        new QuestionReportQuery.GetReportedQuestionPageQuery(filter, sort, page, size),
                        QueryResponseTypes.instanceOf(QuestionReportSummaryPage.class))
                .thenCompose(summaryPage -> {
                    List<String> questionIds = summaryPage.content().stream()
                            .map(QuestionReportSummary::questionId)
                            .toList();
                    if (questionIds.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                PageResponse.of(List.of(), page, size, summaryPage.totalElements()));
                    }
                    CompletableFuture<List<Question>> questionsFuture = queryGateway.query(
                            new QuestionQuery.GetQuestionsByIdsQuery(questionIds),
                            QueryResponseTypes.multipleInstancesOf(Question.class));
                    CompletableFuture<List<Topic>> topicsFuture = questionsFuture.thenCompose(questions -> {
                        List<String> topicIds = questions.stream()
                                .map(Question::topicId)
                                .distinct()
                                .toList();
                        return queryGateway.query(
                                new TopicQuery.GetTopicsByIdsQuery(topicIds),
                                QueryResponseTypes.multipleInstancesOf(Topic.class));
                    });
                    return CompletableFuture.allOf(questionsFuture, topicsFuture).thenApply(_ -> {
                        List<Question> questions = questionsFuture.join();
                        Map<String, Question> byId = questions.stream()
                                .collect(Collectors.toMap(Question::questionId, Function.identity(),
                                        (first, _) -> first));
                        Map<String, Topic> topics = topicsFuture.join().stream()
                                .collect(Collectors.toMap(Topic::topicId, Function.identity(),
                                        (first, _) -> first));
                        Map<String, QuestionReportSummary> summaries = summaryPage.content().stream()
                                .collect(Collectors.toMap(QuestionReportSummary::questionId,
                                        Function.identity(), (first, _) -> first));
                        List<AdminQuestionSummaryView> views = summaryPage.content().stream()
                                .map(summary -> {
                                    Question question = byId.get(summary.questionId());
                                    return question == null ? null : QuestionViews.toSummaryView(
                                            question,
                                            topics.get(question.topicId()),
                                            summaries.get(summary.questionId()));
                                })
                                .filter(Objects::nonNull)
                                .toList();
                        return PageResponse.of(views, page, size, summaryPage.totalElements());
                    });
                });
    }

    public CompletableFuture<AdminQuestionDetailView> detail(String questionId) {
        CompletableFuture<Question> questionFuture = requireQuestion(questionId);
        CompletableFuture<List<AdminQuestionReportView>> reportsFuture =
                questionReportService.adminReports(questionId);
        return questionFuture.thenCompose(question -> {
            CompletableFuture<Topic> topicFuture = topicOf(question.topicId());
            return CompletableFuture.allOf(topicFuture, reportsFuture).thenApply(_ -> {
                QuestionReportCount count = reportCount(questionId, reportsFuture.join());
                AdminQuestionView view = QuestionViews.toAdminView(question, topicFuture.join(), count);
                return new AdminQuestionDetailView(view, reportsFuture.join());
            });
        });
    }

    public CompletableFuture<List<AdminTopicOptionView>> topics() {
        return queryGateway.query(
                        new TopicQuery.GetAdminTopicOptionsQuery(),
                        QueryResponseTypes.multipleInstancesOf(Topic.class))
                .thenApply(topics -> topics.stream()
                        .map(topic -> new AdminTopicOptionView(
                                topic.topicId(),
                                topic.names(),
                                topic.status(),
                                topic.questionsCounter() == null ? 0
                                        : topic.questionsCounter().getOrDefault(QuestionStatus.APPROVED, 0)))
                        .toList());
    }

    public CompletableFuture<AdminQuestionReportSummaryView> summary() {
        return queryGateway.query(
                        new QuestionReportQuery.GetQuestionReportStatsQuery(),
                        QueryResponseTypes.instanceOf(QuestionReportStats.class))
                .thenApply(stats -> new AdminQuestionReportSummaryView(
                        stats.openReports(),
                        stats.reportedQuestions(),
                        stats.suspendedQuestions(),
                        stats.archivedQuestions()));
    }

    public CompletableFuture<String> create(String topicId,
                                            String adminId,
                                            CreateQuestionRequest request) {
        return queryGateway.query(
                        new TopicQuery.GetTopicByIdQuery(topicId),
                        QueryResponseTypes.instanceOf(Topic.class))
                .thenCompose(_ -> {
                    String questionId = UUID.randomUUID().toString();
                    return commandGateway.send(new QuestionCommand.CreateQuestionCommand(
                                    questionId,
                                    topicId,
                                    toContents(request.contents()),
                                    request.correctAnswer(),
                                    request.imageUrl(),
                                    adminId))
                            .thenApply(__ -> questionId);
                });
    }

    public CompletableFuture<Void> addTranslation(String questionId,
                                                  String adminId,
                                                  AddQuestionTranslationRequest request) {
        return requireQuestion(questionId)
                .thenCompose(_ -> commandGateway.send(new QuestionCommand.AddQuestionTranslationCommand(
                        questionId, adminId, request.language(), request.text(),
                        toAnswers(request.answers()))))
                .thenAccept(_ -> {
                });
    }

    public CompletableFuture<Void> updateText(String questionId,
                                              String adminId,
                                              UpdateQuestionTextRequest request) {
        return requireQuestion(questionId)
                .thenCompose(_ -> commandGateway.send(new QuestionCommand.UpdateQuestionTextCommand(
                        questionId, adminId, request.language(), request.text())))
                .thenAccept(_ -> {
                });
    }

    public CompletableFuture<Void> updateAnswers(String questionId,
                                                 String adminId,
                                                 UpdateQuestionAnswersRequest request) {
        return requireQuestion(questionId)
                .thenCompose(_ -> commandGateway.send(new QuestionCommand.UpdateQuestionAnswersCommand(
                        questionId, adminId, request.language(), toAnswers(request.answers()))))
                .thenAccept(_ -> {
                });
    }

    public CompletableFuture<Void> updateCorrectAnswer(String questionId,
                                                       String adminId,
                                                       UpdateQuestionCorrectAnswerRequest request) {
        return requireQuestion(questionId)
                .thenCompose(_ -> commandGateway.send(new QuestionCommand.UpdateQuestionCorrectAnswerCommand(
                        questionId, adminId, request.correctAnswer())))
                .thenAccept(_ -> {
                });
    }

    public CompletableFuture<Void> updateImageUrl(String questionId,
                                                  String adminId,
                                                  UpdateQuestionImageUrlRequest request) {
        return requireQuestion(questionId)
                .thenCompose(_ -> commandGateway.send(new QuestionCommand.UpdateQuestionImageUrlCommand(
                        questionId, adminId, request.imageUrl())))
                .thenAccept(_ -> {
                });
    }

    public CompletableFuture<Void> approve(String questionId, String adminId) {
        return requireQuestion(questionId)
                .thenCompose(_ -> commandGateway.send(
                        new QuestionCommand.ApproveQuestionCommand(questionId, adminId)))
                .thenAccept(_ -> {
                });
    }

    public CompletableFuture<Void> reject(String questionId, String adminId, String reason) {
        return requireQuestion(questionId)
                .thenCompose(_ -> commandGateway.send(
                        new QuestionCommand.RejectQuestionCommand(questionId, adminId, reason)))
                .thenAccept(_ -> {
                });
    }

    public CompletableFuture<Void> suspend(String questionId,
                                           String adminId,
                                           SuspendQuestionRequest request) {
        return requireQuestion(questionId)
                .thenCompose(_ -> commandGateway.send(new QuestionCommand.SuspendQuestionCommand(
                        questionId,
                        QuestionSuspensionReason.MANUAL,
                        request == null ? null : request.note(),
                        adminId,
                        null,
                        null,
                        0,
                        0,
                        Instant.now())))
                .thenAccept(_ -> {
                });
    }

    public CompletableFuture<Void> reinstate(String questionId,
                                             String adminId,
                                             ReinstateQuestionRequest request) {
        return requireQuestion(questionId)
                .thenCompose(_ -> {
                    if (request != null && Boolean.TRUE.equals(request.resolveOpenReports())) {
                        return questionReportService.resolveAdminAll(
                                        questionId,
                                        adminId,
                                        new ResolveQuestionReportsRequest(
                                                QuestionReportResolution.RESOLVED,
                                                request.note()))
                                .thenApply(_ -> null);
                    }
                    return CompletableFuture.completedFuture(null);
                })
                .thenCompose(_ -> commandGateway.send(new QuestionCommand.ReinstateQuestionCommand(
                        questionId, adminId, request == null ? null : request.note(), Instant.now())))
                .thenAccept(_ -> {
                });
    }

    public CompletableFuture<Void> archive(String questionId,
                                           String adminId,
                                           ArchiveQuestionRequest request) {
        return requireQuestion(questionId)
                .thenCompose(_ -> {
                    if (request != null && Boolean.TRUE.equals(request.resolveOpenReports())) {
                        return questionReportService.resolveAdminAll(
                                        questionId,
                                        adminId,
                                        new ResolveQuestionReportsRequest(
                                                QuestionReportResolution.RESOLVED,
                                                request.note()))
                                .thenApply(_ -> null);
                    }
                    return CompletableFuture.completedFuture(null);
                })
                .thenCompose(_ -> commandGateway.send(new QuestionCommand.ArchiveQuestionCommand(
                        questionId, adminId, request == null ? null : request.note(), Instant.now())))
                .thenAccept(_ -> {
                });
    }

    public CompletableFuture<Void> restore(String questionId,
                                           String adminId,
                                           RestoreQuestionRequest request) {
        return requireQuestion(questionId)
                .thenCompose(_ -> commandGateway.send(new QuestionCommand.RestoreQuestionCommand(
                        questionId, adminId, request == null ? null : request.note(), Instant.now())))
                .thenAccept(_ -> {
                });
    }

    private CompletableFuture<PageResponse<AdminQuestionView>> enrich(List<Question> questions,
                                                                      int page,
                                                                      int size,
                                                                      long totalElements) {
        if (questions.isEmpty()) {
            return CompletableFuture.completedFuture(PageResponse.of(List.of(), page, size, totalElements));
        }
        List<String> questionIds = questions.stream().map(Question::questionId).toList();
        List<String> topicIds = questions.stream().map(Question::topicId).distinct().toList();
        CompletableFuture<List<QuestionReportCount>> countsFuture = queryGateway.query(
                new QuestionReportQuery.GetQuestionReportCountsQuery(questionIds),
                QueryResponseTypes.multipleInstancesOf(QuestionReportCount.class));
        CompletableFuture<List<Topic>> topicsFuture = queryGateway.query(
                new TopicQuery.GetTopicsByIdsQuery(topicIds),
                QueryResponseTypes.multipleInstancesOf(Topic.class));
        return CompletableFuture.allOf(countsFuture, topicsFuture).thenApply(_ -> {
            Map<String, QuestionReportCount> counts = countsFuture.join().stream()
                    .collect(Collectors.toMap(QuestionReportCount::questionId,
                            Function.identity(), (first, _) -> first));
            Map<String, Topic> topics = topicsFuture.join().stream()
                    .collect(Collectors.toMap(Topic::topicId, Function.identity(),
                            (first, _) -> first));
            List<AdminQuestionView> views = questions.stream()
                    .map(question -> QuestionViews.toAdminView(
                            question,
                            topics.get(question.topicId()),
                            counts.get(question.questionId())))
                    .toList();
            return PageResponse.of(views, page, size, totalElements);
        });
    }

    private CompletableFuture<Topic> topicOf(String topicId) {
        return queryGateway.query(
                new TopicQuery.GetTopicByIdQuery(topicId),
                QueryResponseTypes.instanceOf(Topic.class));
    }

    private CompletableFuture<Question> requireQuestion(String questionId) {
        return queryGateway.query(
                new QuestionQuery.GetQuestionByIdQuery(questionId),
                QueryResponseTypes.instanceOf(Question.class));
    }

    private static QuestionReportCount reportCount(String questionId,
                                                   List<AdminQuestionReportView> reports) {
        int open = (int) reports.stream()
                .filter(report -> report.status() == QuestionReportStatus.OPEN)
                .count();
        Instant last = reports.stream()
                .map(AdminQuestionReportView::createdAt)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        return new QuestionReportCount(questionId, open, reports.size(), last);
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
}
