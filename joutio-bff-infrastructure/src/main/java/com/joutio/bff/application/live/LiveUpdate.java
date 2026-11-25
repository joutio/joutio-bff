package com.joutio.bff.application.live;

/**
 * Valeur d'une vue vivante versionnée, poussée sur {@code /topic/live/<view>/{key}} et lue par
 * {@code GET /api/live/{view}/{key}}. Le client ignore une {@code version} inférieure ou égale à celle qu'il détient.
 *
 * @param view    nom de la vue
 * @param key     clé de la vue (identifiant de l'agrégat, du joueur…)
 * @param version version monotone (séquence de l'agrégat)
 * @param payload valeur, {@code *View} du BFF propre à la vue
 * @param <T>     type du {@code *View}
 */
public record LiveUpdate<T>(String view, String key, long version, T payload) {
}
