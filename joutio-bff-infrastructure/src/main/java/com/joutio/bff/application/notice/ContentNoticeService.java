package com.joutio.bff.application.notice;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Notifications de contenu illicite (2026-11-15) : réception publique, file de la console, décision motivée.
 * Garde-fous du formulaire public : champ piège (robots) et 5 notifications par heure et par adresse IP.
 */
@Service
public class ContentNoticeService {

    private static final Logger logger = LoggerFactory.getLogger(ContentNoticeService.class);
    static final int MAX_PER_HOUR = 5;

    private final ContentNoticeRepository repository;
    private final Clock clock;
    private final Map<String, Deque<Instant>> recentByIp = new ConcurrentHashMap<>();

    public ContentNoticeService(ContentNoticeRepository repository) {
        this(repository, Clock.systemUTC());
    }

    ContentNoticeService(ContentNoticeRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Données saisies dans le formulaire ; {@code website} est le champ piège, toujours vide pour un humain. */
    public record Submission(
            String name,
            String email,
            ContentNotice.NotifierQuality quality,
            String contentUrl,
            ContentNotice.ContentNoticeCategory category,
            String description,
            String rightsEvidence,
            boolean goodFaith,
            String website) {
    }

    /**
     * Enregistre une notification. Un robot (champ piège rempli) reçoit la même réponse qu'un humain, sans
     * enregistrement ; au-delà de {@link #MAX_PER_HOUR} envois par IP : {@code 429}.
     */
    public UUID submit(Submission submission, String reporterUserId, String clientIp) {
        if (!submission.goodFaith()) {
            throw new BffProblems.ContentNoticeGoodFaithRequiredProblem();
        }
        UUID id = UUID.randomUUID();
        if (submission.website() != null && !submission.website().isBlank()) {
            logger.info("Notification de contenu ignorée (champ piège rempli) depuis {}", clientIp);
            return id;
        }
        throttle(clientIp);
        ContentNotice notice = new ContentNotice(id, ContentNotice.ContentNoticeStatus.RECEIVED, submission.name().trim(),
                submission.email().trim(), submission.quality(), submission.contentUrl().trim(), submission.category(),
                submission.description().trim(), blankToNull(submission.rightsEvidence()), true, reporterUserId,
                Instant.now(clock), null, null, null, null);
        repository.save(notice);
        logger.info("Notification de contenu {} reçue ({}) pour {}", id, notice.category(), notice.contentUrl());
        return id;
    }

    public List<ContentNotice> list(ContentNotice.ContentNoticeStatus status, int page, int size) {
        return repository.findByStatus(status, Math.max(0, page), Math.clamp(size, 1, 100));
    }

    public long count(ContentNotice.ContentNoticeStatus status) {
        return repository.countByStatus(status);
    }

    /** Décision de la modération, motivée ; l'action elle-même (retrait, fermeture) passe par son endpoint. */
    public ContentNotice decide(UUID id, ContentNotice.ContentNoticeDecision decision, String reason, String adminId) {
        ContentNotice notice = repository.findById(id).orElseThrow(() -> new BffProblems.ContentNoticeNotFoundProblem(id.toString()));
        ContentNotice decided = notice.decide(decision, reason == null ? null : reason.trim(), adminId, Instant.now(clock));
        repository.save(decided);
        logger.info("Notification de contenu {} : {} par {}", id, decision, adminId);
        return decided;
    }

    private void throttle(String clientIp) {
        String key = clientIp == null ? "unknown" : clientIp;
        Instant now = Instant.now(clock);
        Deque<Instant> recent = recentByIp.computeIfAbsent(key, _ -> new ArrayDeque<>());
        synchronized (recent) {
            while (!recent.isEmpty() && recent.peekFirst().isBefore(now.minus(Duration.ofHours(1)))) {
                recent.pollFirst();
            }
            if (recent.size() >= MAX_PER_HOUR) {
                throw new BffProblems.ContentNoticeTooManyProblem();
            }
            recent.addLast(now);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
