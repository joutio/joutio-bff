package com.joutio.bff.application.notice;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification de contenu illicite (LCEN art. 6-I-5, DSA art. 16), reçue par le formulaire public de la landing ou de
 * l'app, puis traitée par la modération (2026-11-15). Conservée avec sa décision : preuve de la diligence de Joutio
 * comme hébergeur.
 */
public record ContentNotice(
        UUID id,
        ContentNoticeStatus status,
        String notifierName,
        String notifierEmail,
        NotifierQuality notifierQuality,
        String contentUrl,
        ContentNoticeCategory category,
        String description,
        String rightsEvidence,
        boolean goodFaith,
        /** Joueur connecté qui notifie depuis l'app, sinon {@code null}. */
        String reporterUserId,
        Instant receivedAt,
        Instant decidedAt,
        String decidedBy,
        ContentNoticeDecision decision,
        String decisionReason) {

    public enum ContentNoticeStatus { RECEIVED, ACTIONED, REJECTED }

    /** Qualité du notifiant : titulaire des droits, son représentant, ou tiers (contenu illicite d'une autre nature). */
    public enum NotifierQuality { RIGHTS_HOLDER, REPRESENTATIVE, THIRD_PARTY }

    public enum ContentNoticeCategory { COPYRIGHT, IMAGE_RIGHTS, IMPERSONATION, OTHER_ILLEGAL }

    /** Suite donnée : contenu retiré (image, question), sujet fermé, ou notification rejetée. */
    public enum ContentNoticeDecision { IMAGE_REMOVED, QUESTION_REMOVED, TOPIC_CLOSED, REJECTED }

    public ContentNotice decide(ContentNoticeDecision decision, String reason, String adminId, Instant at) {
        ContentNoticeStatus next = decision == ContentNoticeDecision.REJECTED ? ContentNoticeStatus.REJECTED : ContentNoticeStatus.ACTIONED;
        return new ContentNotice(id, next, notifierName, notifierEmail, notifierQuality, contentUrl, category, description,
                rightsEvidence, goodFaith, reporterUserId, receivedAt, at, adminId, decision, reason);
    }
}
