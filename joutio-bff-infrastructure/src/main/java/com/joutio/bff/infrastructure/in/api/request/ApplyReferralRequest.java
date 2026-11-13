package com.joutio.bff.infrastructure.in.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corps de {@code PUT /api/me/referral} : code de parrainage saisi ou lu dans un lien. */
public record ApplyReferralRequest(@NotBlank @Size(max = 16) String code) {
}
