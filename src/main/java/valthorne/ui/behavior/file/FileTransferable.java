package valthorne.ui.behavior.file;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable native clipboard payload for a file explorer's copy or cut selection.
 *
 * <p>The standard Java file-list flavor interoperates with the system explorer.
 * Text consumers receive one absolute path per line. Windows also receives its
 * four-byte preferred drop effect, allowing a pasted cut selection to be moved.
 * Each stream request owns its cursor; the published selection never changes.</p>
 */
final class FileTransferable implements Transferable {

    /**
     * Binary representation of Windows' native Preferred DropEffect format.
     */
    static final DataFlavor DROP_EFFECT_FLAVOR = new DataFlavor("application/x-valthorne-drop-effect;class=java.io.InputStream", "Preferred DropEffect");

    /**
     * Available clipboard representations, ordered from files to plain text.
     */
    private static final DataFlavor[] FLAVORS = {DataFlavor.javaFileListFlavor, DROP_EFFECT_FLAVOR, DataFlavor.stringFlavor};

    private final List<File> files; // Immutable native file-list representation.
    private final String text; // Absolute paths separated by the platform line separator.
    private final boolean cut; // Whether a successful paste should move the source selection.

    /**
     * Prepares all non-stream clipboard representations once per user operation.
     *
     * @param paths immutable normalized absolute paths
     * @param cut whether this payload requests a move rather than a copy
     */
    FileTransferable(List<Path> paths, boolean cut) {
        this.cut = cut;
        ArrayList<File> nativeFiles = new ArrayList<>(paths.size());
        StringBuilder pathText = new StringBuilder();
        for (int i = 0; i < paths.size(); i++) {
            Path path = paths.get(i);
            nativeFiles.add(path.toFile());
            if (i > 0) pathText.append(System.lineSeparator());
            pathText.append(path);
        }
        files = List.copyOf(nativeFiles);
        text = pathText.toString();
    }

    /**
     * Identifies a locally owned cut operation without re-reading native bytes.
     *
     * @return whether the payload requests a move
     */
    boolean isCut() {
        return cut;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return FLAVORS.clone();
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        for (DataFlavor supported : FLAVORS)
            if (supported.equals(flavor)) return true;
        return false;
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (DataFlavor.javaFileListFlavor.equals(flavor)) return files;
        if (DataFlavor.stringFlavor.equals(flavor)) return text;
        if (DROP_EFFECT_FLAVOR.equals(flavor))
            return new ByteArrayInputStream(new byte[]{(byte) (cut ? 2 : 1), 0, 0, 0});
        throw new UnsupportedFlavorException(flavor);
    }
}
