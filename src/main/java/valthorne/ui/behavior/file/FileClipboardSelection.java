package valthorne.ui.behavior.file;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of file paths and their native clipboard transfer intent.
 *
 * <p>Reading both values together prevents a subsequent clipboard change from
 * applying another selection's cut flag to these paths. The native clipboard
 * service supplies normalized absolute paths; arbitrary constructed snapshots
 * retain their caller-provided paths. Empty snapshots always describe a copy.</p>
 *
 * @param paths immutable source paths in clipboard order
 * @param cut whether paste should move the selection rather than copy it
 */
public record FileClipboardSelection(List<Path> paths, boolean cut) {

    /**
     * Retains an immutable path snapshot without allowing null entries.
     *
     * @param paths source paths in selection order
     * @param cut whether these paths represent a cut selection
     */
    public FileClipboardSelection {
        paths = List.copyOf(Objects.requireNonNull(paths, "paths"));
        cut = cut && !paths.isEmpty();
    }
}
