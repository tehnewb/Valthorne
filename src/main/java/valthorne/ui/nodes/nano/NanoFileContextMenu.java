package valthorne.ui.nodes.nano;

import valthorne.JGL;
import valthorne.Keyboard;
import valthorne.WindowContext;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.graphics.Color;
import valthorne.ui.UIInputEvent;
import valthorne.ui.UINode;
import valthorne.ui.UIRoot;
import valthorne.ui.behavior.TextEditing;
import valthorne.ui.behavior.file.FileClipboard;
import valthorne.ui.behavior.file.FileClipboardSelection;
import valthorne.ui.behavior.file.FileCommand;
import valthorne.ui.behavior.file.FileOpenActions;
import valthorne.ui.behavior.file.FileOperations;
import valthorne.ui.behavior.file.FileOperationHistory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Shared file-explorer command controller for directory trees and asset listings.
 * Each opening captures its selection and destination, preserving multi-selection
 * without treating a right press as navigation. Commands provide native file
 * clipboard copy/cut, paste, creation, rename, trash/deletion, reveal, and Open In.
 * Applications can register their own compatible Open In handlers.
 *
 * <p>Creation and rename use an editable Nano dialog. Deletion always presents
 * the actual selected names and whether Trash or permanent deletion will be used.
 * Protected roots cannot be cut, renamed, or deleted. An optional confined root
 * limits destinations and browser-owned mutations. Native clipboard imports can
 * copy or move external files, but cannot move a protected root or its parents.
 * Paste preserves both files on collisions.</p>
 *
 * <p>Undo and redo share this controller's session history across its tree and
 * listing. Ctrl+Z reverses file mutations; Ctrl+Y or Ctrl+Shift+Z reapplies them.
 * Copy/import, move, rename, creation, and Trash deletion are reversible. Copying
 * clipboard data and folder navigation do not create history. Permanent deletion
 * clears history and remains irreversible. Recovery copies are retained in private
 * temporary storage; replay never overwrites an unrelated destination.</p>
 *
 * <p>Disk transfers and native application dispatch run on a lazily started
 * worker. UI updates are delivered through the engine queue, and commands are
 * disabled during an operation. There is no filesystem access during drawing.
 * Call {@link #update()} from the owner's update and {@link #hide()} before it
 * closes or detaches. This controller owns only transient menu/dialog controls;
 * it never owns or disposes the owner's theme or root.</p>
 *
 * <pre>{@code
 * NanoFileContextMenu commands = new NanoFileContextMenu(browser, browser::getSelectedPaths, browser::getDirectory)
 *         .onChanged(this::refreshView)
 *         .onOpen(path -> inspect(path));
 * commands.openIn("Animation Editor", editor::acceptsAsset, editor::dropAsset);
 * }</pre>
 */
public final class NanoFileContextMenu {

    /**
     * Shared fallback validation ink for detached or unthemed dialog creation.
     */
    private static final Color ERROR_INK = new Color(0xFFF87171);

    private final UINode owner; // Borrowed UI node controlling scope and lifetime.
    private final Supplier<List<Path>> selection; // Current keyboard-command selection supplied by the owner.
    private final Supplier<Path> destination; // Current creation/paste directory supplied by the owner.
    private final NanoPopupMenu popup = new NanoPopupMenu(); // Owned transient command overlay.
    private final FileClipboard clipboard = new FileClipboard(); // Shared native file clipboard access.
    private final FileOperations operations = new FileOperations(); // Stateless checked file operations.
    private final FileOperationHistory history = new FileOperationHistory(); // Session mutation history, serialized by the command worker.
    private final FileOpenActions applications = new FileOpenActions(); // Stateless native application dispatch.
    private final ArrayList<NanoFileOpenTarget> targets = new ArrayList<>(); // Application-owned Open In registrations.
    private List<Path> protectedPaths = List.of(); // Roots that may not be removed, cut, or renamed.
    private Path confinedRoot; // Optional normalized browsing root; null allows ordinary native explorer operations.
    private Runnable changed = () -> {}; // UI-thread refresh notification after disk changes, including partial failures.
    private Consumer<Path> opened; // Optional UI-thread application open handler; null uses native association.
    private Consumer<IOException> failed = failure -> {}; // UI-thread application failure report after inline dialog presentation.
    private Consumer<String> status = message -> {}; // Optional UI-thread operation feedback.
    private UIRoot visibleRoot; // Root owning current overlays, or null when none have opened.
    private NanoModal dialog; // Owned name, confirmation, or failure dialog, or null while absent.
    private NanoFileChooser chooser; // Optional executable chooser retained while its dialog is open.
    private ArrayList<Path> pendingDefaultPaths; // Lazily allocated UI-thread native-open batch; null until first use.
    private boolean defaultOpenQueued; // Whether one engine-queue task is scheduled to drain pending native opens.
    private boolean busy; // UI-thread command state; worker completion clears it through the engine queue.

    /**
     * Retains an owner and dynamic keyboard-command sources without reading files.
     *
     * @param owner attached UI node hosting menus and dialogs
     * @param selection current selected paths; never returns null
     * @param destination current destination directory, or null when unavailable
     */
    public NanoFileContextMenu(UINode owner, Supplier<List<Path>> selection, Supplier<Path> destination) {
        this.owner = Objects.requireNonNull(owner);
        this.selection = Objects.requireNonNull(selection);
        this.destination = Objects.requireNonNull(destination);
    }

    /**
     * Installs UI refresh after filesystem changes; clipboard commands do not refresh.
     *
     * @param listener nonnull UI-thread callback
     * @return this controller
     */
    public NanoFileContextMenu onChanged(Runnable listener) {
        changed = Objects.requireNonNull(listener);
        return this;
    }

    /**
     * Installs application opening instead of native desktop association.
     *
     * @param listener nonnull UI-thread file or directory handler
     * @return this controller
     */
    public NanoFileContextMenu onOpen(Consumer<Path> listener) {
        opened = Objects.requireNonNull(listener);
        return this;
    }

    /**
     * Installs application failure reporting without suppressing dialog feedback.
     *
     * @param listener nonnull checked-failure callback
     * @return this controller
     */
    public NanoFileContextMenu onError(Consumer<IOException> listener) {
        failed = Objects.requireNonNull(listener);
        return this;
    }

    /**
     * Installs concise progress and completion text for an owner's status bar.
     *
     * @param listener nonnull UI-thread text consumer
     * @return this controller
     */
    public NanoFileContextMenu onStatus(Consumer<String> listener) {
        status = Objects.requireNonNull(listener);
        return this;
    }

    /**
     * Reports accepted worker activity without filesystem or clipboard access.
     *
     * @return whether a file command is awaiting UI-thread completion
     */
    public boolean isBusy() {
        return busy;
    }

    /**
     * Reports backward history availability without disk access.
     *
     * @return whether undo can currently be requested
     */
    public boolean canUndo() {
        return !busy && !history.undoName().isEmpty();
    }

    /**
     * Reports forward history availability without disk access.
     *
     * @return whether redo can currently be requested
     */
    public boolean canRedo() {
        return !busy && !history.redoName().isEmpty();
    }

    /**
     * Returns retained session recovery storage for explicit application housekeeping.
     * Do not remove it while undo or redo history remains in use.
     *
     * @return recovery directory, or null before storage has been allocated
     */
    public Path getRecoveryDirectory() {
        if (busy) throw new IllegalStateException("An operation is still running.");
        return history.getRecoveryDirectory();
    }

    /**
     * Requests backward replay and refreshes the owner after completion or failure.
     */
    public void undo() {
        if (canUndo()) run("Undo " + history.undoName(), history::undo, true, null);
    }

    /**
     * Requests forward replay and refreshes the owner after completion or failure.
     */
    public void redo() {
        if (canRedo()) run("Redo " + history.redoName(), history::redo, true, null);
    }

    /**
     * Limits browser-owned mutations and destination directories to this root.
     * Canonical containment is checked again at operation time.
     *
     * @param root nonnull application root
     * @return this controller
     * @throws IllegalStateException if an accepted file operation is still running
     */
    public NanoFileContextMenu confinedRoot(Path root) {
        if (busy) throw new IllegalStateException("An operation is still running.");
        history.forget();
        confinedRoot = Objects.requireNonNull(root)
                .toAbsolutePath()
                .normalize();
        return this;
    }

    /**
     * Protects root directories against cut, rename, and deletion, including parents
     * whose removal would contain a protected root.
     *
     * @param paths nonnull protected roots
     * @return this controller
     * @throws IllegalStateException if an accepted file operation is still running
     */
    public NanoFileContextMenu protectedPaths(List<Path> paths) {
        if (busy) throw new IllegalStateException("An operation is still running.");
        ArrayList<Path> normalized = new ArrayList<>(paths.size());
        for (Path path : paths) normalized.add(Objects.requireNonNull(path).toAbsolutePath().normalize());
        protectedPaths = List.copyOf(normalized);
        history.forget();
        return this;
    }

    /**
     * Registers an in-application destination alongside native Open In commands.
     *
     * @param name displayed destination label
     * @param accepts predicate matching one selected file
     * @param handler UI-thread handler, such as an animation editor's image loader
     * @return this controller
     */
    public NanoFileContextMenu openIn(String name, Predicate<Path> accepts, Consumer<Path> handler) {
        targets.add(new NanoFileOpenTarget(name, accepts, handler));
        return this;
    }

    /**
     * Queues one file for native opening, combining UI-thread calls into one worker operation.
     * Application handlers can use this fallback for file types they do not inspect
     * themselves. Multiple callbacks during one selection activation are captured
     * before the engine queue drains, so the first file does not disable later
     * native fallback requests. Status and failures are delivered on the UI thread.
     * Calls while this owner is unavailable or another operation is busy are ignored.
     *
     * @param path nonnull file or directory to open
     */
    public void openDefault(Path path) {
        Objects.requireNonNull(path);
        if (!active() || busy) return;
        if (pendingDefaultPaths == null) pendingDefaultPaths = new ArrayList<>(4);
        pendingDefaultPaths.add(path);
        if (defaultOpenQueued) return;
        defaultOpenQueued = true;
        JGL.runTask(this::drainDefaultOpen);
    }

    /**
     * Requests a new file through the same name-entry dialog as the file menu.
     *
     * @param directory destination validated again before creation
     */
    public void createFile(Path directory) {
        create(directory, false);
    }

    /**
     * Requests a new folder through the same name-entry dialog as the file menu.
     *
     * @param directory destination validated again before creation
     */
    public void createFolder(Path directory) {
        create(directory, true);
    }

    /**
     * Copies explicitly chosen imports without replacing the native clipboard.
     * Existing names are preserved using the ordinary keep-both transfer policy.
     *
     * @param paths nonnull user-approved source snapshot
     * @param directory destination validated again before copying
     */
    public void importFiles(List<Path> paths, Path directory) {
        if (busy) {
            status.accept("A file operation is still running. Drop or import the files again when it finishes.");
            return;
        }
        List<Path> captured = List.copyOf(paths);
        if (captured.isEmpty()) return;
        run("Importing assets", () -> {
            validateDestination(directory);
            history.paste(captured, directory, false, "Import");
        }, true, null);
    }

    /**
     * Builds one command snapshot at the pointer, preserving owner selection.
     *
     * @param anchor attached row, tile, or browser viewport
     * @param paths immutable or caller-owned selected paths, copied before use
     * @param folder destination for paste and creation, or null for an unavailable place
     * @param event secondary press with screen-space coordinates
     */
    public void showFor(UINode anchor, List<Path> paths, Path folder, MousePressEvent event) {
        Objects.requireNonNull(anchor);
        Objects.requireNonNull(event);
        if (!active() || anchor.getRoot() != owner.getRoot()) return;
        hide();
        List<Path> snapshot = List.copyOf(paths);
        boolean selected = !snapshot.isEmpty();
        boolean writable = folder != null && Files.isDirectory(folder) && Files.isWritable(folder);
        boolean mutation = !busy && mutable(snapshot);
        boolean canPaste = false;
        try {
            canPaste = writable && !clipboard.readSelection().paths().isEmpty();
        } catch (IOException failure) {
            status.accept(failure.getMessage());
        }
        ArrayList<NanoPopupMenuItem> items = new ArrayList<>(12);
        items.add(new NanoPopupMenuItem(canUndo() ? "Undo " + history.undoName() : "Undo", this::undo, canUndo()));
        items.add(new NanoPopupMenuItem(canRedo() ? "Redo " + history.redoName() : "Redo", this::redo, canRedo()));
        items.add(new NanoPopupMenuItem("Open", () -> open(snapshot), selected && !busy));
        if (snapshot.size() == 1 && !Files.isDirectory(snapshot.getFirst()))
            items.add(NanoPopupMenuItem.submenu("Open In", () -> applications(snapshot.getFirst())));
        items.add(new NanoPopupMenuItem("Show in File Explorer", () -> run("Opening location", () -> applications.reveal(snapshot.getFirst()), false, null), selected && applications.isRevealSupported() && !busy));
        items.add(new NanoPopupMenuItem("Copy", () -> copy(snapshot, false), selected && !busy));
        items.add(new NanoPopupMenuItem("Cut", () -> copy(snapshot, true), mutation));
        items.add(new NanoPopupMenuItem("Paste", () -> paste(folder), canPaste && !busy));
        items.add(new NanoPopupMenuItem("Copy Path", () -> copyPath(snapshot), selected && !busy));
        items.add(new NanoPopupMenuItem("Rename...", () -> rename(snapshot.getFirst()), mutation && snapshot.size() == 1));
        items.add(new NanoPopupMenuItem("Delete...", () -> delete(snapshot, false), mutation));
        items.add(NanoPopupMenuItem.submenu("New", List.of(
                new NanoPopupMenuItem("File...", () -> create(folder, false), writable && !busy),
                new NanoPopupMenuItem("Folder...", () -> create(folder, true), writable && !busy))));
        items.add(new NanoPopupMenuItem("Refresh", changed, !busy));
        visibleRoot = owner.getRoot();
        popup.items(items);
        popup.showAt(anchor, event.getX(), event.getY(), 238);
    }

    /**
     * Routes familiar file shortcuts after the owner has limited their scope to
     * its listing or tree. Text-field editing remains the owner's responsibility.
     *
     * @param context routed key preview event
     * @return whether this controller handled and consumed the key
     */
    public boolean handleShortcut(UIInputEvent context) {
        if (!active() || busy || !(context.event() instanceof KeyPressEvent key)) return false;
        boolean command = key.isCtrlDown() || key.isSuperDown();
        int code = key.getKey();
        if (command && (code == Keyboard.Z || code == Keyboard.Y)) {
            if (code == Keyboard.Y || key.isShiftDown()) redo();
            else undo();
            context.consume();
            return true;
        }
        if (command && code == Keyboard.V) {
            Path folder = destination.get();
            if (folder == null) return false;
            paste(folder);
            context.consume();
            return true;
        }
        if (!(command && (code == Keyboard.C || code == Keyboard.X)) && code != Keyboard.F2 && code != Keyboard.DELETE) return false;
        List<Path> paths = List.copyOf(selection.get());
        if (command && key.getKey() == Keyboard.C && !paths.isEmpty()) copy(paths, false);
        else if (command && key.getKey() == Keyboard.X && mutable(paths)) copy(paths, true);
        else if (key.getKey() == Keyboard.F2 && paths.size() == 1 && mutable(paths)) rename(paths.getFirst());
        else if (key.getKey() == Keyboard.DELETE && mutable(paths)) delete(paths, key.isShiftDown());
        else return false;
        context.consume();
        return true;
    }

    /**
     * Closes overlays without cancelling an already accepted disk operation.
     */
    public void hide() {
        popup.close();
        if (dialog != null) {
            dialog.close();
            dialog = null;
        }
        if (chooser != null) {
            chooser.cancel();
            chooser = null;
        }
    }

    /**
     * Releases overlays when an owner leaves its root or becomes unavailable.
     * Performs no clipboard reads or filesystem access in the frame update.
     */
    public void update() {
        if (!active() || visibleRoot != null && owner.getRoot() != visibleRoot) hide();
        if (dialog != null && !dialog.isOpen()) dialog = null;
    }

    /**
     * Checks effective owner availability through every ancestor.
     *
     * @return whether the owner has an active visible UI root
     */
    private boolean active() {
        if (owner.getRoot() == null) return false;
        for (UINode node = owner; node != null; node = node.getParent())
            if (!node.isVisible() || node.isDisabled()) return false;
        return true;
    }

    /**
     * Checks snapshot protection without performing expensive directory scans.
     *
     * @param paths captured operation sources
     * @return whether there is a selection containing no protected root
     */
    private boolean mutable(List<Path> paths) {
        if (paths.isEmpty()) return false;
        for (Path path : paths) {
            Path entry = path.toAbsolutePath()
                    .normalize();
            if (entry.getParent() == null) return false;
            for (Path protectedPath : protectedPaths)
                if (protectedPath.equals(entry) || protectedPath.startsWith(entry)) return false;
        }
        return true;
    }

    /**
     * Revalidates actual parent containment without following a final directory link.
     *
     * @param path browser-owned mutation target
     * @throws IOException if the target leaves the root or is protected
     */
    private void validateMutation(Path path) throws IOException {
        validateProtection(path);
        Path parent = path.toAbsolutePath()
                .normalize()
                .getParent()
                .toRealPath();
        validateDestination(parent);
    }

    /**
     * Rejects aliases of protected roots while preserving final symbolic links as
     * removable entries. Resolving the parent closes ancestor junction aliases
     * without treating deletion of a link as deletion of its target.
     *
     * @param path source entry requested for mutation or an external cut import
     * @throws IOException if the entry contains a protected root or cannot be resolved
     */
    private void validateProtection(Path path) throws IOException {
        if (!mutable(List.of(path))) throw new IOException("This application root cannot be changed.");
        Path entry = path.toAbsolutePath()
                .normalize();
        Path canonical = entry.getParent()
                .toRealPath()
                .resolve(entry.getFileName());
        for (Path protectedPath : protectedPaths)
            if (protectedPath.toRealPath().startsWith(canonical))
                throw new IOException("This application root cannot be changed.");
    }

    /**
     * Resolves and validates a creation or paste directory at operation time.
     *
     * @param folder requested destination
     * @throws IOException if resolution fails or containment is violated
     */
    private void validateDestination(Path folder) throws IOException {
        if (folder == null) throw new IOException("Choose a destination folder.");
        Path real = folder.toRealPath();
        if (!Files.isDirectory(real)) throw new IOException("The destination is not a folder.");
        if (confinedRoot != null && !real.startsWith(confinedRoot.toRealPath()))
            throw new IOException("The destination is outside this browser's root.");
    }

    /**
     * Opens the captured selection through an application handler or native association.
     *
     * @param paths captured selected paths
     */
    private void open(List<Path> paths) {
        if (opened != null) {
            for (Path path : paths) opened.accept(path);
        } else run("Opening selection", () -> {
            for (Path path : paths) applications.open(path);
        }, false, null);
    }

    /**
     * Captures and clears the pending native-open batch before starting one worker.
     * The mutable list remains available for later activations, while each worker
     * receives an immutable snapshot independent of subsequent selection changes.
     */
    private void drainDefaultOpen() {
        defaultOpenQueued = false;
        List<Path> paths = List.copyOf(pendingDefaultPaths);
        pendingDefaultPaths.clear();
        run("Opening selection", () -> {
            for (Path path : paths) applications.open(path);
        }, false, null);
    }

    /**
     * Builds compatible application and platform opening choices on demand.
     *
     * @param path captured selected file
     * @return immutable submenu command snapshot
     */
    private List<NanoPopupMenuItem> applications(Path path) {
        ArrayList<NanoPopupMenuItem> choices = new ArrayList<>();
        for (NanoFileOpenTarget target : targets)
            if (target.accepts().test(path)) choices.add(new NanoPopupMenuItem(target.name(), () -> target.open().accept(path), !busy));
        choices.add(new NanoPopupMenuItem("Default Application", () -> run("Opening file", () -> applications.open(path), false, null), applications.isOpenSupported() && !busy));
        choices.add(new NanoPopupMenuItem("Default Text Editor", () -> run("Opening editor", () -> applications.edit(path), false, null), applications.isEditSupported() && !busy));
        choices.add(new NanoPopupMenuItem("Choose Application...", () -> run("Choosing application", () -> applications.chooseDefaultApplication(path), false, null), applications.isApplicationChooserSupported() && !busy));
        choices.add(new NanoPopupMenuItem("Browse for Application...", () -> chooseExecutable(path), !busy));
        return List.copyOf(choices);
    }

    /**
     * Places native file-list data on the clipboard without modifying sources.
     *
     * @param paths captured sources
     * @param cut whether later paste should move instead of copy
     */
    private void copy(List<Path> paths, boolean cut) {
        try {
            if (cut) {
                for (Path path : paths) validateMutation(path);
                clipboard.cut(paths);
            } else clipboard.copy(paths);
            status.accept(paths.size() + (cut ? " item(s) ready to move" : " item(s) copied"));
        } catch (IOException failure) {
            report(failure);
        }
    }

    /**
     * Copies readable selected paths as plain text, replacing native file-list clipboard contents.
     *
     * @param paths captured source names
     */
    private void copyPath(List<Path> paths) {
        StringBuilder names = new StringBuilder(paths.size() * 64);
        for (Path path : paths) {
            if (!names.isEmpty()) names.append(System.lineSeparator());
            names.append(path.toAbsolutePath().normalize());
        }
        if (!TextEditing.copyText(names.toString())) {
            report(new IOException("The selected paths could not be copied to the system clipboard."));
            return;
        }
        status.accept("Path copied");
    }

    /**
     * Captures one coherent native clipboard snapshot before beginning a transfer.
     *
     * @param folder selected destination directory
     */
    private void paste(Path folder) {
        try {
            FileClipboardSelection captured = clipboard.readSelection();
            if (captured.paths().isEmpty()) throw new IOException("The clipboard contains no files.");
            run(captured.cut() ? "Moving files" : "Copying files", () -> {
                validateDestination(folder);
                if (captured.cut())
                    for (Path path : captured.paths()) validateProtection(path);
                history.paste(captured.paths(), folder, captured.cut(), captured.cut() ? "Move" : "Copy");
            }, true, captured.cut() ? () -> clipboard.clearIfMatches(captured.paths()) : null);
        } catch (IOException failure) {
            report(failure);
        }
    }

    /**
     * Requests a leaf name before creating a new file or folder.
     *
     * @param folder destination directory
     * @param directory whether to create a folder
     */
    private void create(Path folder, boolean directory) {
        String title = directory ? "New folder" : "New file";
        promptName(title, directory ? "New Folder" : "New File.txt", name -> () -> {
            validateDestination(folder);
            history.create(folder, name, directory);
        });
    }

    /**
     * Requests a replacement leaf name while keeping the same parent directory.
     *
     * @param path captured rename source
     */
    private void rename(Path path) {
        promptName("Rename", path.getFileName().toString(), name -> () -> {
            validateMutation(path);
            history.rename(path, name);
        });
    }

    /**
     * Opens an explicit name-entry dialog; Enter has the same behavior as its action.
     *
     * @param title visible operation name
     * @param initial suggested leaf name
     * @param operation factory capturing validated execution inputs
     */
    private void promptName(String title, String initial, Function<String, FileCommand> operation) {
        if (busy || !active()) return;
        NanoPanel body = dialogBody(title);
        NanoTextField name = new NanoTextField("Name")
                .text(initial);
        name.getLayout()
                .widthPercent(100)
                .height(28)
                .noShrink();
        NanoPanel actions = dialogActions();
        NanoButton cancel = new NanoButton("Cancel")
                .action(button -> hide());
        NanoButton approve = new NanoButton(title)
                .action(button -> submitName(title, name, operation));
        name.action(field -> submitName(title, name, operation));
        actions.add(cancel, approve);
        body.add(name, actions);
        showDialog(body, 460, 158);
        owner.getRoot()
                .setFocusTo(name);
        name.getEditor()
                .selectAll();
    }

    /**
     * Captures submitted text before hiding its dialog or starting disk IO.
     *
     * @param title operation feedback label
     * @param field owned pending-name field
     * @param operation operation factory
     */
    private void submitName(String title, NanoTextField field, Function<String, FileCommand> operation) {
        String name = field.getText()
                .trim();
        if (name.isEmpty()) {
            field.setTooltip("A file or folder name is required.");
            field.setStyle(NanoTextField.BORDER_COLOR_KEY, ERROR_INK);
            return;
        }
        FileCommand captured = operation.apply(name);
        hide();
        run(title, captured, true, null);
    }

    /**
     * Presents actual names and the deletion policy before any destructive action.
     *
     * @param paths immutable selected paths
     * @param permanent whether Shift+Delete explicitly requested permanent deletion
     */
    private void delete(List<Path> paths, boolean permanent) {
        if (busy || !mutable(paths)) return;
        boolean trash = !permanent && operations.isTrashSupported();
        String title = trash ? "Move to Trash" : "Delete permanently";
        NanoPanel body = dialogBody(title + "?");
        StringBuilder names = new StringBuilder();
        int shown = Math.min(paths.size(), 3);
        for (int i = 0; i < shown; i++) {
            if (i > 0) names.append('\n');
            names.append(paths.get(i).getFileName());
        }
        if (paths.size() > shown)
            names.append("\nand ")
                .append(paths.size() - shown)
                .append(" more item(s)");
        NanoLabel entries = new NanoLabel(names.toString());
        entries.getLayout()
                .widthPercent(100)
                .height(22 * (shown + (paths.size() > shown ? 1 : 0)))
                .noShrink();
        NanoLabel policy = new NanoLabel(trash ? "Items can be restored from the system Trash." : "These items will be permanently removed.");
        policy.getLayout()
                .widthPercent(100)
                .height(24)
                .noShrink();
        NanoPanel actions = dialogActions();
        actions.add(new NanoButton("Cancel").action(button -> hide()), new NanoButton(title).action(button -> {
            hide();
            run(title, () -> {
                for (Path path : paths) validateMutation(path);
                if (trash) history.trash(paths);
                else {
                    history.forget();
                    operations.deletePermanently(paths);
                }
            }, true, null);
        }));
        body.add(entries, policy, actions);
        showDialog(body, 500, 172 + 22 * shown);
    }

    /**
     * Offers a native executable picker without constructing shell command strings.
     *
     * @param path captured selected file passed as one process argument
     */
    private void chooseExecutable(Path path) {
        if (busy || !active()) return;
        try {
            Path start = Path.of(System.getProperty("user.home"));
            chooser = new NanoFileChooser(start);
            chooser.onApprove(paths -> run("Opening application", () -> applications.openIn(paths.getFirst(), List.of(path)), false, null));
            chooser.showDialog(owner);
            visibleRoot = owner.getRoot();
        } catch (IOException failure) {
            report(failure);
        }
    }

    /**
     * Creates readable dialog chrome using ordinary themed nodes.
     *
     * @param title visible operation heading
     * @return unattached dialog body
     */
    private NanoPanel dialogBody(String title) {
        NanoPanel body = new NanoPanel();
        body.setStyleName("widget-layout");
        body.getLayout()
                .column()
                .padding(14)
                .gap(10)
                .widthPercent(100)
                .heightPercent(100);
        NanoLabel heading = new NanoLabel(title);
        heading.getLayout()
                .widthPercent(100)
                .height(24)
                .noShrink();
        body.add(heading);
        return body;
    }

    /**
     * Creates a compact horizontal row of explicit dialog actions.
     *
     * @return unattached action panel
     */
    private NanoPanel dialogActions() {
        NanoPanel actions = new NanoPanel();
        actions.setStyleName("widget-layout");
        actions.getLayout()
                .row()
                .justifyEnd()
                .gap(8)
                .widthPercent(100)
                .height(28)
                .noShrink();
        return actions;
    }

    /**
     * Shows a nonblocking owner-scoped dialog within available root dimensions.
     *
     * @param body unattached dialog controls
     * @param width preferred logical width
     * @param height preferred logical height
     */
    private void showDialog(NanoPanel body, float width, float height) {
        hide();
        visibleRoot = owner.getRoot();
        dialog = new NanoModal(owner);
        dialog.setTheme(owner.getTheme());
        dialog.getDialog()
                .getLayout()
                .width(Math.min(width, visibleRoot.getWidth() - 24))
                .height(Math.min(height, visibleRoot.getHeight() - 24));
        dialog.content(body);
        dialog.open();
    }

    /**
     * Runs IO on one virtual worker and reports completion on the engine UI queue.
     * Captured snapshots remain valid if the user navigates elsewhere during work.
     * Completion retains the initiating window context across the worker boundary.
     * Status or worker-startup failures release command state before propagating.
     *
     * @param activity human-readable operation status
     * @param command checked non-graphics operation
     * @param mutation whether owners should rescan after completion or partial failure
     * @param after optional UI-thread clipboard completion operation
     */
    private void run(String activity, FileCommand command, boolean mutation, FileCommand after) {
        if (busy || !active()) return;
        busy = true;
        UIRoot startingRoot = owner.getRoot();
        WindowContext context = WindowContext.current();
        try {
            status.accept(activity + "...");
            Thread.ofVirtual()
                    .name("ui-file-command")
                    .start(() -> {
                        IOException failure = null;
                        try {
                            command.execute();
                } catch (IOException problem) {
                            failure = problem;
                } catch (RuntimeException problem) {
                            failure = new IOException(activity + " failed.", problem);
                }
                        IOException result = failure;
                        JGL.runTask(() -> complete(activity, mutation, after, result, startingRoot, context));
            });
        } catch (RuntimeException | Error failure) {
            busy = false;
            throw failure;
        }
    }

    /**
     * Clears completed native cut state before invoking owner-scoped callbacks.
     * A detached or replaced owner receives failures through its registered listener
     * without opening a dialog. Command state is released before any callback runs.
     * All callbacks run in the initiating window context, including the primary null
     * scope. A closed context receives no callbacks. The queue's prior scope is
     * restored even if a callback fails.
     *
     * @param activity completed operation label
     * @param mutation whether filesystem snapshots need refreshing
     * @param after optional clipboard completion
     * @param failure operation error, or null on success
     * @param startingRoot root owning the original request
     * @param context initiating window context, or null for the primary window
     */
    private void complete(String activity, boolean mutation, FileCommand after, IOException failure, UIRoot startingRoot, WindowContext context) {
        busy = false;
        if (context != null && context.isClosed()) return;
        WindowContext previous = WindowContext.use(context);
        try {
            if (failure == null && after != null) {
                try {
                    after.execute();
                } catch (IOException problem) {
                    failure = problem;
                }
            }
            if (owner.getRoot() != startingRoot) {
                if (failure != null) failed.accept(failure);
                return;
            }
            if (mutation) {
                try {
                    changed.run();
                } catch (RuntimeException | Error problem) {
                    if (failure != null) problem.addSuppressed(failure);
                    throw problem;
                }
            }
            if (failure != null) {
                report(failure);
                return;
            }
            status.accept(activity + " completed");
        } finally {
            WindowContext.use(previous);
        }
    }

    /**
     * Reports a checked failure visibly before invoking the application's listener.
     *
     * @param failure original or wrapped failure retaining its cause
     */
    private void report(IOException failure) {
        String message = failure.getMessage() == null ? "The file operation failed." : failure.getMessage();
        status.accept(message);
        if (active()) {
            NanoPanel body = dialogBody("File operation failed");
            NanoLabel details = new NanoLabel(message);
            details.setTooltip(message);
            details.getLayout()
                    .widthPercent(100)
                    .height(64)
                    .noShrink();
            NanoPanel actions = dialogActions();
            actions.add(new NanoButton("Close").action(button -> hide()));
            body.add(details, actions);
            showDialog(body, 540, 190);
        }
        failed.accept(failure);
    }
}
