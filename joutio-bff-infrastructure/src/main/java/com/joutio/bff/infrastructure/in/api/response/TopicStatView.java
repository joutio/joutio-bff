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
        String emoji,
        String color,
        String imageUrl,
        int games,
        int xp
) {
}
