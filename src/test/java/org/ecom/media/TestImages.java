package org.ecom.media;

import java.util.Base64;

/** Real, tiny images for upload tests. */
public final class TestImages {

    /** A 1×1 transparent PNG. */
    public static final byte[] PNG = Base64.getDecoder()
            .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");

    private TestImages() {
    }

}
