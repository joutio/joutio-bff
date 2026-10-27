package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.response.DuelStatsView;
import com.joutio.bff.infrastructure.in.api.response.ProgressionView;
import com.joutio.profile.domain.model.PlayerProgress;

import java.util.Comparator;

/**
 * Conversion progression → vues web : badges et Elo.
 */
public final class ProgressionViews {

    private ProgressionViews() {
    }

    public static ProgressionView toView(PlayerProgress progress) {
        return new ProgressionView(
                progress.badges().stream()
                        .sorted(Comparator.comparing(Enum::name))
                        .map(badge -> new ProgressionView.BadgeView(
                                badge.name(),
                                badge.label(),
                                badge.family().name(),
                                badge.tier().name()))
                        .toList(),
                progress.rating(),
                progress.peakRating(),
                progress.ratedGames(),
                progress.provisional());
    }

    public static DuelStatsView toStats(PlayerProgress progress) {
        int played = progress.gamesPlayed();
        int wins = progress.wins();
        int losses = progress.losses();
        int draws = progress.draws();
        int winPercent = played == 0 ? 0 : (int) Math.round(wins * 100.0 / played);
        return new DuelStatsView(
                played,
                wins,
                losses,
                draws,
                winPercent,
                progress.bestScore(),
                progress.currentWinStreak(),
                progress.bestWinStreak());
    }
}
