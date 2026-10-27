package com.joutio.bff.infrastructure.config;

import com.joutio.bff.application.DuelLimitsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Active les limites de création de duels ({@code joutio.duel.limits.*}). */
@Configuration
@EnableConfigurationProperties(DuelLimitsProperties.class)
public class DuelLimitsConfiguration {
}
