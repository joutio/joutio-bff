package com.joutio.bff.infrastructure.in.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Événement produit émis par le client web (activation, choix de mode de jeu…).
 *
 * <p>Le nom est volontairement une chaîne : le service applique une allowlist stricte, et
 * les propriétés sont limitées à quelques clés bornées (pas de PII, pas de cardinalité libre).</p>
 */
public record ProductEventRequest(
        @NotBlank @Size(max = 64) String name,
        @Size(max = 8) Map<String, @Size(max = 64) String> properties
) {
    public ProductEventRequest {
        if (properties == null) {
            properties = Map.of();
        } else {
            Map<String, String> sanitized = new LinkedHashMap<>();
            properties.forEach((key, value) -> {
                if (key != null && value != null) {
                    sanitized.put(key, value);
                }
            });
            properties = Map.copyOf(sanitized);
        }
    }
}
