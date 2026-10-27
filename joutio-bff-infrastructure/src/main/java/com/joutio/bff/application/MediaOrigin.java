package com.joutio.bff.application;

/**
 * Origine d'un objet du bucket : upload applicatif (BFF) ou image du seed (`wikipedia/`).
 * Les préfixes sont la seule classification fiable — le bucket ne porte pas de métadonnée d'origine.
 */
public enum MediaOrigin {

    UPLOAD("uploads/"),
    SEED("wikipedia/");

    private final String prefix;

    MediaOrigin(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }

    public static MediaOrigin of(String objectKey) {
        for (MediaOrigin origin : values()) {
            if (objectKey.startsWith(origin.prefix)) {
                return origin;
            }
        }
        return null;
    }
}
