package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.theme.domain.model.TopicStatus;

import java.util.Map;

/** Option du filtre « Sujet » et du formulaire de création admin. */
public record AdminTopicOptionView(
        String topicId,
        Map<Language, String> names,
        TopicStatus status,
        Integer questionsCount
) {
}
