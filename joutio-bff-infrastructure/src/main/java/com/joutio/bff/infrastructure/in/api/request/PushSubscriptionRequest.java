package com.joutio.bff.infrastructure.in.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Abonnement Web Push d'un navigateur (payload de {@code PushManager.subscribe}). */
public record PushSubscriptionRequest(
        @NotBlank @Size(max = 1024) String endpoint,
        @NotNull @Valid Keys keys) {

    public record Keys(
            @NotBlank @Size(max = 255) String p256dh,
            @NotBlank @Size(max = 255) String auth) {
    }
}
