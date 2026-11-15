package com.joutio.bff.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Instant;

/**
 * Compte du joueur ({@code joutio.account.*}).
 *
 * @param birthDateRequiredSince mise en service de la date de naissance : les comptes créés à partir de cet instant
 *                               doivent la renseigner avant de jouer ({@code MeView.birthDateRequired}) ; absent = jamais
 *                               exigée.
 * @param termsVersion           version des CGU en vigueur (2026-11-15, `joutio-landing/src/data/legal.ts`) : un joueur
 *                               qui ne l'a pas acceptée doit le faire avant de jouer ({@code MeView.termsUpToDate}) ;
 *                               absente = aucune acceptation exigée.
 *
 * <p>Un seul constructeur : avec deux, Spring Boot ne sait plus lier le record et le démarrage échoue
 * (« No default constructor found »).</p>
 */
@ConfigurationProperties(prefix = "joutio.account")
public record AccountProperties(Instant birthDateRequiredSince, String termsVersion) {

    /** Le joueur a accepté la version en vigueur, ou aucune n'est exigée. */
    public boolean termsUpToDate(String acceptedVersion) {
        return termsVersion == null || termsVersion.isBlank() || termsVersion.equals(acceptedVersion);
    }
}
