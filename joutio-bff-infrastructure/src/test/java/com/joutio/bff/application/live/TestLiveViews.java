package com.joutio.bff.application.live;

import com.joutio.microservice.core.domain.model.event.EventEnvelope;

/** Vues vivantes de test : une valeur personnelle versionnée et un flux d'agrégat public. */
public final class TestLiveViews {

    private TestLiveViews() {
    }

    public record GetScoreQuery(String userId) {
    }

    /** Valeur du service propriétaire, versionnée par la séquence de son agrégat. */
    public record Score(String userId, long version, int points) {
    }

    /** DTO du BFF exposé aux joueurs. */
    public record ScoreView(int points) {
    }

    public record GetMatchEventsQuery(String matchId) {
    }

    /** {@code /topic/live/score/{userId}}, réservée au joueur lui-même. */
    public static final class ScoreLiveView extends VersionedLiveView<Score, ScoreView> {

        public ScoreLiveView() {
            super("score", Score.class);
        }

        @Override
        public boolean canSubscribe(String userId, String key) {
            return key.equals(userId);
        }

        @Override
        public Object query(String key) {
            return new GetScoreQuery(key);
        }

        @Override
        public long version(Score score) {
            return score.version();
        }

        @Override
        protected ScoreView payload(Score score) {
            return new ScoreView(score.points());
        }
    }

    /** {@code /topic/matches/{matchId}}, publique ; message = type de l'événement. */
    public static final class MatchStreamLiveView extends AggregateStreamLiveView {

        public MatchStreamLiveView() {
            super("/topic/matches/");
        }

        @Override
        public boolean canSubscribe(String userId, String key) {
            return true;
        }

        @Override
        public Object query(String key) {
            return new GetMatchEventsQuery(key);
        }

        @Override
        public Object message(String key, EventEnvelope event) {
            return event.eventType();
        }
    }
}
