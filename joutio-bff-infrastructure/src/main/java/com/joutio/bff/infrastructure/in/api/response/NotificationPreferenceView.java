package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.notification.domain.model.NotificationCategory;

/** Préférence d'une catégorie de notification pour le joueur courant. */
public record NotificationPreferenceView(
        NotificationCategory category,
        boolean enabled
) {
}
