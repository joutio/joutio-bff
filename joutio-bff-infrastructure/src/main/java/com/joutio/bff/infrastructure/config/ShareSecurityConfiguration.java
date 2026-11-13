package com.joutio.bff.infrastructure.config;

import com.joutio.bff.application.ShareProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Chaîne publique des liens {@code go.joutio.com} : sans authentification, sans session, en lecture seule. Placée
 * avant la chaîne resource server du SDK, qui garde son comportement pour {@code /api/**} et {@code /ws/**}.
 */
@Configuration
@EnableConfigurationProperties(ShareProperties.class)
public class ShareSecurityConfiguration {

    @Bean
    @Order(1)
    public SecurityFilterChain shareLinksSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/go/**", "/.well-known/apple-app-site-association", "/.well-known/assetlinks.json")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }
}
