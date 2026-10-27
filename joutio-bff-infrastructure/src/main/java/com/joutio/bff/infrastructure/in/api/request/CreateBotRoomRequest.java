package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.game.domain.model.BotDifficulty;
import jakarta.validation.constraints.NotBlank;

/**
 * Création d'une salle contre le bot (difficulté optionnelle, défaut NORMAL). La salle est
 * l'unique point d'entrée de préparation d'une partie : le bot la rejoint automatiquement,
 * le joueur y apparaît via {@code POST /api/rooms/{roomId}/join}.
 */
public record CreateBotRoomRequest(
        @NotBlank String topicId,
        BotDifficulty difficulty
) {
}
