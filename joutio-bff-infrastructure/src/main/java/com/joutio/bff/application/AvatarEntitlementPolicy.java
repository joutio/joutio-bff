package com.joutio.bff.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Sépare l'avatar de base gratuit (icône + fond), Micah (récompense ou Premium) et les styles
 * DiceBear Premium (Lorelei, Notionists). Le BFF ne fait pas confiance au client : il relit le
 * preset et vérifie les droits avant la commande.
 */
@Component
public class AvatarEntitlementPolicy {

    private static final String BASE_STYLE = "base";
    private static final String MICAH_STYLE = "micah";
    private static final Set<String> PREMIUM_ONLY_STYLES = Set.of("lorelei", "notionists");

    private final ObjectMapper objectMapper;

    public AvatarEntitlementPolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void assertCanSave(String avatarOptions, boolean premium, boolean micahUnlocked) {
        if (avatarOptions == null || avatarOptions.isBlank()) {
            return;
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(avatarOptions);
        } catch (Exception exception) {
            throw new BffProblems.InvalidAvatarOptionsProblem();
        }
        if (node == null || !node.isObject()) {
            throw new BffProblems.InvalidAvatarOptionsProblem();
        }
        String style = node.path("style").asText(MICAH_STYLE);
        if (BASE_STYLE.equals(style)) {
            return;
        }
        if (MICAH_STYLE.equals(style)) {
            if (!micahUnlocked && !premium) {
                throw new BffProblems.MicahLockedProblem();
            }
            return;
        }
        if (!PREMIUM_ONLY_STYLES.contains(style)) {
            throw new BffProblems.InvalidAvatarOptionsProblem();
        }
        if (!premium) {
            throw new BffProblems.PremiumRequiredProblem();
        }
    }
}
