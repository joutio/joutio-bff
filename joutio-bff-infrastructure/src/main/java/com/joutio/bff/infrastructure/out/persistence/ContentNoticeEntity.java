package com.joutio.bff.infrastructure.out.persistence;

import com.joutio.bff.application.notice.ContentNotice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Notification de contenu illicite (table {@code content_notice}). */
@Entity
@Table(name = "content_notice")
public class ContentNoticeEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ContentNotice.ContentNoticeStatus status;
    @Column(name = "notifier_name", nullable = false, length = 160)
    private String notifierName;
    @Column(name = "notifier_email", nullable = false, length = 254)
    private String notifierEmail;
    @Enumerated(EnumType.STRING)
    @Column(name = "notifier_quality", nullable = false, length = 16)
    private ContentNotice.NotifierQuality notifierQuality;
    @Column(name = "content_url", nullable = false, length = 1024)
    private String contentUrl;
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private ContentNotice.ContentNoticeCategory category;
    @Column(name = "description", nullable = false, length = 4000)
    private String description;
    @Column(name = "rights_evidence", length = 2000)
    private String rightsEvidence;
    @Column(name = "good_faith", nullable = false)
    private boolean goodFaith;
    @Column(name = "reporter_user_id", length = 64)
    private String reporterUserId;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(name = "decided_by", length = 64)
    private String decidedBy;
    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 32)
    private ContentNotice.ContentNoticeDecision decision;
    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    protected ContentNoticeEntity() {
    }

    static ContentNoticeEntity of(ContentNotice notice) {
        ContentNoticeEntity entity = new ContentNoticeEntity();
        entity.id = notice.id();
        entity.status = notice.status();
        entity.notifierName = notice.notifierName();
        entity.notifierEmail = notice.notifierEmail();
        entity.notifierQuality = notice.notifierQuality();
        entity.contentUrl = notice.contentUrl();
        entity.category = notice.category();
        entity.description = notice.description();
        entity.rightsEvidence = notice.rightsEvidence();
        entity.goodFaith = notice.goodFaith();
        entity.reporterUserId = notice.reporterUserId();
        entity.receivedAt = notice.receivedAt();
        entity.decidedAt = notice.decidedAt();
        entity.decidedBy = notice.decidedBy();
        entity.decision = notice.decision();
        entity.decisionReason = notice.decisionReason();
        return entity;
    }

    ContentNotice toDomain() {
        return new ContentNotice(id, status, notifierName, notifierEmail, notifierQuality, contentUrl, category, description,
                rightsEvidence, goodFaith, reporterUserId, receivedAt, decidedAt, decidedBy, decision, decisionReason);
    }
}
