package valthorne.graphics.font.slug;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import valthorne.asset.Assets;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

final class SlugLoaderTest {

    @TempDir Path directory;

    @Test
    void byteSourcesAndLoadedDataAreDefensive() {
        byte[] input = {1, 2, 3};
        SlugData data = new SlugLoader().load(SlugParameters.fromBytes(input, "memory", 40, 12));
        input[0] = 9;

        byte[] first = data.bytes();
        byte[] second = data.bytes();
        first[1] = 9;

        assertArrayEquals(new byte[]{1, 2, 3}, second);
        assertNotSame(first, second);
        assertEquals(40, data.firstCodepoint());
        assertEquals(12, data.characterCount());
    }

    @Test
    void pathSourceLoadsThroughDefaultAssetsRegistration() throws Exception {
        Path font = directory.resolve("font.ttf");
        Files.write(font, new byte[]{4, 5, 6, 7});
        String key = "slug-loader-test-" + System.nanoTime();

        try {
            SlugData data = Assets.loadAsync(
                    SlugParameters.fromPath(font.toString(), key, 32, 95), SlugData.class).join();
            assertArrayEquals(new byte[]{4, 5, 6, 7}, data.bytes());
            assertEquals(data, Assets.get(key, SlugData.class));
        } finally {
            Assets.unload(key);
        }
    }
}
