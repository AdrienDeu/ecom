package org.ecom.media;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * In the media bucket, under {@code media/}: CloudFront serves {@code /media/*} from it, so the object key is the
 * public path without its leading slash. Keys are never reused, so images can be cached for good.
 */
class S3ImageStorage implements ImageStorage, AutoCloseable {

    private final S3Client s3;
    private final String bucket;

    S3ImageStorage(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public String store(String key, byte[] content, ImageFormat format) {
        var path = PUBLIC_PREFIX + key;
        s3.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(path.substring(1))
                .contentType(format.contentType())
                .cacheControl("public, max-age=31536000, immutable")
                .build(), RequestBody.fromBytes(content));
        return path;
    }

    @Override
    public void delete(String publicPath) {
        if (ImageStorage.isStored(publicPath)) {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(publicPath.substring(1)).build());
        }
    }

    /** Called by Spring on shutdown. */
    @Override
    public void close() {
        s3.close();
    }

}
