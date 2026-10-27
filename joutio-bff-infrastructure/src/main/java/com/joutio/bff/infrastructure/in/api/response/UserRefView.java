package com.joutio.bff.infrastructure.in.api.response;

/**
 * Référence minimale d'un joueur (adversaire, participant) : identité + avatar.
 */
public record UserRefView(
        String userId,
        String pseudonym,
        String avatarOptions,
        boolean suspended
) {

    /** Référence d'un joueur non suspendu. */
    public UserRefView(String userId, String pseudonym, String avatarOptions) {
        this(userId, pseudonym, avatarOptions, false);
    }
}
