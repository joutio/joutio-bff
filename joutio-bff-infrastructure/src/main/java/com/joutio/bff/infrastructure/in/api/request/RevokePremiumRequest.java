package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.identity.domain.model.UserReportRules;
import jakarta.validation.constraints.Size;

/** Retrait manuel du droit Premium. */
public record RevokePremiumRequest(
        @Size(max = UserReportRules.MAX_NOTE_LENGTH) String note
) {
}
