package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;

/**
 * Validation des paramètres de pagination de la surface web.
 */
public final class PageParams {

    private PageParams() {
    }

    public static void validate(int page, int size, int maxSize) {
        if (page < 0) {
            throw new BffProblems.InvalidRequestParameterProblem("page", "page must be greater than or equal to 0");
        }
        if (size < 1 || size > maxSize) {
            throw new BffProblems.InvalidRequestParameterProblem("size", "size must be between 1 and " + maxSize);
        }
    }
}
