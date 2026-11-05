package com.joutio.bff.infrastructure.in.api.response;

/**
 * Adversaire d'une salle : identité, avatar, état de compte et données de joueur nécessaires à
 * l'écran de recherche (« Or · 867 · France · Le duel commence »). {@code rating} et
 * {@code provisional} sont nuls si la progression est momentanément indisponible ; {@code country}
 * est nul si le joueur n'en a pas renseigné.
 */
public record RoomOpponentView(
        String userId,
        String pseudonym,
        String avatarOptions,
        boolean suspended,
        Integer rating,
        Boolean provisional,
        String country
) {

    /** Référence minimale (bot ou repli) : aucune donnée de progression. */
    public RoomOpponentView(String userId, String pseudonym, String avatarOptions, boolean suspended) {
        this(userId, pseudonym, avatarOptions, suspended, null, null, null);
    }
}
