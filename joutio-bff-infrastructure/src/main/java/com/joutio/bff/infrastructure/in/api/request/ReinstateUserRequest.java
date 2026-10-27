package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.identity.domain.model.UserReportRules;
import jakarta.validation.constraints.Size;

/** Réintégration manuelle d'un compte suspendu. */
public record ReinstateUserRequest(
        @Size(max = UserReportRules.MAX_NOTE_LENGTH) String note
) {
}
