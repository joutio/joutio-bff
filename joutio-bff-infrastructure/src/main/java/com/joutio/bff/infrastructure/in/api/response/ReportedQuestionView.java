package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.theme.domain.model.QuestionReportStatus;

import java.time.Instant;
import java.util.List;

/**
 * Une question de la file des signalements d'un sujet (écran Signalements de l'atelier) : la question
 * actuelle, ses signalements <b>ouverts</b> (anonymes, avec le contenu signalé) et l'historique résumé des
 * signalements clos — jamais leur liste, qui n'apporte rien au tri.
 */
public record ReportedQuestionView(
        QuestionEditorView question,
        List<OwnerQuestionReportView> reports,
        History history) {

    /** Signalements clos de la question : nombre, dernière issue et sa date ; {@code null} s'il n'y en a pas. */
    public record History(int closedCount, QuestionReportStatus lastStatus, Instant lastReviewedAt) {}
}
