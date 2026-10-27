package org.ecom.media;

/**
 * Where uploaded images live. Both implementations serve them under the same public path, {@code /media/<key>}:
 * from the application in local development, through CloudFront from S3 in AWS.
 */
public interface ImageStorage {

    String PUBLIC_PREFIX = "/media/";

    /** Stores the image and returns its public path. */
    String store(String key, byte[] content, ImageFormat format);

    /** Deletes an image stored here; does nothing for any other URL (an external image entered by hand). */
    void delete(String publicPath);

    static boolean isStored(String url) {
        return url != null && url.startsWith(PUBLIC_PREFIX) && !url.contains("..");
    }

}
