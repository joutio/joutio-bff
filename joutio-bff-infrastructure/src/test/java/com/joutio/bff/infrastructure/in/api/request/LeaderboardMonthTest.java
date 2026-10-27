package com.joutio.bff.infrastructure.in.api.request;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LeaderboardMonthTest {

    @Test
    void blank_isNull() {
        assertNull(LeaderboardMonth.validate(null));
        assertNull(LeaderboardMonth.validate(" "));
    }

    @Test
    void valid_month_isTrimmed() {
        assertEquals("2026-08", LeaderboardMonth.validate(" 2026-08 "));
    }

    @Test
    void invalid_month_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> LeaderboardMonth.validate("2026-13"));
        assertThrows(IllegalArgumentException.class, () -> LeaderboardMonth.validate("2026-8"));
        assertThrows(IllegalArgumentException.class, () -> LeaderboardMonth.validate("août"));
    }
}
