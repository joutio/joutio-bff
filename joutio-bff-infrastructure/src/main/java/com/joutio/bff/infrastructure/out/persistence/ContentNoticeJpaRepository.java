package com.joutio.bff.infrastructure.out.persistence;

import com.joutio.bff.application.notice.ContentNotice;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContentNoticeJpaRepository extends JpaRepository<ContentNoticeEntity, UUID> {

    List<ContentNoticeEntity> findByStatusOrderByReceivedAtDesc(ContentNotice.ContentNoticeStatus status, Pageable pageable);

    List<ContentNoticeEntity> findAllByOrderByReceivedAtDesc(Pageable pageable);

    long countByStatus(ContentNotice.ContentNoticeStatus status);
}
