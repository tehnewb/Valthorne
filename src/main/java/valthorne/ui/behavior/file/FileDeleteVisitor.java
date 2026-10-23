package valthorne.ui.behavior.file;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Permanently removes one explicitly selected tree after visiting its children.
 *
 * <p>The caller must traverse without FOLLOW_LINKS. A symbolic link is then
 * removed as a file entry, leaving its target untouched. All failures propagate
 * immediately so a caller can report partial completion.</p>
 */
final class FileDeleteVisitor extends SimpleFileVisitor<Path> {

    /**
     * Creates a visitor for a confirmed deletion or a completed cross-volume move.
     */
    FileDeleteVisitor() {
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
        if (failure != null) throw failure;
        Files.delete(directory);
        return FileVisitResult.CONTINUE;
    }
}
