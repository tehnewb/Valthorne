package valthorne.ui.nodes.nano;

import java.nio.file.Path;

/**
 * Immutable directory-tree row metadata. Depth is relative to the application
 * root, so indentation is independent of a directory's absolute path length.
 * Snapshots borrow their immutable paths and never query the filesystem.
 *
 * @param path normalized absolute directory represented by this row
 * @param depth zero for a root, increasing for each visible expanded ancestor
 * @author Albert Beaupre
 */
public record NanoDirectoryBranch(Path path, int depth) {}
