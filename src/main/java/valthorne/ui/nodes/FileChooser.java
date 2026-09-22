package valthorne.ui.nodes;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.ui.UIInputEvent;
import valthorne.ui.UINode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Engine-native Open/Save chooser with directory navigation, filename entry, extension
 * filters, optional multiple selection, and explicit approval/cancellation. Embed it
 * in a bounded panel or open it as a nonblocking modal on the application's UI thread.
 * <pre>{@code
 * FileChooser chooser = new FileChooser(Path.of("assets"));
 * chooser.filters(List.of(new FileChooser.Filter("Images", List.of("png", "jpg"))));
 * chooser.onApprove(paths -> loadTexture(paths.getFirst()));
 * chooser.onCancel(() -> System.out.println("Cancelled"));
 * chooser.showDialog(openButton);
 * }</pre>
 * Directory scans are synchronous snapshots. Double-click folders to enter them;
 * double-click files or use the approval button to accept. Ctrl/Super toggles and
 * Shift ranges are available in multiple-selection Open mode. A typed filename takes
 * precedence over row selection; multiple selected rows clear the filename field.
 * Save returns a validated destination and requires confirmation for an existing file.
 * File approval never creates, overwrites, deletes, or launches a file: the application
 * performs file IO after approval and must handle filesystem changes and IO failures.
 * The separate New folder command creates only an explicitly named child directory.
 * @author Albert Beaupre
 */
public class FileChooser extends Panel {
    /**
     * Determines whether approval requires an existing item or permits a new file.
     * @author Albert Beaupre
     */
    public enum Mode {
        /**
         * Select existing readable files or directories according to the selection policy.
         */
        OPEN,
        /**
         * Choose one writable file destination, confirming any existing target.
         */
        SAVE
    }

    /**
     * Accepted Open-mode item types; directories remain navigable in every policy.
     * Save always requires a file destination regardless of this Open-mode setting.
     * @author Albert Beaupre
     */
    public enum SelectionMode {
        /**
         * Approve regular files; activating a directory navigates into it.
         */
        FILES,
        /**
         * Approve directories, including the current directory when nothing is entered.
         */
        DIRECTORIES,
        /**
         * Approve either regular files or directories.
         */
        FILES_AND_DIRECTORIES
    }

    /**
     * Named, immutable case-insensitive extension filter. Empty extensions accept all
     * files. Extensions omit the leading dot; compound suffixes such as "tar.gz" work.
     * The first extension is appended to extensionless Save filenames automatically.
     * @param description user-facing dropdown label
     * @param extensions allowed suffixes without dots at either end
     * @author Albert Beaupre
     */
    public record Filter(String description, List<String> extensions) {
        /**
         * Copies and normalizes extensions while rejecting empty labels and malformed suffixes.
         * @param description nonblank display label
         * @param extensions nonnull suffix list, empty for all files
         */
        public Filter {
            if (Objects.requireNonNull(description).isBlank()) throw new IllegalArgumentException("Empty filter label");
            extensions = Objects.requireNonNull(extensions).stream().map(extension -> {
                String value = Objects.requireNonNull(extension).toLowerCase(Locale.ROOT);
                if (value.isBlank() || value.startsWith(".") || value.endsWith(".") || value.contains("/")
                        || value.contains("\\") || value.contains("*") || value.contains("?"))
                    throw new IllegalArgumentException("Invalid extension: " + value);
                return value;
            }).distinct().toList();
        }

        /**
         * Matches only the final path component; directories bypass this rule in the browser.
         * @param path candidate file path
         * @return whether any allowed suffix matches, or all files are accepted
         */
        public boolean accepts(Path path) {
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            return extensions.isEmpty() || extensions.stream().anyMatch(extension -> name.endsWith("." + extension));
        }
    }

    private final FileExplorer explorer; // Unconfined browser owned by this chooser.
    private final TextField location = new TextField("Directory"); // Editable absolute or relative location.
    private final BreadcrumbBar address = new BreadcrumbBar(); // Clickable path segments in the top address bar.
    private final ScrollPanel addressViewport = new ScrollPanel(); // Horizontal clipping of long breadcrumb paths.
    private final Panel addressSlot = new Panel(); // Shared bounds for breadcrumb navigation and editable path entry.
    private boolean editingAddress; // Whether the user is editing a path instead of viewing breadcrumbs.
    private boolean revealAddress; // Scrolls updated breadcrumb tails into view after layout resolves.
    private final TextField filename = new TextField("File name"); // Typed candidate, overriding row selection when nonempty.
    private final ComboBox<Filter> filters = new ComboBox<>(); // Active named extension filter.
    private final Button back = new Button("<"); // Successful-directory history navigation.
    private final Button forward = new Button(">"); // Previously visited forward history.
    private final Button up = new Button("^"); // Parent directory navigation.
    private final Label title = new Label("Open"); // Dialog operation title above the address row.
    private final TextField search = new TextField("Search"); // Live current-directory filename search.
    private final DirectoryTree tree; // Lazy places/drive tree synchronized with the right pane.
    private final SplitPane browser; // Resizable left tree and right file listing.
    private final ScrollPanel messageViewport = new ScrollPanel(); // Collapsible inline validation message.
    private final Panel newFolderRow = new Panel(); // Inline name entry for the New folder command.
    private final TextField newFolderName = new TextField("New folder name"); // Pending single-component folder name.
    private boolean hiddenFiles; // Hidden-file visibility selected from the toolbar.
    private final Button approve = new Button("Open"); // Explicit selection approval.
    private final Label status = new Label(); // Validation or overwrite-confirmation message.
    private final Panel confirmation = new Panel(); // Inline overwrite decision controls.
    private final ArrayDeque<Path> previous = new ArrayDeque<>(); // Back history, newest first.
    private final ArrayDeque<Path> next = new ArrayDeque<>(); // Forward history, newest first.
    private Path current; // Last observed successful directory.
    private Path pendingOverwrite; // Exact validated existing Save target awaiting confirmation.
    private boolean traversing; // Suppresses history insertion during Back/Forward navigation.
    private boolean multiple; // Requested Open-mode multiple selection.
    private Mode mode = Mode.OPEN; // Current operation mode.
    private SelectionMode selectionMode = SelectionMode.FILES; // Accepted Open-mode item types.
    private Filter activeFilter = new Filter("All files", List.of()); // Successfully installed file filter.
    private Consumer<List<Path>> approved = paths -> {}; // Application callback after validation and modal dismissal.
    private Runnable cancelled = () -> {}; // Explicit cancellation callback.
    private Modal modal; // Optional retained nonblocking modal wrapper.

    /**
     * Scans an initial directory and builds navigation, filename, filter, and action rows.
     * Give an embedded chooser a width and height; at least 560 by 440 is recommended.
     * @param directory existing readable initial directory
     * @throws IOException if initial resolution or enumeration fails
     */
    public FileChooser(Path directory) throws IOException {
        explorer = new FileExplorer(directory, false).chrome(false).details(true);
        current = explorer.getDirectory();
        getLayout().column().minWidth(460).minHeight(320).padding(8).gap(4);
        setStyleName("chooser-chrome");
        Panel heading = new Panel(); heading.getLayout().row().itemsCenter().height(26).noShrink();
        Panel titleSpace = new Panel(); titleSpace.getLayout().grow(1); titleSpace.add(title);
        Button close = tool("x", 28, this::cancel); heading.add(titleSpace, close);
        Panel navigation = new Panel(); navigation.getLayout().row().gap(4).height(30).noShrink();
        for (Button button : List.of(back, forward, up)) { button.setStyleName("chooser-tool"); button.getLayout().width(28).height(30).noShrink(); }
        back.action(button -> back()); forward.action(button -> forward());
        up.action(button -> { if (current.getParent() != null) navigate(current.getParent()); });
        location.text(current.toString()).action(field -> navigateText());
        location.setStyleName("chooser-editor"); location.getLayout().absolute().left(0).top(0).widthPercent(100).height(30);
        location.setVisible(false);
        addressSlot.setStyleName("chooser-pane"); addressSlot.getLayout().width(0).grow(1).minWidth(80).height(30);
        addressViewport.horizontal(true).vertical(false).horizontalBar(false).verticalBar(false);
        addressViewport.getLayout().absolute().left(0).top(0).widthPercent(100).height(30);
        address.getLayout().height(30).selfStart().noShrink(); address.onNavigate(this::navigate); addressViewport.setContent(address);
        addressSlot.add(addressViewport, location);
        search.setStyleName("chooser-editor"); search.getLayout().widthPercent(25).minWidth(100).height(30);
        search.getEditor().onChange(() -> {
            try { explorer.search(search.getText()); message(""); }
            catch (IOException failure) { message(failure.getMessage()); }
        });
        navigation.add(back, forward, up, addressSlot, tool("...", 28, this::editAddress), tool("R", 28, this::refresh), search);
        Panel commands = new Panel(); commands.getLayout().row().gap(8).height(30).noShrink();
        Button create = tool("New folder", 100, () -> {
            newFolderRow.setVisible(true); newFolderRow.getLayout().height(30); newFolderName.text("");
            if (getRoot() != null) { getRoot().layout(); getRoot().setFocusTo(newFolderName); }
        });
        Button hidden = tool("Hidden files: off", 140, () -> {});
        hidden.action(button -> {
            try { explorer.showHidden(!hiddenFiles); hiddenFiles = !hiddenFiles; button.text("Hidden files: " + (hiddenFiles ? "on" : "off")); }
            catch (IOException failure) { message(failure.getMessage()); }
        });
        commands.add(create, hidden);
        ArrayList<Path> places = new ArrayList<>(); Path home = Path.of(System.getProperty("user.home"));
        for (String name : List.of("Desktop", "Documents", "Downloads", "Pictures", "Music", "Videos")) {
            Path place = home.resolve(name); if (Files.isDirectory(place)) places.add(place);
        }
        places.add(home); current.getFileSystem().getRootDirectories().forEach(places::add);
        tree = new DirectoryTree(places).onNavigate(this::navigate).onError(failure -> message(failure.getMessage()));
        browser = new SplitPane(tree, explorer).ratio(.31f).dividerSize(5).minimumSizes(135, 220);
        browser.getDivider().setStyleName("chooser-divider");
        browser.getLayout().height(0).grow(1).minHeight(100).widthPercent(100);
        explorer.getLayout().minWidth(0).minHeight(0);
        newFolderRow.getLayout().row().gap(6).height(0).noShrink(); newFolderRow.setVisible(false);
        newFolderName.setStyleName("chooser-editor"); newFolderName.getLayout().width(0).grow(1).height(30);
        newFolderName.action(field -> createFolder(field.getText()));
        newFolderRow.add(newFolderName, tool("Create", 74, () -> createFolder(newFolderName.getText())),
                tool("Cancel", 74, () -> { newFolderRow.setVisible(false); newFolderRow.getLayout().height(0); }));
        filename.action(field -> approve()); filename.setStyleName("chooser-editor");
        filename.getLayout().width(0).grow(1).minWidth(0).height(30);
        filename.getEditor().onChange(this::clearConfirmation);
        filters.items(List.of(activeFilter)).formatter(Filter::description).selectedIndex(0).onChange(this::chooseFilter);
        filters.setStyleName("chooser-action"); filters.getLayout().width(0).grow(1).minWidth(0).height(28);
        filters.getLabel().getLayout().absolute().left(8).top(6);
        messageViewport.horizontal(false).vertical(false).horizontalBar(false).verticalBar(false);
        messageViewport.getLayout().height(0).noShrink().widthPercent(100); messageViewport.setContent(status);
        confirmation.getLayout().row().height(0).noShrink(); confirmation.setVisible(false);
        Button replace = new Button("Replace").action(button -> confirmOverwrite());
        Button keep = new Button("Keep editing").action(button -> { clearConfirmation(); message(""); });
        replace.getLayout().width(100).height(36); keep.getLayout().width(120).height(36);
        confirmation.add(replace, keep);
        Panel actions = new Panel(); actions.getLayout().row().justifyEnd().gap(8).height(30).noShrink();
        approve.action(button -> approve()); approve.setStyleName("chooser-primary"); approve.getLayout().width(90).height(30);
        Button cancel = new Button("Cancel").action(button -> cancel()); cancel.setStyleName("chooser-action"); cancel.getLayout().width(90).height(30);
        actions.add(approve, cancel);
        add(heading, navigation, commands, browser, newFolderRow, fieldRow("File name:", filename),
                fieldRow("Files of type:", filters), messageViewport, confirmation, actions);
        explorer.onSelectionChange(paths -> {
            clearConfirmation();
            filename.text(paths.size() == 1 ? paths.getFirst().getFileName().toString() : "");
        });
        explorer.onDirectoryChange(this::directoryChanged).onOpen(path -> approve()).onError(failure -> message(failure.getMessage()));
        directoryChanged(current);
    }

    /**
     * Creates a compact toolbar command with the chooser's flat button styling.
     * @param label visible command text
     * @param width fixed command width
     * @param action synchronous user operation
     * @return configured owned button
     */
    private Button tool(String label, float width, Runnable action) {
        Button button = new Button(label).action(source -> action.run()); button.setStyleName("chooser-tool");
        if (label.equals("R")) { button.text(""); button.add(new RefreshIcon()); }
        button.getLayout().width(width).height(30).noShrink(); return button;
    }

    /**
     * Vector refresh arrow independent of font glyph coverage. It inherits the current
     * theme text color and does not intercept the owning command's pointer events.
     * @author Albert Beaupre
     */
    private static final class RefreshIcon extends Panel implements valthorne.ui.nodes.nano.NanoNode {
        /**
         * Centers a noninteractive icon inside the refresh toolbar button.
         */
        private RefreshIcon() { setClickable(false); getLayout().absolute().left(5).top(6).width(18).height(18); }

        /**
         * Draws a partial circle with an arrowhead using the root-owned vector context.
         * @param vg active NanoVG context
         */
        @Override public void draw(long vg) {
            var color = getStyle().get(valthorne.ui.theme.UITokens.TEXT);
            int rgb = color == null ? 0x667788 : Math.round(color.r() * 255) << 16 | Math.round(color.g() * 255) << 8 | Math.round(color.b() * 255);
            float x = getAbsoluteX() + 9, y = getAbsoluteY() + 9;
            valthorne.ui.Canvas2D.color(vg, rgb, 1);
            org.lwjgl.nanovg.NanoVG.nvgBeginPath(vg);
            org.lwjgl.nanovg.NanoVG.nvgArc(vg, x, y, 5, -.7f, 4.7f, org.lwjgl.nanovg.NanoVG.NVG_CW);
            org.lwjgl.nanovg.NanoVG.nvgStrokeWidth(vg, 1.4f); org.lwjgl.nanovg.NanoVG.nvgStroke(vg);
            org.lwjgl.nanovg.NanoVG.nvgBeginPath(vg);
            org.lwjgl.nanovg.NanoVG.nvgMoveTo(vg, x + 5, y - 7);
            org.lwjgl.nanovg.NanoVG.nvgLineTo(vg, x + 5, y - 1);
            org.lwjgl.nanovg.NanoVG.nvgLineTo(vg, x, y - 3);
            org.lwjgl.nanovg.NanoVG.nvgClosePath(vg); org.lwjgl.nanovg.NanoVG.nvgFill(vg);
        }
    }

    /**
     * Aligns a persistent field caption and expanding editor across the chooser footer.
     * @param caption field purpose, visible even when a filename is populated
     * @param field owned editor or dropdown
     * @return horizontal footer row
     */
    private Panel fieldRow(String caption, UINode field) {
        Panel row = new Panel(); row.getLayout().row().itemsCenter().gap(8).height(30).noShrink();
        Label label = new Label(caption); label.getLayout().minWidth(92).noShrink(); row.add(label, field); return row;
    }

    /**
     * Shows an inline error/confirmation without reserving a blank row during normal browsing.
     * @param text message text; empty collapses the message viewport
     */
    private void message(String text) { status.text(text == null ? "Operation failed" : text); messageViewport.getLayout().height(status.getText().isEmpty() ? 0 : 24); }

    /**
     * Refreshes the listing and current tree branch, reporting IO failures inline.
     */
    public void refresh() {
        try { explorer.refresh(); tree.expand(current); message(""); }
        catch (IOException failure) { message(failure.getMessage()); }
    }

    /**
     * Creates one child directory from the New folder editor. Separators, dot/parent names,
     * and existing entries are rejected; success refreshes the listing without approving it.
     * @param name single directory name, with no surrounding whitespace trimming
     * @return true only after a directory was created and the listing refreshed
     */
    public boolean createFolder(String name) {
        if (isDisabled()) return false;
        try {
            Path leaf = Path.of(name);
            if (name.isBlank() || name.equals(".") || name.equals("..") || leaf.isAbsolute() || leaf.getNameCount() != 1
                    || name.contains("/") || name.contains("\\")) throw new IOException("Enter a single folder name.");
            Files.createDirectory(current.resolve(leaf)); explorer.refresh(); tree.expand(current);
            newFolderRow.setVisible(false); newFolderRow.getLayout().height(0); message(""); return true;
        } catch (IOException | java.nio.file.InvalidPathException | SecurityException failure) { message(failure.getMessage()); return false; }
    }

    /**
     * Returns the synchronized lazy directory tree; its navigation callback belongs to the chooser.
     * @return owned places/drive tree
     */
    public DirectoryTree getDirectoryTree() { return tree; }

    /**
     * Returns the live search editor, which filters both files and folders in this directory.
     * @return owned search text field
     */
    public TextField getSearchField() { return search; }

    /**
     * Switches the address bar to editable text and focuses it. Ctrl/Super+L provides
     * the same operation; Enter validates navigation, while failure leaves the text editable.
     */
    public void editAddress() {
        editingAddress = true; addressViewport.setVisible(false); location.setVisible(true);
        location.text(current.toString());
        if (getRoot() != null) getRoot().setFocusTo(location);
    }

    /**
     * Returns the filter dropdown for focus and accessibility without transferring ownership.
     * @return owned named-extension selector
     */
    public ComboBox<Filter> getFilterBox() { return filters; }

    /**
     * Returns the pending overwrite action row for focus and presentation integration.
     * @return owned confirmation row, hidden when no Replace decision is pending
     */
    public Panel getConfirmationPanel() { return confirmation; }

    /**
     * Changes Open/Save behavior, clears pending overwrite approval, and updates button text.
     * Save forces single selection; returning to Open restores the requested multiple policy.
     * @param mode nonnull operation mode
     * @return this chooser
     */
    public FileChooser mode(Mode mode) {
        this.mode = Objects.requireNonNull(mode); clearConfirmation();
        explorer.multipleSelection(mode == Mode.OPEN && multiple);
        approve.text(mode == Mode.SAVE ? "Save" : selectionMode == SelectionMode.DIRECTORIES ? "Choose" : "Open");
        title.text(approve.getText());
        return this;
    }

    /**
     * Changes which existing item types Open accepts; directory navigation stays available.
     * @param selectionMode nonnull item-type policy
     * @return this chooser
     */
    public FileChooser selectionMode(SelectionMode selectionMode) {
        this.selectionMode = Objects.requireNonNull(selectionMode); return mode(mode);
    }

    /**
     * Enables Ctrl/Super toggles, Shift ranges, and Ctrl/Super+A in Open mode only.
     * @param multiple whether Open may approve several items
     * @return this chooser
     */
    public FileChooser multipleSelection(boolean multiple) {
        this.multiple = multiple; explorer.multipleSelection(mode == Mode.OPEN && multiple); return this;
    }

    /**
     * Installs a nonempty filter list, selecting its first filter after a successful rescan.
     * A failed scan preserves the previous filter and listing.
     * @param values immutable-copy source for named extension filters
     * @return this chooser
     * @throws IOException if the current directory cannot be rescanned
     */
    public FileChooser filters(List<Filter> values) throws IOException {
        List<Filter> copy = List.copyOf(values);
        if (copy.isEmpty()) throw new IllegalArgumentException("At least one filter is required");
        explorer.filter(copy.getFirst()::accepts); activeFilter = copy.getFirst();
        filters.items(copy).selectedIndex(0); clearConfirmation(); return this;
    }

    /**
     * Applies a user-selected filter transactionally and reports scan failures inline.
     * @param filter candidate dropdown item
     */
    private void chooseFilter(Filter filter) {
        try { explorer.filter(filter::accepts); activeFilter = filter; clearConfirmation(); message(""); }
        catch (IOException failure) { filters.selectedIndex(filters.getItems().indexOf(activeFilter)); message(failure.getMessage()); }
    }

    /**
     * Navigates to an absolute or current-directory-relative path without throwing UI errors.
     * Failed navigation leaves the listing and history unchanged and displays a message.
     * @param path requested directory
     * @return whether navigation succeeded
     */
    public boolean navigate(Path path) {
        if (isDisabled()) return false;
        try { explorer.navigate(path); clearConfirmation(); message(""); return true; }
        catch (IOException | SecurityException failure) { message(failure.getMessage()); return false; }
    }

    /**
     * Parses the location editor on Enter and keeps malformed paths as editable input.
     */
    private void navigateText() {
        try { if (navigate(Path.of(location.getText()))) displayAddress(); }
        catch (java.nio.file.InvalidPathException failure) { message("Invalid directory: " + failure.getReason()); }
    }

    /**
     * Synchronizes history and location after successful navigation, including browser actions.
     * @param path new canonical directory
     */
    private void directoryChanged(Path path) {
        if (!path.equals(current) && !traversing) { previous.push(current); next.clear(); }
        current = path; location.text(path.toString());
        up.setEnabled(path.getParent() != null); tree.reveal(path);
        address.path(path.getRoot(), path);
        for (int i = 0; i < address.size(); i++) {
            Button segment = (Button) address.get(i); segment.setStyleName("chooser-heading");
            if (i < address.size() - 1) segment.text(segment.getText() + "  >");
        }
        displayAddress();
        if (!search.getText().isEmpty()) search.text("");
        search.placeholder("Search " + (path.getFileName() == null ? path : path.getFileName()));
        updateHistory(); clearConfirmation();
    }

    /**
     * Restores the clickable address representation and schedules its tail for scrolling.
     */
    private void displayAddress() {
        editingAddress = false; location.setVisible(false); addressViewport.setVisible(true); revealAddress = true;
    }

    /**
     * Reveals the current folder at the end of a long breadcrumb trail after geometry
     * becomes available. Child updates remain handled by the standard container lifecycle.
     * @param delta elapsed frame seconds
     */
    @Override public void update(float delta) {
        super.update(delta);
        if (revealAddress && addressViewport.getWidth() > 0) {
            addressViewport.scrollX(addressViewport.getMaxScrollX()); revealAddress = false;
        }
    }

    /**
     * Updates Back/Forward enablement from committed history rather than attempted navigation.
     */
    private void updateHistory() { back.setEnabled(!previous.isEmpty()); forward.setEnabled(!next.isEmpty()); }

    /**
     * Visits the last successful directory, retaining history if it is no longer accessible.
     * @return whether a back-history navigation succeeded
     */
    public boolean back() { return traverse(previous, next); }

    /**
     * Revisits the directory left by Back; a new normal navigation clears this history.
     * @return whether a forward-history navigation succeeded
     */
    public boolean forward() { return traverse(next, previous); }

    /**
     * Moves one history entry only after successful enumeration, restoring the traversal guard.
     * @param source history to visit
     * @param destination history receiving the old current directory
     * @return true when an entry was visited
     */
    private boolean traverse(ArrayDeque<Path> source, ArrayDeque<Path> destination) {
        if (source.isEmpty() || isDisabled()) return false;
        Path old = current; traversing = true;
        try {
            if (!navigate(source.peek())) return false;
            source.pop(); destination.push(old); updateHistory(); return true;
        } finally { traversing = false; }
    }

    /**
     * Returns the owned browser for selection, hidden-file policy, and listing inspection.
     * Its callbacks are reserved by the chooser; replacing them disconnects synchronization.
     * @return chooser browser
     */
    public FileExplorer getExplorer() { return explorer; }

    /**
     * Returns the filename editor. Setting text prepares a candidate without approving it.
     * Relative paths resolve against the displayed directory; absolute paths are accepted.
     * @return editable candidate field
     */
    public TextField getFilenameField() { return filename; }

    /**
     * Returns the location editor, whose Enter action performs navigation.
     * @return directory input field
     */
    public TextField getLocationField() { return location; }

    /**
     * Reads the most recent validation or navigation message for presentation and diagnostics.
     * @return current inline status text
     */
    public String getStatus() { return status.getText(); }

    /**
     * Reads the exact existing Save destination currently awaiting an explicit decision.
     * @return canonical target, or null when no overwrite confirmation is pending
     */
    public Path getPendingOverwrite() { return pendingOverwrite; }

    /**
     * Replaces the approval listener. Results are immutable canonical paths, delivered
     * after modal dismissal; application exceptions propagate to the caller.
     * @param listener nonnull synchronous result consumer
     * @return this chooser
     */
    public FileChooser onApprove(Consumer<List<Path>> listener) { approved = Objects.requireNonNull(listener); return this; }

    /**
     * Replaces the explicit Cancel/Escape listener, called after modal dismissal.
     * @param listener nonnull synchronous cancellation callback
     * @return this chooser
     */
    public FileChooser onCancel(Runnable listener) { cancelled = Objects.requireNonNull(listener); return this; }

    /**
     * Validates the current selection, navigates a typed folder in file-only mode,
     * or requests overwrite confirmation. Validation failures keep the chooser open.
     * @return true only when approval was delivered immediately
     */
    public boolean approve() {
        if (isDisabled()) return false;
        clearConfirmation();
        List<Path> result;
        try {
            result = validate();
            if (result.isEmpty()) return false;
            if (mode == Mode.SAVE && Files.exists(result.getFirst())) {
                pendingOverwrite = result.getFirst(); confirmation.setVisible(true); confirmation.getLayout().height(36);
                message("File exists. Replace it? " + pendingOverwrite.getFileName()); return false;
            }
        } catch (IOException | java.nio.file.InvalidPathException | SecurityException failure) {
            message(failure.getMessage()); return false;
        }
        finish(result); return true;
    }

    /**
     * Resolves candidates and validates their current filesystem state without performing IO writes.
     * A directory entered in file-only mode is visited and produces no approval result.
     * @return immutable canonical approved candidates, or empty after directory navigation
     * @throws IOException if names, item types, permissions, or suffixes are unsuitable
     */
    private List<Path> validate() throws IOException {
        String name = filename.getText();
        List<Path> candidates = name.isEmpty() ? explorer.getSelectedPaths() : List.of(current.resolve(Path.of(name)));
        if (candidates.isEmpty() && mode == Mode.OPEN && selectionMode != SelectionMode.FILES) candidates = List.of(current);
        if (candidates.isEmpty()) throw new IOException("Choose an item or enter a file name.");
        if (candidates.size() > 1 && (mode == Mode.SAVE || !multiple)) throw new IOException("Choose one item.");
        ArrayList<Path> result = new ArrayList<>();
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                if (mode == Mode.SAVE || selectionMode == SelectionMode.FILES) {
                    if (candidates.size() != 1) throw new IOException("Choose files only.");
                    explorer.navigate(candidate); return List.of();
                }
            } else if (mode == Mode.SAVE) {
                String leaf = candidate.getFileName().toString();
                if (!leaf.contains(".") && !activeFilter.extensions().isEmpty())
                    candidate = candidate.resolveSibling(leaf + "." + activeFilter.extensions().getFirst());
                Path parent = candidate.toAbsolutePath().getParent().toRealPath();
                if (!Files.isDirectory(parent) || !Files.isWritable(parent)) throw new IOException("Destination folder is not writable.");
                candidate = parent.resolve(candidate.getFileName());
                if (Files.exists(candidate)) {
                    candidate = candidate.toRealPath();
                    if (!Files.isRegularFile(candidate) || !Files.isWritable(candidate)) throw new IOException("Destination is not a writable file.");
                }
                if (!activeFilter.accepts(candidate)) throw new IOException("File name does not match " + activeFilter.description());
                result.add(candidate); continue;
            }
            Path real = candidate.toRealPath();
            boolean directory = Files.isDirectory(real);
            if (!Files.isReadable(real)) throw new IOException("Item is not readable: " + real);
            if (!directory && (!Files.isRegularFile(real) || selectionMode == SelectionMode.DIRECTORIES)) throw new IOException("Choose a directory.");
            if (!directory && !activeFilter.accepts(candidate)) throw new IOException("File does not match " + activeFilter.description());
            result.add(real);
        }
        return List.copyOf(result);
    }

    /**
     * Revalidates and approves only the exact target shown by the pending Replace prompt.
     * Editing the filename or changing navigation/policy invalidates that prompt.
     * @return true if the same destination was approved
     */
    public boolean confirmOverwrite() {
        if (isDisabled() || pendingOverwrite == null) return false;
        Path expected = pendingOverwrite;
        List<Path> result;
        try {
            result = validate();
            if (result.size() != 1 || !result.getFirst().equals(expected)) {
                clearConfirmation(); message("Destination changed. Choose Save again."); return false;
            }
        } catch (IOException | java.nio.file.InvalidPathException | SecurityException failure) {
            clearConfirmation(); message(failure.getMessage()); return false;
        }
        finish(result); return true;
    }

    /**
     * Invalidates any pending overwrite decision and removes its layout space.
     */
    private void clearConfirmation() { pendingOverwrite = null; confirmation.setVisible(false); confirmation.getLayout().height(0); }

    /**
     * Dismisses an optional modal before publishing an immutable validated result.
     * @param paths canonical paths to deliver
     */
    private void finish(List<Path> paths) { clearConfirmation(); message(""); if (modal != null) modal.close(); approved.accept(paths); }

    /**
     * Cancels without approving any path, clearing pending confirmation and closing the modal.
     * Embedded choosers remain visible so their owner can decide how to dismiss them.
     */
    public void cancel() { if (isDisabled()) return; clearConfirmation(); if (modal != null) modal.close(); cancelled.run(); }

    /**
     * Opens a detached chooser in a centered, nonblocking modal on the owner's root.
     * Reuse the same owner to reopen this chooser; an embedded chooser must be used in place.
     * @param owner attached node whose root hosts the dialog
     * @return this chooser
     * @throws IllegalStateException if the owner is detached or the chooser is already embedded
     */
    public FileChooser showDialog(UINode owner) {
        Objects.requireNonNull(owner);
        if (owner.getRoot() == null) throw new IllegalStateException("Dialog owner must be attached to a root");
        if (modal == null) {
            if (getParent() != null) throw new IllegalStateException("An embedded chooser cannot be opened as a dialog");
            modal = new ChooserDialog(owner).closeOnEscape(false).closeOnOutsideClick(false);
            modal.content(this);
        } else if (modal.getModalParent() != owner) throw new IllegalStateException("Reuse the original dialog owner");
        float width = Math.min(760, owner.getRoot().getWidth() - 32);
        float height = Math.min(620, owner.getRoot().getHeight() - 32);
        modal.getDialog().getLayout().width(width).height(height);
        getLayout().widthPercent(100).heightPercent(100);
        clearConfirmation(); modal.open(); owner.getRoot().setFocusTo(filename); return this;
    }

    /**
     * Routes Escape from chooser descendants to explicit cancellation. Other keys retain
     * their child widget semantics, including filename Enter and browser navigation.
     * @param context routed preview event
     */
    @Override public void onInputPreview(UIInputEvent context) {
        if (!isDisabled() && context.event() instanceof KeyPressEvent key) {
            if (key.getKey() == Keyboard.F5) { context.consume(); refresh(); return; }
            if (key.isAltDown()) {
                if (key.getKey() == Keyboard.LEFT) { context.consume(); back(); return; }
                if (key.getKey() == Keyboard.RIGHT) { context.consume(); forward(); return; }
                if (key.getKey() == Keyboard.UP) { context.consume(); if (current.getParent() != null) navigate(current.getParent()); return; }
            }
        }
        if (!isDisabled() && context.event() instanceof KeyPressEvent key && key.getKey() == Keyboard.L
                && (key.isCtrlDown() || key.isSuperDown())) { context.consume(); editAddress(); return; }
        if (editingAddress && context.event() instanceof KeyPressEvent key && key.getKey() == Keyboard.ESCAPE) {
            context.consume(); displayAddress(); if (getRoot() != null) getRoot().setFocusTo(filename); return;
        }
        if (!isDisabled() && context.event() instanceof KeyPressEvent key && key.getKey() == Keyboard.ESCAPE) {
            context.consume(); cancel();
        }
    }

    /**
     * Modal shell that routes Escape through the chooser even when the backdrop or
     * an empty dialog area owns focus. Nested dropdown scopes receive their own keys.
     * @author Albert Beaupre
     */
    private final class ChooserDialog extends Modal {
        /**
         * Binds the shell to the original owner so dismissal restores the saved focus.
         * @param owner attached node requesting this dialog
         */
        private ChooserDialog(UINode owner) { super(owner); }

        /**
         * Converts Escape anywhere in this modal scope into the chooser's cancel result.
         * @param context routed preview event
         */
        @Override public void onInputPreview(UIInputEvent context) {
            FileChooser.this.onInputPreview(context);
        }
    }
}
