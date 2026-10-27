package com.joutio.bff.infrastructure.in.api.response;

import java.util.List;

/** Détail admin : question + tous ses signalements. */
public record AdminQuestionDetailView(
        AdminQuestionView question,
        List<AdminQuestionReportView> reports
) {
}
