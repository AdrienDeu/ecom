package org.ecom.media;

/** The uploaded file is not one of the accepted image formats. */
public class InvalidImageException extends RuntimeException {

    public InvalidImageException() {
        super("Only JPEG, PNG and WebP images are accepted");
    }

}
