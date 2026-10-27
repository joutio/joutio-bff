package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.theme.domain.model.TopicCategory;
import jakarta.validation.constraints.NotNull;

/** Mise à jour de la catégorie d'un sujet. */
public record UpdateTopicCategoryRequest(
        @NotNull TopicCategory category
) {
}
