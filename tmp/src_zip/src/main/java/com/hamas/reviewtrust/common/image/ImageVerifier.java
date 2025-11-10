package com.hamas.reviewtrust.common.image;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;

/**
 * Simple verifier for image content.
 *
 * <p>This helper validates that supplied image bytes represent a supported
 * image format, checks basic constraints such as maximum file size and
 * dimension, and exposes access to the decoded dimensions. It does not
 * perform any perceptual or content analysis but is sufficient to reject
 * malformed or oversized uploads.</p>
 */
public final class ImageVerifier {
    private static final Set<String> SUPPORTED_TYPES = Set.of("jpeg", "jpg", "png", "gif", "bmp");

    private ImageVerifier() {
        // utility class
    }

    /**
     * Attempts to decode the supplied image bytes. On success a Dimension is
     * returned containing the width and height. If decoding fails the return
     * value is null.
     *
     * @param bytes image file contents
     * @return image dimensions or null if unreadable
     */
    public static Dimension getDimension(byte[] bytes) {
        if (bytes == null) return null;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            BufferedImage img = ImageIO.read(bais);
            if (img == null) return null;
            return new Dimension(img.getWidth(), img.getHeight());
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Validates whether the image bytes are of a supported image type. This
     * method inspects the first magic bytes for common signatures (PNG, JPEG,
     * GIF, BMP) and falls back to ImageIO probing for additional formats.
     *
     * @param bytes image bytes
     * @return true if the file appears to be a supported image
     */
    public static boolean isSupportedFormat(byte[] bytes) {
        if (bytes == null || bytes.length < 8) return false;
        // PNG signature: 89 50 4E 47 0D 0A 1A 0A
        if (bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return true;
        }
        // JPEG signature: FF D8 .. FF
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return true;
        }
        // GIF signature: GIF87a or GIF89a
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return true;
        }
        // BMP signature: BM
        if (bytes[0] == 'B' && bytes[1] == 'M') {
            return true;
        }
        // Fallback: ask ImageIO for type
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            String formatName = ImageIO.getImageReaders(ImageIO.createImageInputStream(bais))
                    .next()
                    .getFormatName().toLowerCase();
            return SUPPORTED_TYPES.contains(formatName);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Performs a basic sanity check on the image. It verifies the file does
     * not exceed the given maximum number of bytes, is of a supported
     * format, and optionally checks the dimensions against provided maxima.
     * Pass {@code null} for {@code maxWidth} or {@code maxHeight} to skip
     * dimension checks.
     *
     * @param bytes image bytes
     * @param maxBytes maximum allowed size in bytes
     * @param maxWidth optional maximum width
     * @param maxHeight optional maximum height
     * @return true if all checks pass
     */
    public static boolean isValidImage(byte[] bytes, int maxBytes, Integer maxWidth, Integer maxHeight) {
        if (bytes == null) return false;
        if (maxBytes > 0 && bytes.length > maxBytes) return false;
        if (!isSupportedFormat(bytes)) return false;
        Dimension dim = getDimension(bytes);
        if (dim == null) return false;
        if (maxWidth != null && dim.width > maxWidth) return false;
        if (maxHeight != null && dim.height > maxHeight) return false;
        return true;
    }
}