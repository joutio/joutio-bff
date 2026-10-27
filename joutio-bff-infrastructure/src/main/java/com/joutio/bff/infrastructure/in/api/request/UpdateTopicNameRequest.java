package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.theme.domain.model.TopicRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Mise à jour d'un nom de sujet pour une langue (propriétaire uniquement). */
public record UpdateTopicNameRequest(
        @NotNull Language language,
        @NotBlank @Size(max = TopicRules.MAX_NAME_LENGTH) String name
) {
}
