package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.bff.application.notice.ContentNotice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Décision motivée d'une notification ({@code POST /api/admin/content-notices/{id}/decision}). */
public record DecideContentNoticeRequest(
        @NotNull ContentNotice.ContentNoticeDecision decision, @NotBlank @Size(max = 1000) String reason) {
}
