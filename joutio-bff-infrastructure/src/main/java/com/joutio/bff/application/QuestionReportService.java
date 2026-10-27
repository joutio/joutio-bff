package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.request.ReportQuestionRequest;
import com.joutio.bff.infrastructure.in.api.request.ResolveQuestionReportsRequest;
import com.joutio.bff.infrastructure.in.api.response.AdminQuestionReportResolutionView;
import com.joutio.bff.infrastructure.in.api.response.AdminQuestionReportView;
import com.joutio.bff.infrastructure.in.api.response.OwnerQuestionReportView;
import com.joutio.bff.infrastructure.in.api.response.QuestionReportAckView;
import com.joutio.bff.infrastructure.out.messaging.mapper.GameEventNotificationMapper;
import com.joutio.bff.infrastructure.out.messaging.response.GameNotification;
import com.joutio.game.domain.event.GameEvent;
import com.joutio.game.domain.model.Game;
import com.joutio.game.domain.model.GameRound;
import com.joutio.game.domain.model.GameStatus;
import com.joutio.game.domain.query.GameQuery;
import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.microservice.core.domain.model.notification.EventEnvelope;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.profile.domain.model.Profile;
import com.joutio.theme.domain.command.QuestionReportCommand;
import com.joutio.theme.domain.model.Question;
import com.joutio.theme.domain.model.QuestionReport;
import com.joutio.theme.domain.model.QuestionReportResult;
import com.joutio.theme.domain.model.QuestionReportRules;
import com.joutio.theme.domain.model.QuestionReportSnapshot;
import com.joutio.theme.domain.model.QuestionReportSnapshotSource;
import com.joutio.theme.domain.model.QuestionReportStatus;
import com.joutio.theme.domain.model.QuestionReportTranslation;
import com.joutio.theme.domain.model.Topic;
import com.joutio.theme.domain.query.QuestionQuery;
import com.joutio.theme.domain.query.QuestionReportQuery;
import com.joutio.theme.domain.query.TopicQuery;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Signalements de questions : dépôt par un joueur, lecture propriétaire (sans identité) et
 * résolution par le propriétaire ou l'administration.
 */
@Service
public class QuestionReportService {

    private final QueryGateway queryGateway;
    private final CommandGateway commandGateway;
    private final ProfileLookup profileLookup;

    public QuestionReportService(QueryGateway queryGateway,
                                 CommandGateway commandGateway,
                                 ProfileLookup profileLookup) {
        this.queryGateway = queryGateway;
        this.commandGateway = commandGateway;
        this.profileLookup = profileLookup;
    }

    public CompletableFuture<QuestionReportAckView> report(String questionId,
                                                           String reporterId,
                                                           ReportQuestionRequest request) {
        Instant reportedAt = Instant.now();
        CompletableFuture<Question> questionFuture = requireQuestion(questionId);
        CompletableFuture<Game> gameFuture = queryGateway.query(
                new GameQuery.GetGameByIdQuery(request.gameId()),
                QueryResponseTypes.instanceOf(Game.class));

        return CompletableFuture.allOf(questionFuture, gameFuture).thenCompose(_ -> {
            Question question = questionFuture.join();
            Game game = gameFuture.join();
            validateParticipation(question, game, reporterId);

            CompletableFuture<Topic> topicFuture = queryGateway.query(
                    new TopicQuery.GetTopicByIdQuery(question.topicId()),
                    QueryResponseTypes.instanceOf(Topic.class));
            CompletableFuture<List<EventEnvelope>> eventsFuture = queryGateway.query(
                    new GameQuery.GetGameEventsQuery(game.gameId()),
                    QueryResponseTypes.multipleInstancesOf(EventEnvelope.class))
                    .exceptionally(_ -> List.of());

            return CompletableFuture.allOf(topicFuture, eventsFuture).thenCompose(_ -> {
                Topic topic = topicFuture.join();
                QuestionReportSnapshot snapshot = snapshotOf(
                        eventsFuture.join(), game, question, request.language());
                String threadId = QuestionReportRules.threadId(questionId, reporterId);
                return commandGateway.send(new QuestionReportCommand.ReportQuestionCommand(
                                threadId,
                                questionId,
                                question.topicId(),
                                topic == null ? null : topic.creatorId(),
                                reporterId,
                                game.gameId(),
                                request.reasons(),
                                normalizeComment(request.comment()),
                                request.language(),
                                snapshot,
                                reportedAt))
                        .thenApply(result -> (QuestionReportResult) result)
                        .thenApply(result -> new QuestionReportAckView(
                                result.reportId(),
                                QuestionReportStatus.OPEN,
                                reportedAt,
                                result.created()));
            });
        });
    }

    public CompletableFuture<List<OwnerQuestionReportView>> ownerReports(String questionId, String userId) {
        return requireQuestion(questionId)
                .thenCompose(question -> requireOwner(question.topicId(), userId)
                        .thenCompose(_ -> reportsOf(questionId)))
                .thenApply(reports -> reports.stream().map(OwnerQuestionReportView::of).toList());
    }

    public CompletableFuture<AdminQuestionReportResolutionView> resolveOwner(String questionId,
                                                                             String reportId,
                                                                             String userId,
                                                                             ResolveQuestionReportsRequest request) {
        return requireQuestion(questionId)
                .thenCompose(question -> requireOwner(question.topicId(), userId)
                        .thenCompose(_ -> requireReport(reportId)))
                .thenCompose(report -> {
                    if (!report.questionId().equals(questionId)) {
                        return CompletableFuture.completedFuture(new AdminQuestionReportResolutionView(0));
                    }
                    return resolveOne(report, userId, request);
                });
    }

    public CompletableFuture<AdminQuestionReportResolutionView> resolveOwnerAll(String questionId,
                                                                                String userId,
                                                                                ResolveQuestionReportsRequest request) {
        return requireQuestion(questionId)
                .thenCompose(question -> requireOwner(question.topicId(), userId)
                        .thenCompose(_ -> openReportsOf(questionId)))
                .thenCompose(reports -> resolveAll(reports, userId, request));
    }

    public CompletableFuture<List<AdminQuestionReportView>> adminReports(String questionId) {
        return reportsOf(questionId).thenCompose(reports -> {
            if (reports.isEmpty()) {
                return CompletableFuture.completedFuture(List.of());
            }
            List<String> reporterIds = reports.stream()
                    .map(QuestionReport::reporterId)
                    .distinct()
                    .toList();
            return profileLookup.getAll(reporterIds).thenApply(profiles -> {
                Map<String, String> pseudonyms = profiles.stream()
                        .collect(Collectors.toMap(
                                Profile::userId,
                                Profile::pseudonym,
                                (first, _) -> first));
                return reports.stream()
                        .map(report -> AdminQuestionReportView.of(
                                report, pseudonyms.get(report.reporterId())))
                        .toList();
            });
        });
    }

    public CompletableFuture<AdminQuestionReportResolutionView> resolveAdmin(String reportId,
                                                                             String adminId,
                                                                             ResolveQuestionReportsRequest request) {
        return requireReport(reportId)
                .thenCompose(report -> resolveOne(report, adminId, request));
    }

    public CompletableFuture<AdminQuestionReportResolutionView> resolveAdminAll(String questionId,
                                                                                String adminId,
                                                                                ResolveQuestionReportsRequest request) {
        return openReportsOf(questionId)
                .thenCompose(reports -> resolveAll(reports, adminId, request));
    }

    private CompletableFuture<AdminQuestionReportResolutionView> resolveOne(QuestionReport report,
                                                                            String reviewerId,
                                                                            ResolveQuestionReportsRequest request) {
        if (!report.open()) {
            return CompletableFuture.completedFuture(new AdminQuestionReportResolutionView(0));
        }
        return commandGateway.send(new QuestionReportCommand.ResolveQuestionReportCommand(
                        report.threadId(),
                        report.reportId(),
                        request.resolution(),
                        reviewerId,
                        request.note(),
                        Instant.now()))
                .thenApply(_ -> new AdminQuestionReportResolutionView(1));
    }

    private CompletableFuture<AdminQuestionReportResolutionView> resolveAll(List<QuestionReport> reports,
                                                                            String reviewerId,
                                                                            ResolveQuestionReportsRequest request) {
        if (reports.isEmpty()) {
            return CompletableFuture.completedFuture(new AdminQuestionReportResolutionView(0));
        }
        List<CompletableFuture<Void>> commands = reports.stream()
                .filter(QuestionReport::open)
                .map(report -> commandGateway.send(new QuestionReportCommand.ResolveQuestionReportCommand(
                                report.threadId(),
                                report.reportId(),
                                request.resolution(),
                                reviewerId,
                                request.note(),
                                Instant.now()))
                        .thenAccept(_ -> {
                        }))
                .toList();
        return CompletableFuture.allOf(commands.toArray(CompletableFuture[]::new))
                .thenApply(_ -> new AdminQuestionReportResolutionView(reports.size()));
    }

    private CompletableFuture<List<QuestionReport>> reportsOf(String questionId) {
        return queryGateway.query(
                new QuestionReportQuery.GetQuestionReportsByQuestionQuery(questionId),
                QueryResponseTypes.multipleInstancesOf(QuestionReport.class));
    }

    private CompletableFuture<List<QuestionReport>> openReportsOf(String questionId) {
        return reportsOf(questionId).thenApply(reports -> reports.stream()
                .filter(QuestionReport::open)
                .toList());
    }

    private CompletableFuture<Question> requireQuestion(String questionId) {
        return queryGateway.query(
                new QuestionQuery.GetQuestionByIdQuery(questionId),
                QueryResponseTypes.instanceOf(Question.class));
    }

    private CompletableFuture<QuestionReport> requireReport(String reportId) {
        return queryGateway.query(
                new QuestionReportQuery.GetQuestionReportByIdQuery(reportId),
                QueryResponseTypes.instanceOf(QuestionReport.class));
    }

    private CompletableFuture<Void> requireOwner(String topicId, String userId) {
        return queryGateway.query(
                        new TopicQuery.GetTopicByIdQuery(topicId),
                        QueryResponseTypes.instanceOf(Topic.class))
                .thenAccept(topic -> {
                    if (!Objects.equals(topic.creatorId(), userId)) {
                        throw new BffProblems.NotTopicOwnerProblem(topicId);
                    }
                });
    }

    private static void validateParticipation(Question question, Game game, String reporterId) {
        if (game.status() != GameStatus.FINISHED) {
            throw new BffProblems.QuestionReportNotAllowedProblem(
                    question.questionId(), game.gameId(), "La partie n'est pas terminée");
        }
        boolean participant = Objects.equals(reporterId, game.player1Id())
                || Objects.equals(reporterId, game.player2Id());
        if (!participant) {
            throw new BffProblems.QuestionReportNotAllowedProblem(
                    question.questionId(), game.gameId(),
                    "Le joueur n'a pas participé à cette partie");
        }
        boolean played = game.rounds() != null && game.rounds().stream()
                .anyMatch(round -> Objects.equals(question.questionId(), round.questionId()));
        if (!played || !Objects.equals(question.topicId(), game.topicId())) {
            throw new BffProblems.QuestionReportNotAllowedProblem(
                    question.questionId(), game.gameId(),
                    "La question n'a pas été jouée dans cette partie");
        }
    }

    private static QuestionReportSnapshot snapshotOf(List<EventEnvelope> envelopes,
                                                     Game game,
                                                     Question question,
                                                     Language language) {
        GameNotification.RoundStartedNotification round = envelopes.stream()
                .map(EventEnvelope::payload)
                .filter(GameEvent.class::isInstance)
                .map(GameEvent.class::cast)
                .map(GameEventNotificationMapper::toNotification)
                .flatMap(java.util.Optional::stream)
                .filter(GameNotification.RoundStartedNotification.class::isInstance)
                .map(GameNotification.RoundStartedNotification.class::cast)
                .filter(candidate -> Objects.equals(candidate.questionId(), question.questionId()))
                .findFirst()
                .orElse(null);
        if (round == null) {
            return QuestionReportSnapshot.current(question, language == null ? Language.FR : language);
        }

        Map<String, QuestionReportTranslation> translations = new LinkedHashMap<>();
        round.translations().forEach((code, content) -> translations.put(
                code, new QuestionReportTranslation(content.text(), content.answers())));
        String correctAnswer = game.rounds() == null
                ? null
                : game.rounds().stream()
                        .filter(candidate -> Objects.equals(candidate.questionId(), question.questionId()))
                        .map(GameRound::correctAnswer)
                        .filter(Objects::nonNull)
                        .map(Enum::name)
                        .findFirst()
                        .orElse(null);
        return new QuestionReportSnapshot(
                round.questionText(),
                round.imageUrl(),
                round.answers(),
                translations,
                correctAnswer,
                language == null ? null : language.code(),
                QuestionReportSnapshotSource.PLAYED);
    }

    private static String normalizeComment(String comment) {
        return comment == null || comment.isBlank() ? null : comment.trim();
    }
}
