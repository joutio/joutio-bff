package com.joutio.bff.application;

import java.time.Instant;

/**
 * Déclaration faite par l'auteur d'un envoi d'image (2026-11-15, cadre LCEN/DSA) : il certifie détenir les droits sur
 * l'image (droit d'auteur, droit à l'image) ou qu'elle est libre de droits, selon la version des CGU en vigueur.
 * Conservée avec l'image comme preuve de l'engagement de l'utilisateur.
 */
public record MediaRightsDeclaration(Instant declaredAt, String termsVersion, String sourceNote) {
}
