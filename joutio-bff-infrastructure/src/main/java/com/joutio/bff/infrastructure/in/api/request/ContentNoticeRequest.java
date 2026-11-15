package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.bff.application.notice.ContentNotice;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code POST /api/public/content-notices} : mentions de la notification (LCEN art. 6-I-5, DSA art. 16).
 * {@code website} est un champ piège, invisible pour un humain.
 */
public record ContentNoticeRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull ContentNotice.NotifierQuality quality,
        @NotBlank @Size(max = 1024) String contentUrl,
        @NotNull ContentNotice.ContentNoticeCategory category,
        @NotBlank @Size(min = 20, max = 4000) String description,
        @Size(max = 2000) String rightsEvidence,
        boolean goodFaith,
        @Size(max = 200) String website) {
}
