package com.joutio.bff.infrastructure.out.persistence;

import com.joutio.bff.application.notice.ContentNotice;
import com.joutio.bff.application.notice.ContentNoticeRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ContentNoticeRepositoryAdapter implements ContentNoticeRepository {

    private final ContentNoticeJpaRepository repository;

    public ContentNoticeRepositoryAdapter(ContentNoticeJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public ContentNotice save(ContentNotice notice) {
        repository.save(ContentNoticeEntity.of(notice));
        return notice;
    }

    @Override
    public Optional<ContentNotice> findById(UUID id) {
        return repository.findById(id).map(ContentNoticeEntity::toDomain);
    }

    @Override
    public List<ContentNotice> findByStatus(ContentNotice.ContentNoticeStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return (status == null
                        ? repository.findAllByOrderByReceivedAtDesc(pageable)
                        : repository.findByStatusOrderByReceivedAtDesc(status, pageable))
                .stream()
                .map(ContentNoticeEntity::toDomain)
                .toList();
    }

    @Override
    public long countByStatus(ContentNotice.ContentNoticeStatus status) {
        return status == null ? repository.count() : repository.countByStatus(status);
    }
}
