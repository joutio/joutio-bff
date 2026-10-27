package com.joutio.bff.infrastructure.in.api.request;

/**
 * Validation des paramètres de pagination de la surface web.
 */
public final class PageParams {

    private PageParams() {
    }

    public static void validate(int page, int size, int maxSize) {
        if (page < 0) {
            throw new IllegalArgumentException("page doit être supérieur ou égal à 0");
        }
        if (size < 1 || size > maxSize) {
            throw new IllegalArgumentException("size doit être compris entre 1 et " + maxSize);
        }
    }
}
