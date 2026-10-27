package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.theme.domain.model.QuestionChoice;
import com.joutio.theme.domain.model.QuestionDifficulty;
import com.joutio.theme.domain.model.QuestionStatus;
import com.joutio.theme.domain.model.QuestionSuspensionReason;

import java.time.Instant;
import java.util.List;

/**
 * Question vue par son auteur : contenus localisés (texte + réponses), bonne réponse, statut de
 * modération, flags de suspension/archivage et compteurs de signalements.
 */
public record QuestionEditorView(
        String questionId,
        List<ContentView> contents,
        QuestionChoice correctAnswer,
        String imageUrl,
        QuestionStatus status,
        QuestionDifficulty difficulty,
        boolean suspended,
        QuestionSuspensionReason suspensionReason,
        String suspensionNote,
        Instant suspendedAt,
        Instant reinstatedAt,
        boolean archived,
        String archiveNote,
        Instant archivedAt,
        Instant restoredAt,
        int openReportCount,
        int totalReportCount,
        Instant lastReportedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public record ContentView(
            Language language,
            String text,
            List<AnswerView> answers
    ) {
    }

    public record AnswerView(
            QuestionChoice choice,
            String text
    ) {
    }
}
