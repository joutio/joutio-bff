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
        Map<Language, Map<String, String>> titles,
        /**
         * Motif de fermeture par la modération (2026-11-15, DSA art. 17), affiché à l'équipe du sujet ; {@code null} si
         * le sujet n'est pas fermé. Tant qu'il est fermé, la publication est refusée ({@code urn:joutio:topic:closed}).
         */
        String closedReason) {}
