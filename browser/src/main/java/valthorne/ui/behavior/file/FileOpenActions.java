package valthorne.ui.behavior.file;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import valthorne.browser.BrowserFiles;

/**
 * Exposes browser-safe opening of workspace files as downloads. Browsers do not
 * permit OS associations, native editor dispatch, executable launches, or revealing
 * arbitrary OS paths. Capability methods let shared menus omit those actions;
 * explicitly requesting an unavailable action reports a checked failure.
 */
public final class FileOpenActions {
    /**
     * Creates a stateless browser file-opening service.
     */
    public FileOpenActions() {
    }

    /**
     * Reports support for exporting ordinary workspace files through a download.
     * @return true
     */
    public boolean isOpenSupported() {
        return true;
    }

    /**
     * Reports whether the browser can reveal an OS path in a native explorer.
     * @return false; shared workspace controls provide their own directory navigation
     */
    public boolean isRevealSupported() {
        return false;
    }

    /**
     * Reports whether native editor associations are available.
     * @return false
     */
    public boolean isEditSupported() {
        return false;
    }

    /**
     * Reports whether a native application chooser can be launched.
     * @return false
     */
    public boolean isApplicationChooserSupported() {
        return false;
    }

    /**
     * Downloads one workspace file using its leaf filename.
     * @param path existing virtual file
     * @throws IOException if the file is inaccessible or is a directory
     */
    public void open(Path path) throws IOException {
        BrowserFiles.download(path);
    }

    /**
     * Rejects requests to reveal arbitrary OS paths.
     * @param path requested path
     * @throws IOException always because browsers cannot reveal OS paths
     */
    public void reveal(Path path) throws IOException {
        throw new IOException("The browser cannot reveal operating system paths; navigate within the workspace instead");
    }

    /**
     * Rejects native editor dispatch.
     * @param path requested file
     * @throws IOException always because browser files have no native association
     */
    public void edit(Path path) throws IOException {
        throw new IOException("The browser cannot launch a native editor; download the file instead");
    }

    /**
     * Rejects native executable launches from the browser sandbox.
     * @param executable requested application
     * @param files requested selection
     * @return never returns
     * @throws IOException always because native processes are unavailable
     */
    public Process openIn(Path executable, List<Path> files) throws IOException {
        throw new IOException("Browsers cannot launch native applications");
    }

    /**
     * Rejects native application chooser launches.
     * @param path requested file
     * @return never returns
     * @throws IOException always because native processes are unavailable
     */
    public Process chooseDefaultApplication(Path path) throws IOException {
        throw new IOException("Browsers cannot launch the operating system application chooser");
    }
}
