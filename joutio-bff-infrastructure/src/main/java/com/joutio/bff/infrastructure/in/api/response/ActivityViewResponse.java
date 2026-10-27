package com.joutio.bff.infrastructure.in.api.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Activité journalière d'un joueur pour le calendrier de série : série courante (calculée à la
 * date de la requête), record historique et jours actifs sur la fenêtre demandée.
 */
public record ActivityViewResponse(
        String userId,
        int currentStreak,
        int longestStreak,
        LocalDate lastActiveDate,
        int totalActiveDays,
        List<Day> days
) {
    public record Day(LocalDate date, int games) {
    }
}
