package com.joutio.bff.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PushSubscriptionJpaRepository extends JpaRepository<PushSubscriptionEntity, String> {

    List<PushSubscriptionEntity> findByUserIdOrderByUpdatedAtDesc(String userId);

    long deleteByEndpointAndUserId(String endpoint, String userId);

    long deleteByUserId(String userId);
}
