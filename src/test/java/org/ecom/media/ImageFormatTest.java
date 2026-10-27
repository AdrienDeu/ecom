package org.ecom.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ImageFormatTest {

    @Test
    void recognisesImagesFromTheirFirstBytes() {
        assertThat(ImageFormat.detect(TestImages.PNG)).contains(ImageFormat.PNG);
        assertThat(ImageFormat.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0})).contains(ImageFormat.JPEG);
        assertThat(ImageFormat.detect("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII))).contains(ImageFormat.WEBP);
    }

    @Test
    void rejectsAnythingElseWhateverItsName() {
        assertThat(ImageFormat.detect("<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageFormat.detect("RIFF\0\0\0\0WAVE".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageFormat.detect(new byte[0])).isEmpty();
    }

}
