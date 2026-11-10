package com.joutio.bff.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.joutio.bff.infrastructure.in.api.response.NotificationView;
import com.joutio.bff.infrastructure.in.api.response.ReportedQuestionView;
import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.notification.domain.model.Notification;
import com.joutio.notification.domain.model.NotificationType;
import com.joutio.theme.domain.model.Question;
import com.joutio.theme.domain.model.QuestionChoice;
import com.joutio.theme.domain.model.QuestionContent;
import com.joutio.theme.domain.model.QuestionReport;
import com.joutio.theme.domain.model.QuestionReportReason;
import com.joutio.theme.domain.model.QuestionReportStatus;
import com.joutio.theme.domain.model.QuestionStatus;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** File des signalements d'un sujet : ouverts seulement, du plus récent au plus ancien, historique résumé. */
class QuestionReportQueueTest {

    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");

    @Test
    void entry_keeps_open_reports_newest_first_and_summarises_closed_ones() {
        ReportedQuestionView entry =
                QuestionReportService.queueEntry(
                        question(),
                        List.of(
                                report("old-open", QuestionReportStatus.OPEN, T0, null),
                                report("new-open", QuestionReportStatus.OPEN, T0.plusSeconds(3600), null),
                                report("fixed", QuestionReportStatus.RESOLVED, T0.minusSeconds(86400), T0.minusSeconds(3600)),
                                report("dismissed", QuestionReportStatus.DISMISSED, T0.minusSeconds(172800), T0.minusSeconds(7200))));

        assertThat(entry.reports()).extracting(view -> view.reportId()).containsExactly("new-open", "old-open");
        assertThat(entry.question().openReportCount()).isEqualTo(2);
        assertThat(entry.question().totalReportCount()).isEqualTo(4);
        assertThat(entry.history().closedCount()).isEqualTo(2);
        assertThat(entry.history().lastStatus()).isEqualTo(QuestionReportStatus.RESOLVED);
        assertThat(entry.history().lastReviewedAt()).isEqualTo(T0.minusSeconds(3600));
    }

    @Test
    void entry_without_closed_reports_has_no_history() {
        ReportedQuestionView entry =
                QuestionReportService.queueEntry(question(), List.of(report("r", QuestionReportStatus.OPEN, T0, null)));

        assertThat(entry.history()).isNull();
    }

    @Test
    void report_notification_never_exposes_the_reporter() {
        Notification notification =
                Notification.builder()
                        .notificationId("n1")
                        .type(NotificationType.QUESTION_REPORTED)
                        .actorId("reporter-1")
                        .sourceId("report-1")
                        .topicId("topic-1")
                        .createdAt(T0)
                        .build();

        NotificationView view = NotificationView.of(notification, true);

        assertThat(view.actorId()).isNull();
        assertThat(view.actorSuspended()).isFalse();
    }

    private static Question question() {
        return Question.builder()
                .questionId("q1")
                .topicId("topic-1")
                .contents(
                        Map.of(
                                Language.FR,
                                new QuestionContent(
                                        Language.FR,
                                        "En quelle année meurt Louis XIV ?",
                                        Map.of(QuestionChoice.A, "1715", QuestionChoice.B, "1714", QuestionChoice.C, "1789", QuestionChoice.D, "1815"))))
                .correctAnswer(QuestionChoice.A)
                .status(QuestionStatus.APPROVED)
                .creatorId("creator-1")
                .createdAt(T0)
                .updatedAt(T0)
                .build();
    }

    private static QuestionReport report(String id, QuestionReportStatus status, Instant createdAt, Instant reviewedAt) {
        return QuestionReport.builder()
                .reportId(id)
                .threadId("thread-" + id)
                .questionId("q1")
                .topicId("topic-1")
                .reporterId("reporter-" + id)
                .reasons(Set.of(QuestionReportReason.ANSWERS))
                .language(Language.FR)
                .status(status)
                .createdAt(createdAt)
                .reviewedAt(reviewedAt)
                .build();
    }
}
