package valthorne.ui.behavior.file;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Copies one file tree without following links or replacing destination entries.
 *
 * <p>Directories are created before their children and retain their source
 * modification times after copying. Regular files retain basic attributes through
 * the filesystem provider's copy operation. Link entries remain links.</p>
 */
final class FileCopyVisitor extends SimpleFileVisitor<Path> {

    private final Path source; // Original root used to calculate each relative destination.
    private final Path destination; // Unused destination root of the complete copy.

    /**
     * Binds a new visitor to one explicit source/destination pair.
     *
     * @param source normalized source root
     * @param destination unused normalized destination root
     */
    FileCopyVisitor(Path source, Path destination) {
        this.source = source;
        this.destination = destination;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
        Files.createDirectory(destination.resolve(source.relativize(directory)));
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
        Files.copy(file, destination.resolve(source.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
        if (failure != null) throw failure;
        Files.setLastModifiedTime(destination.resolve(source.relativize(directory)), Files.getLastModifiedTime(directory, LinkOption.NOFOLLOW_LINKS));
        return FileVisitResult.CONTINUE;
    }
}
