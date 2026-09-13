package valthorne.graphics;

import java.util.Locale;

/** Native runtimes actually published by the pinned Filament FFM release. */
public final class FilamentPlatform {
    private FilamentPlatform() {}
    public static boolean supported(String os, String arch) {
        os = os.toLowerCase(Locale.ROOT);
        arch = arch.toLowerCase(Locale.ROOT);
        boolean x64 = arch.equals("amd64") || arch.equals("x86_64");
        boolean arm64 = arch.equals("aarch64") || arch.equals("arm64");
        return os.startsWith("windows") && x64
                || os.startsWith("linux") && (x64 || arm64)
                || (os.startsWith("mac") || os.startsWith("darwin")) && arm64;
    }
    public static boolean supported() {
        return supported(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }
}
