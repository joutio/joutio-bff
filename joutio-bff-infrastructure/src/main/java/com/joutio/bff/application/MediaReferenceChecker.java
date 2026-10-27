package com.joutio.bff.application;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Port de vérification des références d'images dans les autres services (theme : couverture,
 * bannière de sujet et illustration de question ; profile : bannière de profil). Utilisé avant
 * toute suppression d'administration : une image encore référencée ne peut être supprimée que
 * sur demande explicite ({@code force}). En cas d'indisponibilité, l'appelant échoue fermé.
 */
public interface MediaReferenceChecker {

    /**
     * Compte, par URL, le nombre d'éléments métier qui la référencent (0 si absente).
     *
     * @param urls URLs publiques des objets (taille bornée par la page d'administration)
     */
    CompletableFuture<Map<String, Long>> countReferences(List<String> urls);
}
