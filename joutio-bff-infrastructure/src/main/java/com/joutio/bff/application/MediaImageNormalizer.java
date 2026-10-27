package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.out.media.MediaProperties;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

/**
 * Validation + normalisation des images uploadées :
 *
 * <ul>
 *   <li>format détecté par contenu (magic bytes) : **JPEG ou PNG uniquement** (SVG/GIF/WebP/HEIC
 *       refusés — SVG pour l'XSS, GIF pour l'animation, les autres ne sont pas décodables par le
 *       JDK) ;</li>
 *   <li>garde anti-bombe : dimensions et nombre de pixels bornés avant décodage complet ;</li>
 *   <li>redimensionnement à {@code joutio.media.max-dimension} (jamais d'upscale) ;</li>
 *   <li>ré-encodage : JPEG (qualité configurable) si opaque, PNG si transparence — les
 *       métadonnées EXIF sont perdues (l'orientation EXIF est traitée côté client par le
 *       pré-redimensionnement canvas).</li>
 * </ul>
 */
@Component
public class MediaImageNormalizer {

    private final MediaProperties properties;

    public MediaImageNormalizer(MediaProperties properties) {
        this.properties = properties;
    }

    public MediaImage normalize(byte[] content) {
        if (content == null || content.length == 0) {
            throw new BffProblems.MediaFileMissingProblem();
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BffProblems.MediaUnsupportedFormatProblem();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("jpg") && !format.equals("png")) {
                    throw new BffProblems.MediaUnsupportedFormatProblem();
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0
                        || width > properties.maxInputDimension()
                        || height > properties.maxInputDimension()
                        || (long) width * height > properties.maxPixels()) {
                    throw new BffProblems.MediaInvalidImageProblem(
                            "dimensions inadmissibles (" + width + "x" + height + ")");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new BffProblems.MediaInvalidImageProblem("image illisible");
                }
                boolean alpha = image.getColorModel().hasAlpha();
                return encode(scale(image), alpha);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BffProblems.MediaInvalidImageProblem("image illisible");
        }
    }

    private BufferedImage scale(BufferedImage source) {
        int max = properties.maxDimension();
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= max && height <= max) {
            return source;
        }
        double ratio = Math.min((double) max / width, (double) max / height);
        int targetWidth = Math.max(1, (int) Math.round(width * ratio));
        int targetHeight = Math.max(1, (int) Math.round(height * ratio));
        int type = source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage current = source;
        int currentWidth = width;
        int currentHeight = height;
        // Réduction progressive par demi : bien meilleure qualité qu'un downscale direct.
        while (currentWidth / 2 >= targetWidth && currentHeight / 2 >= targetHeight) {
            currentWidth = Math.max(targetWidth, currentWidth / 2);
            currentHeight = Math.max(targetHeight, currentHeight / 2);
            current = draw(current, currentWidth, currentHeight, type);
        }
        return draw(current, targetWidth, targetHeight, type);
    }

    private static BufferedImage draw(BufferedImage source, int width, int height, int type) {
        BufferedImage target = new BufferedImage(width, height, type);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private MediaImage encode(BufferedImage image, boolean alpha) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            if (alpha) {
                ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
                try {
                    writer.setOutput(output);
                    writer.write(image);
                } finally {
                    writer.dispose();
                }
                return new MediaImage(bytes.toByteArray(), "image/png", ".png",
                        image.getWidth(), image.getHeight());
            }
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            try {
                ImageWriteParam parameters = writer.getDefaultWriteParam();
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parameters.setCompressionQuality((float) properties.jpegQuality());
                BufferedImage opaque = image.getType() == BufferedImage.TYPE_INT_RGB
                        ? image
                        : draw(image, image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                writer.setOutput(output);
                writer.write(null, new IIOImage(opaque, null, null), parameters);
                return new MediaImage(bytes.toByteArray(), "image/jpeg", ".jpg",
                        opaque.getWidth(), opaque.getHeight());
            } finally {
                writer.dispose();
            }
        }
    }
}
