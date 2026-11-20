package com.joutio.bff.infrastructure.in.api.response;

/**
 * Référence minimale d'un joueur (adversaire, participant) : identité + avatar.
 */
public record UserRefView(
        String userId,
        String pseudonym,
        String avatarOptions,
        boolean suspended,
        /** Premium accordé (badge à droite du pseudonyme). */
        boolean premium
) {

    /** Référence d'un joueur non suspendu, sans Premium. */
    public UserRefView(String userId, String pseudonym, String avatarOptions) {
        this(userId, pseudonym, avatarOptions, false, false);
    }
}
