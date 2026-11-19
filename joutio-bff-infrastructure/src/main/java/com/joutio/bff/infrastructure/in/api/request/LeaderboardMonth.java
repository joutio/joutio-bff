package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;

import java.util.regex.Pattern;

/**
 * Validation du paramètre {@code month} des classements mensuels ({@code YYYY-MM}).
 */
public final class LeaderboardMonth {

    private static final Pattern FORMAT = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    private LeaderboardMonth() {
    }

    /**
     * @return le mois normalisé, ou {@code null} si absent (le service retombe sur le mois courant).
     * @throws BffProblems.InvalidRequestParameterProblem si le format n'est pas {@code YYYY-MM}.
     */
    public static String validate(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        String normalized = month.trim();
        if (!FORMAT.matcher(normalized).matches()) {
            throw new BffProblems.InvalidRequestParameterProblem("month", "month must use the YYYY-MM format");
        }
        return normalized;
    }
}
