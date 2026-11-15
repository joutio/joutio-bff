package com.joutio.bff.infrastructure.in.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code POST /api/admin/topics/{topicId}/close} : motif communiqué à l'auteur (exposé des motifs, DSA art.
 * 17), par exemple « Image reproduite sans autorisation (notification du 15/11/2026) ».
 */
public record CloseTopicRequest(@NotBlank @Size(max = 500) String reason) {
}
