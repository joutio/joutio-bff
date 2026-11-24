package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.matchmaking.domain.model.RoomOrigin;

/** Qui peut devenir le second joueur d'une salle : un joueur nommé, ou quiconque ouvre le lien. */
public enum RoomAccess {
    NOMINATIVE,
    OPEN_LINK;

    public static RoomAccess of(RoomOrigin origin) {
        return origin == RoomOrigin.SHARED ? OPEN_LINK : NOMINATIVE;
    }
}
