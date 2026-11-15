package com.joutio.bff.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccountPropertiesTest {

    @Test
    void bindsFromConfiguration() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "joutio.account.birth-date-required-since", "2026-11-13T00:00:00Z",
                "joutio.account.terms-version", "2026-11-15")));

        AccountProperties properties = binder.bind("joutio.account", AccountProperties.class).get();

        assertThat(properties.birthDateRequiredSince()).isEqualTo(Instant.parse("2026-11-13T00:00:00Z"));
        assertThat(properties.termsVersion()).isEqualTo("2026-11-15");
        assertThat(properties.termsUpToDate("2026-11-15")).isTrue();
        assertThat(properties.termsUpToDate("2026-01-01")).isFalse();
    }
}
