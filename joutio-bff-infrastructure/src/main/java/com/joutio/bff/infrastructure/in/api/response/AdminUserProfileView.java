package com.joutio.bff.infrastructure.in.api.response;

/** Profil public (mutable) d'un joueur vu par l'administration. */
public record AdminUserProfileView(
        String pseudonym,
        String bio,
        String country,
        String avatarOptions,
        String bannerUrl
) {
}
