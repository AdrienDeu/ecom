package org.ecom.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalImageStorageTest {

    @TempDir
    Path directory;

    @Test
    void storesAndDeletesUnderTheMediaPath() throws Exception {
        var storage = new LocalImageStorage(directory);

        var path = storage.store("products/a.png", TestImages.PNG, ImageFormat.PNG);

        assertThat(path).isEqualTo("/media/products/a.png");
        assertThat(Files.readAllBytes(directory.resolve("products/a.png"))).isEqualTo(TestImages.PNG);
        storage.delete(path);
        assertThat(directory.resolve("products/a.png")).doesNotExist();
    }

    @Test
    void neverWritesOutsideItsDirectory() {
        var storage = new LocalImageStorage(directory);

        assertThatIllegalArgumentException().isThrownBy(() -> storage.store("../escape.png", TestImages.PNG, ImageFormat.PNG));
    }

    @Test
    void leavesExternalImagesAlone() throws Exception {
        var outside = Files.writeString(directory.resolve("kept.txt"), "kept");
        var storage = new LocalImageStorage(directory.resolve("media"));

        storage.delete("https://cdn.example.com/image.png");
        storage.delete("/media/../kept.txt");
        storage.delete(null);

        assertThat(outside).exists();
    }

}
