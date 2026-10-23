package valthorne.browser;

import java.io.IOException;
import java.io.File;
import java.nio.file.attribute.FileTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

/**
 * Connects user-selected browser files to TeaVM's sandboxed filesystem. Imported
 * files and folders are independent workspace copies, not paths into the user's
 * machine. Shared file controls, NIO operations, and map loaders use those copies.
 *
 * <p>NativeFileDialog.save registers a destination before returning its workspace
 * path. Once the application writes that path, changed contents are flushed to a
 * granted File System Access handle, or downloaded once on browsers without that
 * API. Downloads require another Save operation for subsequent revisions. Writes
 * are checked at most four times per second and once at orderly engine shutdown.
 * Periodic scans check size and modification time without reading stable files.
 * Call flush(true) after writes that deliberately preserve both metadata values.
 * A successful flush means the browser accepted the write, not durable OS sync.</p>
 *
 * <p>Calls suspend their Java coroutine while browser I/O proceeds. Failures are
 * propagated, picker cancellation returns null, and session shutdown cancels picker
 * UI. Application-created workspace files can also be downloaded explicitly.</p>
 *
 * <pre>{@code
 * Path output = NativeFileDialog.builder()
 *         .title("Save level")
 *         .build()
 *         .save();
 * if (output != null) {
 *     Files.write(output, encodedLevel);
 *     BrowserFiles.flush(true);
 * }
 * BrowserFiles.download(BrowserFiles.home().resolve("snapshot.bin"));
 * }</pre>
 */
public final class BrowserFiles {
    /**
     * Outstanding save destinations, confined to the application coroutine.
     */
    private static final ArrayList<BrowserSaveTarget> targets = new ArrayList<>();
    /**
     * Earliest monotonic time for the next periodic export scan.
     */
    private static long nextFlush;
    /**
     * Browser session owning the pending destinations.
     */
    private static int ownerSession;

    /**
     * Prevents constructing stateless workspace services.
     */
    private BrowserFiles() {
    }

    /**
     * Returns the session workspace, creating its root before file controls scan it.
     * @return absolute sandbox directory
     */
    public static Path home() {
        /*
         * The workspace is virtual; creating it cannot enumerate or modify OS files.
         */
        Path root = Path.of("/workspace");
        try {
            createDirectories(root);
            createDirectories(Path.of("/tmp"));
        } catch (IOException failure) {
            throw new IllegalStateException("Could not create browser workspace", failure);
        }
        return root;
    }

    /**
     * Ensures a virtual directory exists, preserving idempotent NIO behavior.
     * @param directory requested virtual directory
     * @return requested path after creation or an existing-directory check
     * @throws IOException if the path is a file or cannot be created
     */
    public static Path createDirectories(Path directory) throws IOException {
        /*
         * TeaVM's provider throws for an already existing final directory. Avoid
         * that provider path while preserving errors for existing regular files.
         */
        return Files.isDirectory(directory) ? directory : Files.createDirectories(directory);
    }

    /**
     * Opens a cancellable browser picker and imports the selected file or folder.
     * @param mode zero for Open, one for Save, two for folder import
     * @param title visible caption
     * @param initial suggested filename or workspace path
     * @param patterns configured filename patterns
     * @return absolute workspace path, or null on cancellation
     * @throws IOException on picker, import, or permission failure
     */
    public static Path choose(int mode, String title, String initial, String[] patterns) throws IOException {
        /*
         * Picker results contain only explicitly selected files. Verify normalized
         * paths again before accepting them into the workspace namespace.
         */
        if (mode < 0 || mode > 2) throw new IllegalArgumentException("Unknown browser file dialog mode");
        ensureSession();
        JSObject selection = request(mode, title, initial, String.join("\n", patterns));
        if (selection == null) return null;
        Path root = home();
        int count = count(selection);
        for (int i = 0; i < count; i++) {
            Path path = checked(root, entryPath(selection, i));
            byte[] bytes = entryBytes(selection, i);
            if (bytes == null) createDirectories(path);
            else {
                createDirectories(path.getParent());
                Files.write(path, bytes);
            }
        }
        Path selected = checked(root, selected(selection));
        if (mode == 1) {
            createDirectories(selected.getParent());
            targets.add(new BrowserSaveTarget(selected));
        }
        return selected;
    }

    /**
     * Downloads a current workspace file snapshot under its leaf filename.
     * @param path existing sandbox file
     * @throws IOException if the file cannot be read or download dispatch fails
     */
    public static void download(Path path) throws IOException {
        /*
         * Read only the application-visible workspace, never a native OS path.
         */
        Path source = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) throw new IOException("Select an existing workspace file");
        download(source.getFileName().toString(), Files.readAllBytes(source));
    }

    /**
     * Flushes changed registered save files, preserving failed targets for diagnostics.
     * @param force whether to bypass periodic throttling during orderly shutdown
     * @throws IOException if a chosen destination rejects a write
     */
    public static void flush(boolean force) throws IOException {
        /*
         * Stable destinations require only metadata queries, with no file-sized
         * allocations or retained duplicate contents. Recheck a shared clock tick
         * once so same-size edits immediately following a read are not missed.
         */
        ensureSession();
        if (targets.isEmpty()) return;
        long now = System.nanoTime();
        if (!force && now < nextFlush) return;
        nextFlush = now + 250_000_000L;
        for (int i = 0; i < targets.size();) {
            BrowserSaveTarget target = targets.get(i);
            if (Files.isRegularFile(target.path)) {
                long modified = Files.getLastModifiedTime(target.path).toMillis();
                long size = Files.size(target.path);
                if (force || modified != target.modified || size != target.size || modified >= target.readTime) {
                    long readTime = System.currentTimeMillis();
                    byte[] bytes = Files.readAllBytes(target.path);
                    write(target.path.toString(), bytes);
                    target.modified = modified;
                    target.size = size;
                    target.readTime = readTime;
                    if (!persistent(target.path.toString())) {
                        targets.remove(i);
                        continue;
                    }
                }
            }
            i++;
        }
    }

    /**
     * Drops Java snapshots when a new browser application session starts.
     */
    private static void ensureSession() {
        /*
         * A relaunch must not retain prior page grants or export old application files.
         */
        int session = session();
        if (session != ownerSession) {
            targets.clear();
            nextFlush = 0;
            ownerSession = session;
        }
    }

    /**
     * Validates that a picker-provided path remains in the virtual workspace.
     * @param root workspace namespace
     * @param value browser-provided path
     * @return normalized absolute workspace path
     * @throws IOException if the path escapes the workspace
     */
    private static Path checked(Path root, String value) throws IOException {
        /*
         * Normalize before the prefix check so malicious relative components cannot
         * make an imported entry overwrite another virtual filesystem namespace.
         */
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!path.startsWith(root) || path.equals(root)) throw new IOException("Browser selection escaped the workspace");
        return path;
    }

    /**
     * Preserves copied directory timestamps through TeaVM's virtual file provider.
     * @param path existing workspace entry
     * @param time desired modification timestamp
     * @return the same path after its timestamp was updated
     * @throws IOException if the provider rejects the timestamp
     */
    public static Path setLastModifiedTime(Path path, FileTime time) throws IOException {
        /*
         * TeaVM exposes timestamp mutation through java.io.File rather than the
         * corresponding NIO convenience method. Both use the same virtual provider.
         */
        if (!new File(path.toString()).setLastModified(time.toMillis()))
            throw new IOException("Could not preserve workspace timestamp: " + path);
        return path;
    }

    /**
     * Identifies the browser host owning file grants and clipboard selections.
     * @return current session identity, or zero before browser bootstrap
     */
    @JSBody(script = "return globalThis.valthorneHost ? globalThis.valthorneHost.files.session : 0;")
    public static native int session();

    /**
     * Suspends until the user selects or cancels a browser picker.
     * @param mode picker operation
     * @param title visible caption
     * @param initial suggested path
     * @param patterns newline-delimited filename patterns
     * @return selection container or null
     * @throws IOException on browser failure
     */
    @Async
    private static native JSObject request(int mode, String title, String initial, String patterns) throws IOException;

    /**
     * Converts browser picker completion into a checked Java continuation.
     * @param mode picker operation
     * @param title visible caption
     * @param initial suggested path
     * @param patterns newline-delimited patterns
     * @param callback suspended Java caller
     */
    private static void request(int mode, String title, String initial, String patterns, AsyncCallback<JSObject> callback) {
        /*
         * A null selection is cancellation; permission and I/O failures remain errors.
         */
        request(mode, title, initial, patterns, (result, error) -> {
            if (error == null) callback.complete(result);
            else callback.error(new IOException("Browser file selection failed: " + error));
        });
    }

    /**
     * Starts the host-owned file picker.
     * @param mode operation
     * @param title caption
     * @param initial filename hint
     * @param patterns newline-delimited patterns
     * @param callback one-shot completion
     */
    @JSBody(params = {"mode", "title", "initial", "patterns", "callback"}, script = "globalThis.valthorneHost.files.choose(mode,title,initial,patterns.split('\\n')).then(result=>callback(result,null),error=>callback(null,String(error)));")
    private static native void request(int mode, String title, String initial, String patterns, BrowserFileResult callback);

    /**
     * Reads an imported entry count.
     * @param selection completed picker result
     * @return entry count
     */
    @JSBody(params = "selection", script = "return selection.entries.length;")
    private static native int count(JSObject selection);

    /**
     * Reads an imported entry path.
     * @param selection completed picker result
     * @param index entry index
     * @return virtual path
     */
    @JSBody(params = {"selection", "index"}, script = "return selection.entries[index].path;")
    private static native String entryPath(JSObject selection, int index);

    /**
     * Copies selected bytes into Java storage, or identifies a directory with null.
     * @param selection completed picker result
     * @param index entry index
     * @return file bytes or null for a directory
     */
    @JSBody(params = {"selection", "index"}, script = "return selection.entries[index].bytes;")
    private static native byte[] entryBytes(JSObject selection, int index);

    /**
     * Reads the selected virtual file or directory.
     * @param selection completed picker result
     * @return selected virtual path
     */
    @JSBody(params = "selection", script = "return selection.selected;")
    private static native String selected(JSObject selection);

    /**
     * Reports whether a destination retains a browser file handle after a write.
     * @param path registered save path
     * @return whether further writes can reuse the granted handle
     */
    @JSBody(params = "path", script = "var value=globalThis.valthorneHost.files.destinations.get(path); return !!(value && value.handle);")
    private static native boolean persistent(String path);

    /**
     * Dispatches a snapshot download.
     * @param name leaf filename
     * @param bytes owned file snapshot
     */
    @JSBody(params = {"name", "bytes"}, script = "globalThis.valthorneHost.files.download(name,new Uint8Array(bytes));")
    private static native void download(String name, byte[] bytes);

    /**
     * Suspends until one granted file write completes.
     * @param path registered save path
     * @param bytes snapshot retained until completion
     * @throws IOException on write failure
     */
    @Async
    private static native void write(String path, byte[] bytes) throws IOException;

    /**
     * Connects write completion to its suspended Java caller.
     * @param path registered save path
     * @param bytes snapshot to write
     * @param callback suspended caller
     */
    private static void write(String path, byte[] bytes, AsyncCallback<Void> callback) {
        /*
         * Publish the exported version only after the browser accepts its bytes.
         */
        write(path, bytes, error -> {
            if (error == null) callback.complete(null);
            else callback.error(new IOException("Browser file write failed: " + error));
        });
    }

    /**
     * Starts one asynchronous write to the user-selected browser destination.
     * @param path registered save path
     * @param bytes owned source snapshot
     * @param callback one-shot write completion
     */
    @JSBody(params = {"path", "bytes", "callback"}, script = "globalThis.valthorneHost.files.write(path,new Uint8Array(bytes)).then(()=>callback(null),error=>callback(String(error)));")
    private static native void write(String path, byte[] bytes, BrowserFileWritten callback);
}
