package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.identity.domain.model.UserReportResolution;
import com.joutio.identity.domain.model.UserReportRules;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Clôture d'un ou plusieurs signalements de joueurs. */
public record ResolveUserReportsRequest(
        @NotNull UserReportResolution resolution,
        @Size(max = UserReportRules.MAX_NOTE_LENGTH) String note
) {
}
