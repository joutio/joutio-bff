package com.joutio.bff.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Liens universels {@code go.joutio.com} ({@code joutio.share.*}, {@code design/specs/screens/partage.md}).
 *
 * @param webUrl          app web, ouverte sur ordinateur
 * @param appStoreUrl     fiche App Store
 * @param androidPackage  paquet Play Store et App Links
 * @param appleAppIds     {@code TEAMID.bundle} déclarés dans l'AASA (Universal Links) ; vide = aucun
 * @param androidSha256   empreintes SHA-256 de la clé de signature (assetlinks.json) ; vide = aucune
 * @param defaultImage    image d'aperçu sans bannière ni couverture
 */
@ConfigurationProperties(prefix = "joutio.share")
public record ShareProperties(
        @DefaultValue("https://app.joutio.com") String webUrl,
        @DefaultValue("https://apps.apple.com/app/joutio") String appStoreUrl,
        @DefaultValue("com.joutio.app") String androidPackage,
        @DefaultValue("") List<String> appleAppIds,
        @DefaultValue("") List<String> androidSha256,
        @DefaultValue("https://joutio.com/og.png") String defaultImage) {

    public String playStoreUrl(String referrer) {
        String base = "https://play.google.com/store/apps/details?id=" + androidPackage;
        return referrer == null ? base : base + "&referrer=" + java.net.URLEncoder.encode(referrer, java.nio.charset.StandardCharsets.UTF_8);
    }
}
