package com.joutio.bff.infrastructure.in.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Création d'une salle (salle d'attente) sur un sujet. Le lien de partage est composé
 * côté client à partir de l'identifiant de la salle : {@code /join/{roomId}}.
 * <p>
 * Le défi nominatif ne passe pas par cette route : il se crée via {@code POST /api/challenges}.
 */
public record CreateRoomRequest(
        @NotBlank String topicId
) {
}
