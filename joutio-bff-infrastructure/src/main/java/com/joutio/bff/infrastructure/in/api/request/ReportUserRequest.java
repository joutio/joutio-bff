package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.identity.domain.model.UserReportReason;
import com.joutio.identity.domain.model.UserReportRules;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Signalement d'un joueur par un autre joueur. */
public record ReportUserRequest(
        @NotEmpty Set<UserReportReason> reasons,
        @Size(max = UserReportRules.MAX_COMMENT_LENGTH) String comment,
        String gameId
) {
}
