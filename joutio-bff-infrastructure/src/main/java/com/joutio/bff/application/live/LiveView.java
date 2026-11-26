package com.joutio.bff.application.live;

import org.axonframework.messaging.responsetypes.ResponseType;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Une vue vivante relayée aux clients en STOMP : une lecture d'un service dont la nouvelle valeur est poussée
 * ({@code subscriptionQuery} côté BFF, {@code QueryUpdateEmitter} côté projection).
 *
 * <p>Deux formes, une seule mécanique (le pont {@code LiveViewSubscriptions}) :</p>
 * <ul>
 *   <li>{@link VersionedLiveView} : une valeur versionnée, poussée en {@link LiveUpdate} sur
 *   {@code /topic/live/<vue>/{clé}} ;</li>
 *   <li>{@link AggregateStreamLiveView} : le flux d'un agrégat, l'historique en résultat initial puis chaque
 *   événement, poussé dans le contrat existant de sa destination.</li>
 * </ul>
 *
 * <p>Chaque update porte une version monotone : le pont ne relaie que les versions supérieures à la dernière
 * relayée, et comble avec le résultat initial après la réouverture d'un abonnement perdu.</p>
 *
 * @param <I> résultat initial de la requête
 * @param <U> update poussé par la projection
 */
public interface LiveView<I, U> {

    /** Préfixe de la destination STOMP, terminé par {@code /} ; la clé le suit, sans {@code /}. */
    String destinationPrefix();

    /** Règle d'accès, évaluée avant l'abonnement : <b>locale, sans I/O</b> (vue publique, clé = joueur…). */
    boolean canSubscribe(String userId, String key);

    /** Requête envoyée au service propriétaire pour cette clé (abonnement et lecture). */
    Object query(String key);

    ResponseType<I> initialResponseType();

    ResponseType<U> updateResponseType();

    /** Les updates contenus dans un résultat initial, par version croissante ; vide si aucune valeur. */
    List<U> updatesOf(I initial);

    /** Version monotone d'un update (séquence de l'agrégat). */
    long version(U update);

    /**
     * Message STOMP d'un update (DTO du BFF), ou {@code null} pour ne rien pousser. Asynchrone, car un message peut
     * s'enrichir d'autres lectures (profils de l'adversaire) ; le pont envoie les messages dans l'ordre des versions.
     */
    CompletableFuture<?> message(String key, U update);
}
