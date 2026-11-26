package com.joutio.bff.application.live;

import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import org.axonframework.messaging.responsetypes.ResponseType;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Vue vivante d'une valeur versionnée (résultat de duel, profil, compte…), relayée en {@link LiveUpdate} sur
 * {@code /topic/live/<name>/{clé}} et lue par {@code GET /api/live/<name>/{clé}}.
 *
 * <p>Le {@code @QueryHandler} du service propriétaire renvoie la valeur, ou {@code null} si elle n'existe pas encore ;
 * la projection émet la même valeur à chaque changement.</p>
 *
 * @param <V> valeur du service propriétaire, qui porte sa version
 * @param <T> {@code *View} du BFF exposé aux joueurs
 */
public abstract class VersionedLiveView<V, T> implements LiveView<V, V> {

    public static final String DESTINATION_PREFIX = "/topic/live/";

    private final String name;
    private final ResponseType<V> responseType;

    protected VersionedLiveView(String name, Class<V> type) {
        this.name = name;
        this.responseType = QueryResponseTypes.instanceOf(type);
    }

    public final String name() {
        return name;
    }

    @Override
    public final String destinationPrefix() {
        return DESTINATION_PREFIX + name + "/";
    }

    @Override
    public final ResponseType<V> initialResponseType() {
        return responseType;
    }

    @Override
    public final ResponseType<V> updateResponseType() {
        return responseType;
    }

    @Override
    public final List<V> updatesOf(V initial) {
        return initial == null ? List.of() : List.of(initial);
    }

    @Override
    public final CompletableFuture<LiveUpdate<T>> message(String key, V value) {
        long version = version(value);
        return payload(key, value).thenApply(payload -> new LiveUpdate<>(name, key, version, payload));
    }

    /** {@code *View} du BFF exposé aux joueurs pour cette valeur, vue par la clé (le joueur, s'il en fait partie). */
    protected abstract CompletableFuture<T> payload(String key, V value);
}
