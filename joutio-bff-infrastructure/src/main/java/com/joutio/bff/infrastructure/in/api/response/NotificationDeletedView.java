package com.joutio.bff.infrastructure.in.api.response;

/**
 * Payload du push WS de suppression d'une notification ({@code NOTIFICATION_DELETED}) : les
 * clients retirent la ligne de leur inbox et recalcule le compteur non lus.
 */
public record NotificationDeletedView(String notificationId) {
}
