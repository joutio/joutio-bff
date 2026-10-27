package com.joutio.bff.infrastructure.in.api.response;

/** Clé publique VAPID (applicationServerKey du {@code PushManager.subscribe}). */
public record VapidKeyView(String publicKey) {
}
