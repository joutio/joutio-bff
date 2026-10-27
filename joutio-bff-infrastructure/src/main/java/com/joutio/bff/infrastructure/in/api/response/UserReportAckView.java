package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.identity.domain.model.UserReportStatus;

import java.time.Instant;

public record UserReportAckView(
        String reportId,
        UserReportStatus status,
        Instant createdAt,
        boolean created
) {
}
