package com.joutio.bff.infrastructure.config;

import com.joutio.bff.application.CreationLimitsProperties;
import com.joutio.bff.application.DuelLimitsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Active les limites de duels ({@code joutio.duel.limits.*}) et de création ({@code joutio.creation.limits.*}). */
@Configuration
@EnableConfigurationProperties({DuelLimitsProperties.class, CreationLimitsProperties.class})
public class DuelLimitsConfiguration {
}
