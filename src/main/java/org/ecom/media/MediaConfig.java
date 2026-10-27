package org.ecom.media;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MediaProperties.class)
class MediaConfig implements WebMvcConfigurer {

    private final ImageStorage storage;

    MediaConfig(MediaProperties properties) {
        this.storage = switch (properties.storage()) {
            case LOCAL -> new LocalImageStorage(properties.localDirectory());
            // Region and credentials from the environment: the ECS task role in AWS
            case S3 -> new S3ImageStorage(S3Client.create(), properties.bucket());
        };
    }

    @Bean
    ImageStorage imageStorage() {
        return storage;
    }

    /** Local storage only: in AWS, CloudFront serves /media/* straight from the bucket. */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (storage instanceof LocalImageStorage local) {
            registry.addResourceHandler(ImageStorage.PUBLIC_PREFIX + "**")
                    .addResourceLocations(local.directory().toUri().toString());
        }
    }

}
