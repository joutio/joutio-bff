package com.joutio.bff.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Chaîne publique de {@code /api/public/**} (2026-11-15) : formulaire de notification de contenu illicite, ouvert aux
 * titulaires de droits sans compte. Sans session ; CORS du SDK (la landing {@code joutio.com} doit figurer dans
 * {@code microservice.cors.allowed-origins}). Aucun JWT n'est lu ici : la notification reste anonyme côté compte.
 */
@Configuration
public class PublicApiSecurityConfiguration {

    @Bean
    @Order(2)
    public SecurityFilterChain publicApiSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/public/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }
}
