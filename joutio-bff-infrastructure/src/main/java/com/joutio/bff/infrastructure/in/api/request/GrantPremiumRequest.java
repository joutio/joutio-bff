package com.joutio.bff.infrastructure.in.api.request;

import java.time.Instant;

/**
 * Attribution manuelle de Premium par un administrateur. {@code premiumUntil} nul = sans échéance.
 */
public record GrantPremiumRequest(Instant premiumUntil) {
}
