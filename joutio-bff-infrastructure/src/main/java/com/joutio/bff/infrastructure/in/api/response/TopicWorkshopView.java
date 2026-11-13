package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.theme.domain.model.TopicPermissions;
import java.util.Map;

/**
 * Atelier d'un sujet : carte, permissions de l'appelant et titres par palier saisis par le
 * créateur ({@code langue → palier → libellé}, vide quand tout tombe sur le repli « Or · Sujet »).
 */
public record TopicWorkshopView(
        TopicCardView topic,
        TopicPermissions permissions,
        Map<Language, Map<String, String>> titles) {}
