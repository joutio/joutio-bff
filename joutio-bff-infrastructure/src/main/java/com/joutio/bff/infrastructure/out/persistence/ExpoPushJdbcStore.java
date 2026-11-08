package com.joutio.bff.infrastructure.out.persistence;

import com.joutio.bff.application.ExpoPushStore;
import com.joutio.notification.domain.event.NotificationEvent.NotificationCreatedEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class ExpoPushJdbcStore implements ExpoPushStore {
    private final JdbcTemplate jdbc;
    public ExpoPushJdbcStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional
    public void register(String user, String installation, String token, Platform platform, boolean rematches) {
        // Sérialise rotation/réaffectation, y compris deux requêtes concurrentes pour le même token.
        jdbc.execute("SELECT pg_advisory_xact_lock(785314206)");
        jdbc.update("DELETE FROM expo_push_subscription WHERE token = ? AND installation_id <> ?", token, installation);
        jdbc.update("""
            INSERT INTO expo_push_subscription(installation_id,user_id,token,platform,rematches)
            VALUES (?,?,?,?,?) ON CONFLICT (installation_id) DO UPDATE SET
            registered_at = CASE WHEN expo_push_subscription.user_id <> EXCLUDED.user_id
                OR expo_push_subscription.token <> EXCLUDED.token THEN now() ELSE expo_push_subscription.registered_at END,
            user_id=EXCLUDED.user_id, token=EXCLUDED.token, platform=EXCLUDED.platform,
            rematches=EXCLUDED.rematches, updated_at=now()
            """, installation, user, token, platform.name(), rematches);
    }
    @Override public void unregister(String user, String installation) {
        jdbc.update("DELETE FROM expo_push_subscription WHERE installation_id=? AND user_id=?", installation, user);
    }
    @Override @Transactional public void deleteUser(String user) {
        jdbc.update("DELETE FROM expo_push_subscription WHERE user_id=?", user);
        jdbc.update("DELETE FROM expo_push_delivery WHERE user_id=?", user);
    }
    @Override public void enqueue(NotificationCreatedEvent e) {
        Instant expires = e.expiresAt() == null ? e.createdAt().plusSeconds(3600) : e.expiresAt();
        jdbc.update("""
            INSERT INTO expo_push_delivery(id,notification_id,installation_id,user_id,token,type,source_id,game_id,expires_at)
            SELECT md5(? || ':' || s.installation_id), ?, s.installation_id, s.user_id, s.token, ?, ?, ?, ?
            FROM expo_push_subscription s WHERE s.user_id=? AND s.registered_at <= ?
            AND (CAST(? AS varchar) IS NULL OR s.rematches) ON CONFLICT (notification_id,installation_id) DO NOTHING
            """, e.notificationId(), e.notificationId(), e.type().name(), e.sourceId(), e.gameId(),
                Timestamp.from(expires), e.userId(), Timestamp.from(e.createdAt()), e.gameId());
    }
    @Override public Optional<Delivery> claim(Instant now) {
        return jdbc.query("""
            UPDATE expo_push_delivery SET next_at=?, attempts=attempts+1 WHERE id=(
                SELECT id FROM expo_push_delivery WHERE done=false AND next_at<=?
                ORDER BY next_at FOR UPDATE SKIP LOCKED LIMIT 1
            ) RETURNING *
            """, (rs, row) -> new Delivery(rs.getString("id"), rs.getString("installation_id"), rs.getString("token"),
                new Notice(rs.getString("notification_id"),rs.getString("user_id"),rs.getString("type"),
                    rs.getString("source_id"),rs.getString("game_id"),rs.getTimestamp("expires_at").toInstant()),
                rs.getString("ticket"),rs.getInt("attempts")), Timestamp.from(now.plusSeconds(60)), Timestamp.from(now))
                .stream().findFirst();
    }
    @Override public boolean eligible(Delivery d) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM expo_push_subscription WHERE installation_id=? AND user_id=? AND token=?
                AND (CAST(? AS varchar) IS NULL OR rematches)
                AND registered_at <= (SELECT created_at FROM expo_push_delivery WHERE id=?))
            """, Boolean.class, d.installationId(), d.notice().userId(), d.token(), d.notice().gameId(), d.id()));
    }
    @Override public void finish(String id) { jdbc.update("UPDATE expo_push_delivery SET done=true WHERE id=?", id); }
    @Override public void retry(String id, Instant next) {
        jdbc.update("UPDATE expo_push_delivery SET next_at=? WHERE id=?", Timestamp.from(next), id);
    }
    @Override public void ticket(String id, String ticket, Instant at) {
        jdbc.update("UPDATE expo_push_delivery SET ticket=?,attempts=0,next_at=? WHERE id=?", ticket, Timestamp.from(at), id);
    }
    @Override public void disableToken(String token) { jdbc.update("DELETE FROM expo_push_subscription WHERE token=?", token); }
    @Override public void cancel(String notification) {
        jdbc.update("UPDATE expo_push_delivery SET done=true WHERE notification_id=? AND ticket IS NULL", notification);
    }
    @Override public void purge(Instant before) {
        jdbc.update("DELETE FROM expo_push_delivery WHERE created_at < ?", Timestamp.from(before));
    }
}
