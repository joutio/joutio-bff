package com.joutio.bff.infrastructure.in.api.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Corps de {@code PUT /api/me/birth-date} : date ISO {@code AAAA-MM-JJ}, 15 ans minimum (identity). */
public record UpdateBirthDateRequest(@NotNull LocalDate birthDate) {
}
