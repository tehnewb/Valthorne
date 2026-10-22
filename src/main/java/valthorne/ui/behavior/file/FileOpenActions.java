package valthorne.ui.behavior.file;

import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Opens explorer selections using native desktop associations or an explicit application.
 *
 * <p>The service contains no shell command parsing. Explicit application launches
 * use one process argument per selected path, preserving spaces and preventing
 * filenames from becoming shell instructions. The Windows application chooser
 * is launched from the operating system's own System32 directory.</p>
 *
 * <p>Desktop support differs by platform. Capability methods allow menus to hide
 * unavailable actions, and checked failures allow the caller to display native
 * association or process errors. Run opening actions on a worker thread because
 * desktop association dispatch can block. No process is waited for or terminated
 * by this service; applications own their external lifecycle.</p>
 */
public final class FileOpenActions {

    /**
     * Creates a stateless service for native open and reveal actions.
     */
    public FileOpenActions() {
    }

    /**
     * Reports whether default file associations and native directory opening are available.
     *
     * @return whether the desktop supports OPEN
     */
    public boolean isOpenSupported() {
        return supports(Action.OPEN);
    }

    /**
     * Reports whether an entry can be revealed or its containing directory opened.
     *
     * @return whether BROWSE_FILE_DIR or OPEN is supported
     */
    public boolean isRevealSupported() {
        return supports(Action.BROWSE_FILE_DIR) || supports(Action.OPEN);
    }

    /**
     * Reports whether the native default editor association can be invoked.
     *
     * @return whether the desktop supports EDIT
     */
    public boolean isEditSupported() {
        return supports(Action.EDIT);
    }

    /**
     * Reports whether the Windows native application chooser is installed.
     *
     * @return whether this platform exposes an existing System32 OpenWith executable
     */
    public boolean isApplicationChooserSupported() {
        Path chooser = applicationChooser();
        return chooser != null && Files.isRegularFile(chooser);
    }

    /**
     * Opens one file or directory using its native default association.
     *
     * @param path existing file or directory to open
     * @throws IOException if the desktop action is unavailable or dispatch fails
     */
    public void open(Path path) throws IOException {
        Path entry = requireEntry(path);
        try {
            desktop(Action.OPEN).open(entry.toFile());
        } catch (UnsupportedOperationException | IllegalArgumentException | SecurityException failure) {
            throw new IOException("The entry could not be opened with its default application: " + entry, failure);
        }
    }

    /**
     * Reveals one entry in the native explorer, falling back to opening its containing folder.
     *
     * @param path existing file or directory to reveal
     * @throws IOException if native explorer dispatch is unavailable or fails
     */
    public void reveal(Path path) throws IOException {
        Path entry = requireEntry(path);
        try {
            if (supports(Action.BROWSE_FILE_DIR)) {
                desktop(Action.BROWSE_FILE_DIR).browseFileDirectory(entry.toFile());
                return;
            }
            Path parent = entry.getParent();
            desktop(Action.OPEN).open((parent == null ? entry : parent).toFile());
        } catch (UnsupportedOperationException | IllegalArgumentException | SecurityException failure) {
            throw new IOException("The entry could not be revealed in the system explorer: " + entry, failure);
        }
    }

    /**
     * Opens a file using the native default editing association.
     *
     * @param path existing file to edit
     * @throws IOException if the editing association is unavailable or fails
     */
    public void edit(Path path) throws IOException {
        Path entry = requireEntry(path);
        try {
            desktop(Action.EDIT).edit(entry.toFile());
        } catch (UnsupportedOperationException | IllegalArgumentException | SecurityException failure) {
            throw new IOException("The entry could not be opened with its default editor: " + entry, failure);
        }
    }

    /**
     * Launches an explicitly chosen native executable with each selected entry as an argument.
     *
     * @param executable existing executable application; Windows requires an executable .exe file
     * @param files nonempty immutable selection to open in that application
     * @return started process without waiting for application exit
     * @throws IOException if the application or any selection is unavailable, or process launch fails
     */
    public Process openIn(Path executable, List<Path> files) throws IOException {
        Path application = requireEntry(executable);
        if (!Files.isRegularFile(application) || !Files.isExecutable(application))
            throw new IOException("Choose an executable application: " + application);
        String applicationName = application.getFileName()
                .toString();
        if (isWindows() && !applicationName.regionMatches(true, applicationName.length() - 4, ".exe", 0, 4))
            throw new IOException("Choose a native Windows application with an .exe extension.");
        Objects.requireNonNull(files, "files");
        if (files.isEmpty()) throw new IOException("Select at least one file to open.");
        ArrayList<String> arguments = new ArrayList<>(files.size() + 1);
        arguments.add(application.toString());
        for (Path file : files) arguments.add(requireEntry(file).toString());
        try {
            return new ProcessBuilder(arguments)
                    .redirectOutput(Redirect.DISCARD)
                    .redirectError(Redirect.DISCARD)
                    .start();
        } catch (SecurityException failure) {
            throw new IOException("The selected application could not be started: " + application, failure);
        }
    }

    /**
     * Shows the native Windows chooser for opening one file with another application.
     *
     * @param path existing file whose association should be chosen
     * @return started native chooser process without waiting for its exit
     * @throws IOException if the chooser is unavailable, the selection is a directory, or launch fails
     */
    public Process chooseDefaultApplication(Path path) throws IOException {
        Path entry = requireEntry(path);
        if (Files.isDirectory(entry))
            throw new IOException("Select a file to choose an opening application.");
        Path chooser = applicationChooser();
        if (chooser == null || !Files.isRegularFile(chooser))
            throw new IOException("The native application chooser is unavailable on this platform.");
        try {
            return new ProcessBuilder(chooser.toString(), entry.toString())
                    .redirectOutput(Redirect.DISCARD)
                    .redirectError(Redirect.DISCARD)
                    .start();
        } catch (SecurityException failure) {
            throw new IOException("The native application chooser could not be started.", failure);
        }
    }

    /**
     * Checks one native desktop capability without constructing unrelated UI state.
     *
     * @param action native action requested by a menu entry
     * @return whether the active platform desktop supports that action
     */
    private boolean supports(Action action) {
        return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(action);
    }

    /**
     * Acquires the desktop only after verifying the requested action's availability.
     *
     * @param action requested native action
     * @return desktop capable of performing the requested action
     * @throws IOException if no compatible desktop is available
     */
    private Desktop desktop(Action action) throws IOException {
        if (!Desktop.isDesktopSupported())
            throw new IOException("The system desktop is unavailable.");
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(action))
            throw new IOException("The system desktop does not support " + action + '.');
        return desktop;
    }

    /**
     * Normalizes and verifies a native opening target.
     *
     * @param path requested file or directory
     * @return existing normalized absolute target
     * @throws IOException if the target is unavailable
     */
    private Path requireEntry(Path path) throws IOException {
        Path entry = Objects.requireNonNull(path, "path")
                .toAbsolutePath()
                .normalize();
        Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return entry;
    }

    /**
     * Locates the operating system's chooser without searching the process PATH.
     *
     * @return absolute System32 chooser path, or null on unsupported platforms
     */
    private Path applicationChooser() {
        if (!isWindows()) return null;
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot == null || systemRoot.isBlank()) return null;
        Path root = Path.of(systemRoot);
        if (!root.isAbsolute()) return null;
        return root.resolve("System32")
                .resolve("OpenWith.exe");
    }

    /**
     * Detects the platform whose native chooser and executable restrictions apply.
     *
     * @return whether the operating system identifies itself as Windows
     */
    private boolean isWindows() {
        return System.getProperty("os.name", "").regionMatches(true, 0, "Windows", 0, 7);
    }
}
