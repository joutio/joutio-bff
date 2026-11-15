package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.microservice.core.domain.model.i18n.Language;

import java.util.Map;

/**
 * Part d'un sujet dans l'activité du joueur (donut « Sujets joués » du profil) : parties humaines
 * jouées et XP dans le sujet, avec les informations éditoriales du sujet.
 */
public record TopicStatView(
        String topicId,
        Map<Language, String> names,
        /** Catégorie du sujet (enum `TopicCategory`) : couleur de sa part dans le donut. */
        String category,
        String imageUrl,
        /** Pictogramme carré de la tuile (`null` : repli sur la vignette, puis la catégorie). */
        String pictogramUrl,
        int games,
        int xp,
        /** Sujet éditorial de Joutio (badge « Officiel », 2026-11-15). */
        boolean official
) {

    public TopicStatView(String topicId, Map<Language, String> names, String category, String imageUrl, String pictogramUrl,
                         int games, int xp) {
        this(topicId, names, category, imageUrl, pictogramUrl, games, xp, false);
    }
}
