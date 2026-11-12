package com.joutio.bff.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Plafonds de création ({@code joutio.creation.limits.*}), gratuit / Premium
 * (design/specs/screens/creation.md § 3). 0 = illimité.
 */
@ConfigurationProperties(prefix = "joutio.creation.limits")
public record CreationLimitsProperties(
        @DefaultValue("1") int publishedTopics,
        @DefaultValue("10") int premiumPublishedTopics,
        @DefaultValue("30") int questionsPerTopic,
        @DefaultValue("150") int premiumQuestionsPerTopic) {
}
