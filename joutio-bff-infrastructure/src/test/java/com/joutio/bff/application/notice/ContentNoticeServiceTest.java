package com.joutio.bff.application.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class ContentNoticeServiceTest {

    private final List<ContentNotice> saved = new ArrayList<>();
    private final ContentNoticeRepository repository = new ContentNoticeRepository() {
        @Override
        public ContentNotice save(ContentNotice notice) {
            saved.removeIf(existing -> existing.id().equals(notice.id()));
            saved.add(notice);
            return notice;
        }

        @Override
        public Optional<ContentNotice> findById(UUID id) {
            return saved.stream().filter(notice -> notice.id().equals(id)).findFirst();
        }

        @Override
        public List<ContentNotice> findByStatus(ContentNotice.ContentNoticeStatus status, int page, int size) {
            return saved;
        }

        @Override
        public long countByStatus(ContentNotice.ContentNoticeStatus status) {
            return saved.size();
        }
    };
    private final ContentNoticeService service =
            new ContentNoticeService(repository, Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneOffset.UTC));

    private static ContentNoticeService.Submission submission(boolean goodFaith, String website) {
        return new ContentNoticeService.Submission("Ana Martin", "ana@example.test", ContentNotice.NotifierQuality.RIGHTS_HOLDER,
                "https://go.joutio.com/t/topic-1", ContentNotice.ContentNoticeCategory.COPYRIGHT,
                "La vignette du sujet reproduit ma photographie sans autorisation.", "Photo publiée sur mon site", goodFaith, website);
    }

    @Test
    void submit_records_the_notice_as_received() {
        UUID id = service.submit(submission(true, null), null, "1.2.3.4");

        assertThat(saved).singleElement().satisfies(notice -> {
            assertThat(notice.id()).isEqualTo(id);
            assertThat(notice.status()).isEqualTo(ContentNotice.ContentNoticeStatus.RECEIVED);
            assertThat(notice.category()).isEqualTo(ContentNotice.ContentNoticeCategory.COPYRIGHT);
        });
    }

    @Test
    void submit_requires_good_faith_and_ignores_robots() {
        assertThatThrownBy(() -> service.submit(submission(false, null), null, "1.2.3.4"))
                .isInstanceOf(BffProblems.ContentNoticeGoodFaithRequiredProblem.class);

        service.submit(submission(true, "https://spam.test"), null, "1.2.3.4");
        assertThat(saved).isEmpty();
    }

    @Test
    void submit_limits_notices_per_address_and_hour() {
        for (int i = 0; i < ContentNoticeService.MAX_PER_HOUR; i++) {
            service.submit(submission(true, null), null, "1.2.3.4");
        }

        assertThatThrownBy(() -> service.submit(submission(true, null), null, "1.2.3.4"))
                .isInstanceOf(BffProblems.ContentNoticeTooManyProblem.class);
        service.submit(submission(true, null), null, "5.6.7.8");
        assertThat(saved).hasSize(ContentNoticeService.MAX_PER_HOUR + 1);
    }

    @Test
    void decide_records_the_motivated_decision() {
        UUID id = service.submit(submission(true, null), null, "1.2.3.4");

        ContentNotice decided = service.decide(id, ContentNotice.ContentNoticeDecision.TOPIC_CLOSED, "Image reproduite sans droit", "admin");

        assertThat(decided.status()).isEqualTo(ContentNotice.ContentNoticeStatus.ACTIONED);
        assertThat(decided.decisionReason()).isEqualTo("Image reproduite sans droit");
        assertThat(decided.decidedBy()).isEqualTo("admin");
    }
}
