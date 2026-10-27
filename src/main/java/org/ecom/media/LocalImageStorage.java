package org.ecom.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** On the local disk, served by the application itself (MediaConfig): local development and tests. */
class LocalImageStorage implements ImageStorage {

    private final Path directory;

    LocalImageStorage(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    @Override
    public String store(String key, byte[] content, ImageFormat format) {
        var file = resolve(key);
        try {
            var parent = file.getParent();
            Files.createDirectories(parent != null ? parent : directory);
            Files.write(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store image " + key, e);
        }
        return PUBLIC_PREFIX + key;
    }

    @Override
    public void delete(String publicPath) {
        if (!ImageStorage.isStored(publicPath)) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(publicPath.substring(PUBLIC_PREFIX.length())));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete image " + publicPath, e);
        }
    }

    Path directory() {
        return directory;
    }

    private Path resolve(String key) {
        var file = directory.resolve(key).normalize();
        if (!file.startsWith(directory)) {
            throw new IllegalArgumentException("Image key outside the media directory: " + key);
        }
        return file;
    }

}
