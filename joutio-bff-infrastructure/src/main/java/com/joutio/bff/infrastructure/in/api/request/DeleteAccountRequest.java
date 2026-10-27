package com.joutio.bff.infrastructure.in.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Confirmation d'une suppression de compte par code OTP uniquement. */
public record DeleteAccountRequest(
        @NotBlank @Size(min = 6, max = 6) String code
) {
}
