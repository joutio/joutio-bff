package com.joutio.bff.application.notice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stockage des notifications de contenu (table {@code content_notice}). */
public interface ContentNoticeRepository {

    ContentNotice save(ContentNotice notice);

    Optional<ContentNotice> findById(UUID id);

    /** Notifications d'un statut ({@code null} : toutes), des plus récentes aux plus anciennes. */
    List<ContentNotice> findByStatus(ContentNotice.ContentNoticeStatus status, int page, int size);

    long countByStatus(ContentNotice.ContentNoticeStatus status);
}
