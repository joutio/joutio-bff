package com.joutio.bff.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Instant;

/**
 * Compte du joueur ({@code joutio.account.*}).
 *
 * @param birthDateRequiredSince mise en service de la date de naissance : les comptes créés à partir de cet instant
 *                               doivent la renseigner avant de jouer ({@code MeView.birthDateRequired}) ; absent = jamais
 *                               exigée.
 */
@ConfigurationProperties(prefix = "joutio.account")
public record AccountProperties(Instant birthDateRequiredSince) {
}
