package com.joutio.bff.infrastructure.in.api.request;

import jakarta.validation.constraints.Size;

/** Rejet d'une question par le propriétaire du sujet (motif optionnel). */
public record RejectQuestionRequest(
        @Size(max = 500) String reason
) {
}
