package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.out.media.MediaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaImageNormalizerTest {

    private final MediaImageNormalizer normalizer = new MediaImageNormalizer(properties(100, 5));

    @Test
    void shrinksOpaqueImagesToJpegWithinMaxDimension() throws IOException {
        MediaImage image = normalizer.normalize(image("png", 400, 200, false));

        assertThat(image.width()).isEqualTo(100);
        assertThat(image.height()).isEqualTo(50);
        assertThat(image.contentType()).isEqualTo("image/jpeg");
        assertThat(image.extension()).isEqualTo(".jpg");
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(image.content()))).isNotNull();
    }

    @Test
    void keepsSmallImagesUntouchedInSize() throws IOException {
        MediaImage image = normalizer.normalize(image("png", 80, 40, false));

        assertThat(image.width()).isEqualTo(80);
        assertThat(image.height()).isEqualTo(40);
        assertThat(image.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void preservesTransparencyAsPng() throws IOException {
        MediaImage image = normalizer.normalize(image("png", 20, 20, true));

        assertThat(image.contentType()).isEqualTo("image/png");
        assertThat(image.extension()).isEqualTo(".png");
    }

    @Test
    void rejectsUnsupportedFormats() throws IOException {
        assertThatThrownBy(() -> normalizer.normalize(image("gif", 20, 20, false)))
                .isInstanceOf(BffProblems.MediaUnsupportedFormatProblem.class);
    }

    @Test
    void rejectsNonImageContent() {
        assertThatThrownBy(() -> normalizer.normalize("pas une image".getBytes()))
                .isInstanceOf(BffProblems.MediaUnsupportedFormatProblem.class);
    }

    @Test
    void rejectsEmptyContent() {
        assertThatThrownBy(() -> normalizer.normalize(new byte[0]))
                .isInstanceOf(BffProblems.MediaFileMissingProblem.class);
    }

    @Test
    void rejectsDimensionsBeyondInputGuard() throws IOException {
        MediaImageNormalizer guarded = new MediaImageNormalizer(properties(100, 5, 150));

        assertThatThrownBy(() -> guarded.normalize(image("png", 200, 10, false)))
                .isInstanceOf(BffProblems.MediaInvalidImageProblem.class);
    }

    private static byte[] image(String format, int width, int height, boolean alpha) throws IOException {
        int type = alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage buffered = new BufferedImage(width, height, type);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(buffered, format, bytes);
        return bytes.toByteArray();
    }

    private static MediaProperties properties(int maxDimension, int maxMegabytes) {
        return properties(maxDimension, maxMegabytes, 8000);
    }

    private static MediaProperties properties(int maxDimension, int maxMegabytes, int maxInputDimension) {
        return new MediaProperties(
                "https://media.example.test/joutio-media/",
                new MediaProperties.S3("http://localhost:9000", "us-east-1", "joutio-media", "key", "secret", true),
                DataSize.ofMegabytes(maxMegabytes),
                maxDimension,
                maxInputDimension,
                40_000_000L,
                0.85,
                200,
                DataSize.ofMegabytes(100));
    }
}
