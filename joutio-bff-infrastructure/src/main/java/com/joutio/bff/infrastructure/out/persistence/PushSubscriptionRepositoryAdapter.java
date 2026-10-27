package com.joutio.bff.infrastructure.out.persistence;

import com.joutio.bff.application.PushSubscription;
import com.joutio.bff.application.PushSubscriptionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Adaptateur JPA des abonnements Web Push. L'endpoint est la clé naturelle (upsert au rebind). */
@Component
public class PushSubscriptionRepositoryAdapter implements PushSubscriptionRepository {

    private final PushSubscriptionJpaRepository repository;

    public PushSubscriptionRepositoryAdapter(PushSubscriptionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void save(PushSubscription subscription) {
        PushSubscriptionEntity entity = repository.findById(subscription.endpoint())
                .orElseGet(() -> new PushSubscriptionEntity(
                        subscription.endpoint(),
                        subscription.userId(),
                        subscription.p256dh(),
                        subscription.auth(),
                        subscription.userAgent(),
                        subscription.createdAt(),
                        subscription.updatedAt()));
        entity.setUserId(subscription.userId());
        entity.setP256dh(subscription.p256dh());
        entity.setAuth(subscription.auth());
        entity.setUserAgent(subscription.userAgent());
        entity.setUpdatedAt(subscription.updatedAt());
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PushSubscription> find(String endpoint) {
        return repository.findById(endpoint).map(PushSubscriptionRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional
    public void deleteByEndpointAndUserId(String endpoint, String userId) {
        repository.deleteByEndpointAndUserId(endpoint, userId);
    }

    @Override
    @Transactional
    public long deleteByUserId(String userId) {
        return repository.deleteByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PushSubscription> findByUserId(String userId) {
        return repository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(PushSubscriptionRepositoryAdapter::toDomain)
                .toList();
    }

    private static PushSubscription toDomain(PushSubscriptionEntity entity) {
        return new PushSubscription(
                entity.getEndpoint(),
                entity.getUserId(),
                entity.getP256dh(),
                entity.getAuth(),
                entity.getUserAgent(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
