package valthorne.ui.behavior.file;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Shares file explorer copy and cut selections with the operating system clipboard.
 *
 * <p>Published selections are immutable snapshots, so changing UI selection does
 * not change a pending paste. Standard file-list data works with native explorers;
 * Windows' Preferred DropEffect distinguishes externally copied and cut files.
 * Plain text is also published for applications that accept path text.</p>
 *
 * <p>Call clipboard operations from the UI thread. Native ownership notifications
 * may arrive on the AWT event thread, so only the ownership reference is volatile.
 * File data is read on demand rather than polled each frame. Clipboard contention
 * and inaccessible formats are reported as checked failures for the UI to show.</p>
 *
 * <pre>{@code
 * FileClipboard clipboard = new FileClipboard();
 * clipboard.cut(selectedPaths);
 * FileClipboardSelection selection = clipboard.readSelection();
 * fileOperations.paste(selection.paths(), targetDirectory, selection.cut());
 * clipboard.clearIfMatches(selection.paths());
 * }</pre>
 */
public final class FileClipboard implements ClipboardOwner {

    private volatile FileTransferable ownedTransferable; // Current locally owned payload; null after another application takes ownership.

    /**
     * Registers the native Windows move/copy format without opening the clipboard.
     */
    public FileClipboard() {
        String operatingSystem = System.getProperty("os.name", "");
        if (operatingSystem.regionMatches(true, 0, "Windows", 0, 7)) {
            SystemFlavorMap flavors = (SystemFlavorMap) SystemFlavorMap.getDefaultFlavorMap();
            flavors.addUnencodedNativeForFlavor(FileTransferable.DROP_EFFECT_FLAVOR, "Preferred DropEffect");
            flavors.addFlavorForUnencodedNative("Preferred DropEffect", FileTransferable.DROP_EFFECT_FLAVOR);
        }
    }

    /**
     * Publishes a copy selection, replacing the current native clipboard contents.
     *
     * @param paths files or directories to copy; an empty selection clears the clipboard
     * @throws IOException if the native clipboard is busy or unavailable
     */
    public void copy(List<Path> paths) throws IOException {
        publish(paths, false);
    }

    /**
     * Publishes a cut selection without modifying any source files.
     *
     * @param paths files or directories to move on paste; an empty selection clears the clipboard
     * @throws IOException if the native clipboard is busy or unavailable
     */
    public void cut(List<Path> paths) throws IOException {
        publish(paths, true);
    }

    /**
     * Reads the native file-list selection, including selections copied in another explorer.
     *
     * @return immutable normalized absolute paths, or an empty list for non-file contents
     * @throws IOException if clipboard access fails or its advertised file data is malformed
     */
    public List<Path> read() throws IOException {
        return readFiles(clipboardContents());
    }

    /**
     * Reads file paths and their copy or cut intent from the same native contents snapshot.
     *
     * @return immutable coherent selection for one subsequently submitted paste operation
     * @throws IOException if the clipboard contents or preferred effect cannot be decoded
     */
    public FileClipboardSelection readSelection() throws IOException {
        Transferable contents = clipboardContents();
        List<Path> paths = readFiles(contents);
        return new FileClipboardSelection(paths, !paths.isEmpty() && preferredMove(contents));
    }

    /**
     * Decodes the standard file list from one captured clipboard payload.
     *
     * @param contents captured native contents, or null for an empty clipboard
     * @return immutable normalized file paths, or an empty list for non-file contents
     * @throws IOException if the advertised list is malformed or inaccessible
     */
    private List<Path> readFiles(Transferable contents) throws IOException {
        if (contents == null || !contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return List.of();
        Object data = transferData(contents, DataFlavor.javaFileListFlavor);
        if (!(data instanceof List<?> files))
            throw new IOException("The clipboard file list has an unsupported representation.");

        ArrayList<Path> paths = new ArrayList<>(files.size());
        for (Object entry : files) {
            if (!(entry instanceof File file))
                throw new IOException("The clipboard contains an invalid file-list entry.");
            try {
                paths.add(file.toPath().toAbsolutePath().normalize());
            } catch (InvalidPathException | SecurityException failure) {
                throw new IOException("The clipboard contains an invalid or inaccessible file path.", failure);
            }
        }
        return List.copyOf(paths);
    }

    /**
     * Reads whether the current native file selection requests a move.
     *
     * <p>Selections without a preferred effect are treated as copies. A locally
     * owned payload retains its cut state on platforms without Windows' format.</p>
     *
     * @return whether paste should move files
     * @throws IOException if clipboard access or preferred-effect decoding fails
     */
    public boolean isCut() throws IOException {
        return preferredMove(clipboardContents());
    }

    /**
     * Decodes move intent from the same payload that supplied a file selection.
     *
     * @param contents captured native contents, or null for an empty clipboard
     * @return whether the payload requests a move
     * @throws IOException if the preferred-effect stream is invalid or inaccessible
     */
    private boolean preferredMove(Transferable contents) throws IOException {
        if (contents == null || !contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false;
        FileTransferable owned = ownedTransferable;
        if (contents == owned) return owned.isCut();
        if (!contents.isDataFlavorSupported(FileTransferable.DROP_EFFECT_FLAVOR)) return false;

        Object data = transferData(contents, FileTransferable.DROP_EFFECT_FLAVOR);
        if (!(data instanceof InputStream stream))
            throw new IOException("The clipboard's preferred drop effect is not a binary stream.");
        try (stream) {
            byte[] effect = stream.readNBytes(4);
            if (effect.length != 4)
                throw new IOException("The clipboard's preferred drop effect is incomplete.");
            return (effect[0] & 2) != 0;
        }
    }

    /**
     * Clears the native clipboard and relinquishes this service's pending cut state.
     *
     * @throws IOException if the native clipboard is busy or unavailable
     */
    public void clear() throws IOException {
        try {
            systemClipboard().setContents(new StringSelection(""), null);
            ownedTransferable = null;
        } catch (IllegalStateException | SecurityException failure) {
            throw new IOException("The system clipboard could not be cleared.", failure);
        }
    }

    /**
     * Clears a successfully pasted cut selection while retaining different or copied clipboard contents.
     *
     * @param paths original immutable source snapshot used by the completed paste
     * @throws IOException if clipboard ownership cannot be read or cleared
     */
    public void clearIfMatches(List<Path> paths) throws IOException {
        Objects.requireNonNull(paths, "paths");
        Transferable contents = clipboardContents();
        if (preferredMove(contents) && readFiles(contents).equals(paths)) clear();
    }

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
        if (ownedTransferable == contents) ownedTransferable = null;
    }

    /**
     * Normalizes a selection before publishing one immutable native payload.
     *
     * @param paths requested file selection
     * @param cut whether the payload requests a move
     * @throws IOException if the native clipboard is unavailable
     */
    private void publish(List<Path> paths, boolean cut) throws IOException {
        Objects.requireNonNull(paths, "paths");
        if (paths.isEmpty()) {
            clear();
            return;
        }
        ArrayList<Path> normalized = new ArrayList<>(paths.size());
        for (Path path : paths)
            normalized.add(Objects.requireNonNull(path, "path").toAbsolutePath().normalize());
        FileTransferable payload = new FileTransferable(List.copyOf(normalized), cut);
        try {
            systemClipboard().setContents(payload, this);
            ownedTransferable = payload;
        } catch (IllegalStateException | SecurityException failure) {
            throw new IOException("The file selection could not be placed on the system clipboard.", failure);
        }
    }

    /**
     * Obtains the platform clipboard while translating unavailable desktop access into a UI error.
     *
     * @return system clipboard
     * @throws IOException if desktop clipboard access is unavailable
     */
    private Clipboard systemClipboard() throws IOException {
        try {
            return Toolkit.getDefaultToolkit().getSystemClipboard();
        } catch (UnsupportedOperationException | SecurityException failure) {
            throw new IOException("The system clipboard is unavailable.", failure);
        }
    }

    /**
     * Takes one native contents snapshot without relying on previous UI selection state.
     *
     * @return transferable contents, or null when the clipboard is empty
     * @throws IOException if the clipboard is busy or inaccessible
     */
    private Transferable clipboardContents() throws IOException {
        try {
            return systemClipboard().getContents(null);
        } catch (IllegalStateException | SecurityException failure) {
            throw new IOException("The system clipboard could not be read.", failure);
        }
    }

    /**
     * Reads an advertised clipboard representation while preserving native failure causes.
     *
     * @param contents current clipboard payload
     * @param flavor requested advertised representation
     * @return decoded transfer data
     * @throws IOException if the representation disappears or cannot be decoded
     */
    private Object transferData(Transferable contents, DataFlavor flavor) throws IOException {
        try {
            return contents.getTransferData(flavor);
        } catch (UnsupportedFlavorException | IllegalStateException | SecurityException failure) {
            throw new IOException("The clipboard file data could not be read.", failure);
        }
    }
}
