package valthorne.android.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads ordinary classpath resources and application-accessible Android files.
 * Absolute filesystem paths remain filesystem paths; relative resources are
 * resolved through the same application class loader used by asset parameters.
 */
public final class AndroidAssets {
    /**
     * Prevents constructing a stateless asset reader.
     */
    private AndroidAssets() {
    }

    /**
     * Reads one encoded resource without touching the graphics device.
     * @param name classpath resource or application-accessible path
     * @return independent bytes
     * @throws IOException if neither resource nor file can be read
     */
    public static byte[] read(String name) throws IOException {
        String resource = name.startsWith("/") ? name.substring(1) : name;
        try (InputStream input = AndroidAssets.class.getClassLoader().getResourceAsStream(resource)) {
            if (input != null) return input.readAllBytes();
        }
        return Files.readAllBytes(Path.of(name));
    }
}
