package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.theme.domain.model.QuestionReportSnapshot;

import java.util.List;
import java.util.Map;

/** Contenu figé d'une question au moment du signalement. */
public record QuestionReportSnapshotView(
        String questionText,
        String imageUrl,
        List<AnswerView> answers,
        List<ContentView> translations,
        String correctAnswer,
        String language,
        String source
) {

    public record AnswerView(String choice, String text) {
    }

    public record ContentView(Language language, String text, List<AnswerView> answers) {
    }

    public static QuestionReportSnapshotView of(QuestionReportSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        List<ContentView> translations = snapshot.translations().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ContentView(
                        Language.fromCode(entry.getKey()),
                        entry.getValue().text(),
                        answers(entry.getValue().answers())))
                .toList();
        return new QuestionReportSnapshotView(
                snapshot.questionText(),
                snapshot.imageUrl(),
                answers(snapshot.answers()),
                translations,
                snapshot.correctAnswer(),
                snapshot.language(),
                snapshot.source() == null ? null : snapshot.source().name());
    }

    private static List<AnswerView> answers(Map<String, String> answers) {
        if (answers == null) {
            return List.of();
        }
        return answers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new AnswerView(entry.getKey(), entry.getValue()))
                .toList();
    }
}
