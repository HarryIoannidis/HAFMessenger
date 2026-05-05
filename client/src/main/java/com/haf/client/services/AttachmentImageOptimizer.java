package com.haf.client.services;

import com.haf.shared.constants.AttachmentConstants;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Optimizes outbound image attachments before encryption when the user chooses
 * a lower send quality.
 */
public final class AttachmentImageOptimizer {
    private static final int ORIGINAL_QUALITY = 100;
    private static final int MAX_DIMENSION = 2560;

    /**
     * Result of an image optimization attempt.
     *
     * @param bytes     bytes to send
     * @param mediaType MIME type of the bytes
     * @param fileName  display filename matching the bytes
     * @param optimized whether bytes differ from the original source
     */
    public record OptimizedAttachment(byte[] bytes, String mediaType, String fileName, boolean optimized) {
    }

    private AttachmentImageOptimizer() {
    }

    /**
     * Optimizes image bytes only when quality is below 100 and the image is
     * readable by ImageIO.
     *
     * @param originalBytes original file bytes
     * @param mediaType     normalized source media type
     * @param fileName      source file name
     * @param quality       user quality in range 60..100
     * @return optimized result, or original bytes when optimization is not useful
     */
    public static OptimizedAttachment optimize(
            byte[] originalBytes,
            String mediaType,
            String fileName,
            int quality) {
        byte[] safeBytes = originalBytes == null ? new byte[0] : originalBytes;
        String safeMediaType = AttachmentConstants.normalizeMimeType(mediaType);
        String safeFileName = fileName == null || fileName.isBlank() ? "attachment" : fileName;

        if (quality >= ORIGINAL_QUALITY || !isOptimizableImageMediaType(safeMediaType) || safeBytes.length == 0) {
            return original(safeBytes, safeMediaType, safeFileName);
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(safeBytes));
            if (image == null) {
                return original(safeBytes, safeMediaType, safeFileName);
            }

            BufferedImage resized = resizeIfNeeded(image);
            boolean hasAlpha = resized.getColorModel().hasAlpha();
            EncodedImage encoded = hasAlpha
                    ? encodePng(resized)
                    : encodeJpeg(resized, quality);
            if (encoded.bytes().length == 0 || encoded.bytes().length >= safeBytes.length) {
                return original(safeBytes, safeMediaType, safeFileName);
            }
            return new OptimizedAttachment(
                    encoded.bytes(),
                    encoded.mediaType(),
                    adjustFileName(safeFileName, encoded.mediaType()),
                    true);
        } catch (Exception _) {
            return original(safeBytes, safeMediaType, safeFileName);
        }
    }

    private static OptimizedAttachment original(byte[] bytes, String mediaType, String fileName) {
        return new OptimizedAttachment(bytes, mediaType, fileName, false);
    }

    private static boolean isOptimizableImageMediaType(String mediaType) {
        return "image/jpeg".equals(mediaType)
                || "image/png".equals(mediaType)
                || "image/bmp".equals(mediaType);
    }

    private static BufferedImage resizeIfNeeded(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int longest = Math.max(width, height);
        if (longest <= MAX_DIMENSION) {
            return image;
        }

        double scale = MAX_DIMENSION / (double) longest;
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        int type = image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, type);

        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private static EncodedImage encodeJpeg(BufferedImage image, int quality) throws IOException {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            return new EncodedImage(new byte[0], "image/jpeg");
        }

        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(Math.clamp(quality, 60, 95) / 100.0f);
            }
            writer.write(null, new IIOImage(rgb, null, null), params);
            return new EncodedImage(output.toByteArray(), "image/jpeg");
        } finally {
            writer.dispose();
        }
    }

    private static EncodedImage encodePng(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return new EncodedImage(output.toByteArray(), "image/png");
        }
    }

    private static String adjustFileName(String fileName, String mediaType) {
        if (!"image/jpeg".equals(mediaType)) {
            return fileName;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return fileName;
        }
        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        return stem + ".jpg";
    }

    private record EncodedImage(byte[] bytes, String mediaType) {
    }
}
