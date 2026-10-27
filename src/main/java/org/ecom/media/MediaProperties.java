package org.ecom.media;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param storage        {@code local} (default: files under {@code localDirectory}, served by the application) or
 *                       {@code s3} (the media bucket, served by CloudFront)
 * @param localDirectory where local storage writes
 * @param bucket         media bucket, for {@code s3}
 */
@ConfigurationProperties("ecom.media")
public record MediaProperties(@DefaultValue("local") Storage storage,
                              @DefaultValue("var/media") Path localDirectory,
                              String bucket) {

    public enum Storage {
        LOCAL, S3
    }

}
