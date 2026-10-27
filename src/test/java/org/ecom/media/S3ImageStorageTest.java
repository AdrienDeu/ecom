package org.ecom.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3ImageStorageTest {

    private final S3Client s3 = mock(S3Client.class);
    private final S3ImageStorage storage = new S3ImageStorage(s3, "ecom-media");

    @Test
    void storesUnderTheKeyCloudFrontServes() {
        var path = storage.store("products/a.webp", TestImages.PNG, ImageFormat.WEBP);

        assertThat(path).isEqualTo("/media/products/a.webp");
        var request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("ecom-media");
        assertThat(request.getValue().key()).isEqualTo("media/products/a.webp");
        assertThat(request.getValue().contentType()).isEqualTo("image/webp");
        assertThat(request.getValue().cacheControl()).contains("immutable");
    }

    @Test
    void deletesOnlyItsOwnImages() {
        storage.delete("https://cdn.example.com/a.png");
        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));

        storage.delete("/media/products/a.png");

        var request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3).deleteObject(request.capture());
        assertThat(request.getValue().key()).isEqualTo("media/products/a.png");
    }

    @Test
    void closesTheClient() {
        storage.close();

        verify(s3).close();
    }

}
