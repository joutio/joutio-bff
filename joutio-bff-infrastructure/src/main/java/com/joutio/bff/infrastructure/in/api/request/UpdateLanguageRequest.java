package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.microservice.core.domain.model.i18n.Language;
import jakarta.validation.constraints.NotNull;

/**
 * Corps de {@code PUT /api/profiles/{userId}/language}.
 */
public record UpdateLanguageRequest(
        @NotNull Language language
) {
}
