package valthorne.math.physics;

import com.github.stephengold.joltjni.Jolt;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Initializes the shared Jolt native runtime used by Valthorne physics worlds.
 * Resolves a bundled platform library, extracts it to a temporary directory,
 * loads it, and registers the default allocator, tracing callback, factory and
 * physics types. Individual worlds own their simulation resources independently;
 * closing a world does not shut down this shared runtime.
 *
 * <p>Initialization is synchronized on this class and skipped after successful
 * completion. The success flag is set only after all registration steps finish.
 * Failure does not roll back native registration already performed, so a later
 * call is a retry rather than a guaranteed clean start.</p>
 *
 * <p>Platform detection recognizes Windows, macOS and Linux, with x86-64 and
 * AArch64 aliases and Linux-only ARMHF support. A matching native resource must
 * still be present in the runtime dependencies. Extracted files are scheduled
 * for deletion at JVM exit, not removed when an individual world closes.</p>
 *
 * @author Albert Beaupre
 */
final class JoltRuntime {
    /**
     * Records successful completion of native loading and type registration.
     * Access is guarded by the synchronized initialization method; failures leave
     * this false even when some native setup has already occurred.
     */
    private static boolean initialized;

    /**
     * Prevents construction of the runtime bootstrap utility. Physics worlds
     * access shared initialization through the static entry point.
     */
    private JoltRuntime() {}

    /**
     * Initializes native physics once for this loaded runtime class. Reads
     * {@code os.name} and {@code os.arch}, selects the bundled library resource,
     * copies it into a unique temporary directory, and loads the absolute path
     * through {@link System#load(String)}. Default Jolt registration follows.
     *
     * <p>Subsequent successful calls return immediately. The resource stream is
     * closed on all exits. Extraction I/O failures are wrapped with context;
     * native loading errors propagate unchanged. No OpenGL context is needed.</p>
     *
     * @throws UnsupportedOperationException if the detected platform or CPU is unsupported
     * @throws IllegalStateException         if the native resource is absent or factory creation fails
     * @throws UncheckedIOException          if reading or extracting the library fails
     * @throws UnsatisfiedLinkError          if the JVM cannot load or link the extracted library
     * @throws SecurityException             if runtime permissions deny a required property, file or load operation
     */
    static synchronized void initialize() {
        if (initialized) return;
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        String platform = os.contains("win") ? "windows" : os.contains("mac") ? "osx" : os.contains("linux") ? "linux" : null;
        String cpu = switch (arch) {
            case "amd64", "x86_64" -> "x86-64";
            case "aarch64", "arm64" -> "aarch64";
            case "arm", "arm32" -> "armhf";
            default -> null;
        };
        if (platform == null || cpu == null || (cpu.equals("armhf") && !platform.equals("linux")))
            throw new UnsupportedOperationException("Jolt native library unavailable for " + os + "/" + arch);
        String filename = platform.equals("windows") ? "joltjni.dll" : platform.equals("osx") ? "libjoltjni.dylib" : "libjoltjni.so";
        String resource = "/" + platform + "/" + cpu + "/com/github/stephengold/" + filename;
        try (InputStream input = JoltRuntime.class.getResourceAsStream(resource)) {
            if (input == null)
                throw new IllegalStateException("Missing Jolt native resource " + resource + "; include the matching ReleaseSp runtime dependency");
            Path directory = Files.createTempDirectory("valthorne-jolt-");
            directory.toFile().deleteOnExit();
            Path library = directory.resolve(filename);
            Files.copy(input, library);
            library.toFile().deleteOnExit();
            System.load(library.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to extract Jolt native library", e);
        }
        Jolt.registerDefaultAllocator();
        Jolt.installDefaultTraceCallback();
        if (!Jolt.newFactory()) throw new IllegalStateException("Unable to create Jolt factory");
        Jolt.registerTypes();
        initialized = true;
    }
}
