package com.joutio.bff.infrastructure.in.api.request;

import com.joutio.bff.application.ExpoPushStore.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ExpoPushSubscriptionRequest(
        @NotBlank @Pattern(regexp="[a-zA-Z0-9-]{16,64}") String installationId,
        @NotBlank @Size(max=255) @Pattern(regexp="(?:ExponentPushToken|ExpoPushToken)\\[[A-Za-z0-9_-]+\\]") String token,
        @NotNull Platform platform,
        @NotNull Boolean rematches) {}
