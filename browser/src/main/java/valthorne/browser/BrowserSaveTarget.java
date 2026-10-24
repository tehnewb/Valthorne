package valthorne.browser;

import java.nio.file.Path;

/**
 * Tracks the last exported version of one explicitly selected browser destination.
 * Confined to the application coroutine; stable file versions need only metadata checks.
 */
final class BrowserSaveTarget {
    final Path path; // Workspace file whose changes should be exported.
    long modified = Long.MIN_VALUE; // Modification time of the last exported version.
    long size = -1; // Byte length of the last exported version.
    long readTime = Long.MIN_VALUE; // Wall-clock tick when the last snapshot was read.

    /**
     * Registers a destination before the application creates or overwrites its file.
     * @param path normalized workspace file
     */
    BrowserSaveTarget(Path path) {
        this.path = path;
    }
}
