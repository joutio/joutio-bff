package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.microservice.core.domain.model.i18n.Language;

import java.util.Map;

/**
 * Suggestion de la palette ⌘K : sujet ou joueur.
 * {@code names} est renseigné pour les sujets (résolution par langue côté client, repli {@code label}).
 */
public record SuggestionView(
        Type type,
        String id,
        String label,
        String subtitle,
        /** Catégorie du sujet (enum `TopicCategory`, `null` pour un joueur) : couleur côté web. */
        String category,
        String emoji,
        String color,
        String imageUrl,
        String avatarOptions,
        Map<Language, String> names,
        boolean suspended
) {

    public SuggestionView(Type type,
                          String id,
                          String label,
                          String subtitle,
                          String category,
                          String emoji,
                          String color,
                          String imageUrl,
                          String avatarOptions,
                          Map<Language, String> names) {
        this(type, id, label, subtitle, category, emoji, color, imageUrl, avatarOptions, names, false);
    }

    public enum Type {
        TOPIC,
        PLAYER
    }
}
