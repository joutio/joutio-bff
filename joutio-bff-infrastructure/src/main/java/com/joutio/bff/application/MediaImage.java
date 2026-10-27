package com.joutio.bff.application;

/** Image normalisée prête à être stockée (ré-encodée, métadonnées EXIF retirées). */
public record MediaImage(byte[] content, String contentType, String extension, int width, int height) {
}
