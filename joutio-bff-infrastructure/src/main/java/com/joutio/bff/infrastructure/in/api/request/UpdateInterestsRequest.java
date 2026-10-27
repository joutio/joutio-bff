package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.theme.domain.model.TopicCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Corps de {@code PUT /api/me/interests} : codes de catégories ordonnés (premier = univers
 * principal), au maximum 3. Une liste vide efface les centres d'intérêt.
 */
public record UpdateInterestsRequest(
        @NotNull @Size(max = 3) List<@NotNull TopicCategory> interests
) {
}
