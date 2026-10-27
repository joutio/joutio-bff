package com.joutio.bff.application;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** Abonnements Web Push du joueur : enregistrement (upsert), désabonnement, lecture. */
@Service
public class PushSubscriptionService {

    private final PushSubscriptionRepository repository;

    public PushSubscriptionService(PushSubscriptionRepository repository) {
        this.repository = repository;
    }

    public void register(String userId,
                         String endpoint,
                         String p256dh,
                         String auth,
                         String userAgent) {
        Instant now = Instant.now();
        Instant createdAt = repository.find(endpoint)
                .map(PushSubscription::createdAt)
                .orElse(now);
        repository.save(new PushSubscription(endpoint, userId, p256dh, auth, userAgent, createdAt, now));
    }

    public void unregister(String userId, String endpoint) {
        repository.deleteByEndpointAndUserId(endpoint, userId);
    }

    public List<PushSubscription> subscriptionsOf(String userId) {
        return repository.findByUserId(userId);
    }
}
