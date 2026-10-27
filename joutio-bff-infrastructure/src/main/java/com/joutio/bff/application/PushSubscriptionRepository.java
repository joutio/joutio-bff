package com.joutio.bff.application;

import java.util.List;
import java.util.Optional;

/** Port de persistance des abonnements Web Push (adapté par l'infrastructure JPA). */
public interface PushSubscriptionRepository {

    /** Upsert par endpoint : rebinde l'abonnement sur le joueur courant. */
    void save(PushSubscription subscription);

    Optional<PushSubscription> find(String endpoint);

    /** Supprime l'abonnement s'il appartient au joueur (pas de fuite inter-comptes). */
    void deleteByEndpointAndUserId(String endpoint, String userId);

    /** Abonnements d'un joueur, du plus récent au plus ancien. */
    List<PushSubscription> findByUserId(String userId);

    /** Supprime tous les abonnements d'un compte supprimé. */
    long deleteByUserId(String userId);
}
