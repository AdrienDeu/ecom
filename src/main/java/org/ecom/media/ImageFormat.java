package org.ecom.media;

import java.util.Arrays;
import java.util.Optional;

/**
 * Image formats accepted for upload, recognised from the file's first bytes: the name and the declared content type
 * come from the client and prove nothing. No SVG: it can carry scripts.
 */
public enum ImageFormat {

    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final String contentType;
    private final String extension;

    ImageFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<ImageFormat> detect(byte[] content) {
        if (startsWith(content, JPEG_MAGIC, 0)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, PNG_MAGIC, 0)) {
            return Optional.of(PNG);
        }
        // RIFF....WEBP
        if (startsWith(content, "RIFF".getBytes(), 0) && startsWith(content, "WEBP".getBytes(), 8)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, byte[] prefix, int offset) {
        return content.length >= offset + prefix.length
                && Arrays.equals(content, offset, offset + prefix.length, prefix, 0, prefix.length);
    }

}
