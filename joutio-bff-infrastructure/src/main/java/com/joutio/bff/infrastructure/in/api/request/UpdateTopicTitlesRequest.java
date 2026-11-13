package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.microservice.core.domain.model.i18n.Language;
import java.util.Map;

/**
 * Titres par palier d'un sujet ({@code langue → palier (FER..DIAMANT) → libellé}). Un libellé vide
 * ou absent retombe sur le repli mécanique « Or · Sujet » ; {@code null} réinitialise tous les
 * titres. Validation (paliers connus, 40 caractères) portée par l'agrégat {@code Topic}.
 */
public record UpdateTopicTitlesRequest(Map<Language, Map<String, String>> titles) {}
