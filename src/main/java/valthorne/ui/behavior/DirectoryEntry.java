package valthorne.ui.behavior;

import java.nio.file.Path;

/**
 * Immutable filesystem listing metadata. Paths are revalidated on activation;
 * this snapshot does not own a directory stream or guarantee continued existence.
 *
 * @param path entry path captured during the scan
 * @param directory whether the scan classified this entry as a directory
 */
public record DirectoryEntry(Path path, boolean directory) {
}
