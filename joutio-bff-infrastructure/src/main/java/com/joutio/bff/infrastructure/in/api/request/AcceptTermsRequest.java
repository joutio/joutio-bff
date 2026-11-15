package com.joutio.bff.infrastructure.in.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corps de {@code PUT /api/me/terms} : version des CGU acceptée ({@code MeView.termsVersion}). */
public record AcceptTermsRequest(@NotBlank @Size(max = 32) String version) {
}
