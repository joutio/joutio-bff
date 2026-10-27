package com.joutio.bff.infrastructure.config;

import com.joutio.bff.infrastructure.out.push.WebPushProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Exécuteur dédié au Web Push : les envois HTTP partent hors du thread des tracking processors
 * (le fan-out STOMP ne doit jamais attendre un service de push) et ne partagent pas le pool
 * Spring commun (tâches potentiellement bloquantes et nombreuses).
 */
@Configuration
@EnableConfigurationProperties(WebPushProperties.class)
public class WebPushConfiguration {

    @Bean
    public ThreadPoolTaskExecutor webPushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("web-push-");
        executor.setDaemon(true);
        executor.initialize();
        return executor;
    }
}
