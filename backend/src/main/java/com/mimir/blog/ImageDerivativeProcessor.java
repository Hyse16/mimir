package com.mimir.blog;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ImageDerivativeProcessor {

    private static final String JPEG_CONTENT_TYPE = "image/jpeg";

    private final int optimizedMaxDimension;
    private final int analysisMaxDimension;
    private final float optimizedQuality;
    private final float analysisQuality;
    private final long maxPixels;

    ImageDerivativeProcessor(
            @Value("${mimir.image.optimized-max-dimension:2048}") int optimizedMaxDimension,
            @Value("${mimir.image.analysis-max-dimension:1280}") int analysisMaxDimension,
            @Value("${mimir.image.optimized-jpeg-quality:0.85}") float optimizedQuality,
            @Value("${mimir.image.analysis-jpeg-quality:0.80}") float analysisQuality,
            @Value("${mimir.image.max-pixels:40000000}") long maxPixels) {
        this.optimizedMaxDimension = positive(optimizedMaxDimension, "optimized max dimension");
        this.analysisMaxDimension = positive(analysisMaxDimension, "analysis max dimension");
        this.optimizedQuality = quality(optimizedQuality, "optimized JPEG quality");
        this.analysisQuality = quality(analysisQuality, "analysis JPEG quality");
        this.maxPixels = positive(maxPixels, "maximum decoded pixels");
    }

    ProcessedImage process(String contentType, byte[] content) {
        if ("image/webp".equals(contentType)) {
            return new ProcessedImage(null, null, BlogAssetDerivativeStatus.ORIGINAL_ONLY, null, null);
        }

        BufferedImage original = decode(content);
        ImageVariant optimized = createVariant(original, optimizedMaxDimension, optimizedQuality);
        ImageVariant analysis = createVariant(original, analysisMaxDimension, analysisQuality);
        return new ProcessedImage(
                original.getWidth(),
                original.getHeight(),
                BlogAssetDerivativeStatus.READY,
                optimized,
                analysis);
    }

    private BufferedImage decode(byte[] content) {
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(content);
                ImageInputStream input = ImageIO.createImageInputStream(bytes)) {
            if (input == null) {
                throw invalidImage();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalidImage();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > maxPixels) {
                    throw new InvalidBlogAssetException("Image dimensions exceed the configured pixel limit.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw invalidImage();
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException error) {
            throw invalidImage();
        }
    }

    private ImageVariant createVariant(BufferedImage original, int maxDimension, float quality) {
        double scale = Math.min(1.0, maxDimension / (double) Math.max(original.getWidth(), original.getHeight()));
        int width = Math.max(1, (int) Math.round(original.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(original.getHeight() * scale));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(original, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return new ImageVariant(JPEG_CONTENT_TYPE, width, height, encodeJpeg(target, quality));
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("The Java runtime does not provide a JPEG encoder.");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), params);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("Unable to encode image derivative.", error);
        } finally {
            writer.dispose();
        }
    }

    private InvalidBlogAssetException invalidImage() {
        return new InvalidBlogAssetException("Image content could not be decoded.");
    }

    private static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive.");
        }
        return value;
    }

    private static long positive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive.");
        }
        return value;
    }

    private static float quality(float value, String name) {
        if (value <= 0 || value > 1) {
            throw new IllegalArgumentException(name + " must be greater than 0 and at most 1.");
        }
        return value;
    }

    record ProcessedImage(
            Integer originalWidth,
            Integer originalHeight,
            BlogAssetDerivativeStatus status,
            ImageVariant optimized,
            ImageVariant analysis) {
    }

    record ImageVariant(String contentType, int width, int height, byte[] content) {
    }
}
