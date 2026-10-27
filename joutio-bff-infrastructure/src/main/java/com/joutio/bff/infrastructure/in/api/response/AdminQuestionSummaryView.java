package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.theme.domain.model.QuestionDifficulty;
import com.joutio.theme.domain.model.QuestionStatus;

import java.time.Instant;
import java.util.Map;

/** Ligne de la file des questions signalées (groupée par question). */
public record AdminQuestionSummaryView(
        String questionId,
        TopicRefView topic,
        Map<Language, String> texts,
        String imageUrl,
        QuestionStatus status,
        QuestionDifficulty difficulty,
        boolean suspended,
        boolean archived,
        int openReportCount,
        int totalReportCount,
        Instant lastReportedAt
) {
}
