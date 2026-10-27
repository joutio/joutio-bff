package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.profile.domain.model.ProfileRules;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code PUT /api/profiles/{userId}/title}. {@code null} (ou absent) efface le titre
 * porté ; sinon une clé stable {@code topicId:TIER}, telle que retournée par
 * {@code GET /api/profiles/{userId}/titles}.
 */
public record UpdateTitleRequest(
        @Size(max = ProfileRules.MAX_EQUIPPED_TITLE_LENGTH) String titleKey
) {
}
