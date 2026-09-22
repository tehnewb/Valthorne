package valthorne.ui.widgets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.graphics.Color;
import valthorne.ui.UIInputEvent;
import valthorne.ui.nodes.nano.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Deterministic widget contracts without a graphics context. Exercises state changes,
 * callback ordering, invalid input, snapshots, and real temporary-directory navigation.
 * @author Albert Beaupre
 */
class NanoWidgetBehaviorTest {
    @TempDir Path temporary; // JUnit-owned isolated filesystem root, removed after each test.

    /**
     * Checks independent window policies, silent bounds clamping, and transactional validation.
     */
    @Test void windowConfigurationClampsSizesAndPreservesIndependentPolicies() {
        var window = new valthorne.ui.nodes.nano.NanoWindow("Tools").bounds(12, 18, 400, 260);
        var changed = new ArrayList<valthorne.ui.nodes.nano.NanoWindow.Frame>(); window.onChange(changed::add);
        window.minimumSize(200, 120).maximumSize(500, 300).bounds(20, 30, 900, 500);
        assertEquals(new valthorne.ui.nodes.nano.NanoWindow.Frame(20, 30, 500, 300), window.getFrame());
        window.bounds(20, 30, 80, 60); assertEquals(200, window.getFrame().width()); assertEquals(120, window.getFrame().height());
        var before = window.getFrame();
        assertThrows(IllegalArgumentException.class, () -> window.minimumSize(600, 150));
        assertThrows(IllegalArgumentException.class, () -> window.maximumSize(100, 90));
        assertThrows(IllegalArgumentException.class, () -> window.bounds(Float.NaN, 0, 200, 120));
        assertEquals(before, window.getFrame()); assertTrue(changed.isEmpty());
        window.draggable(false); assertFalse(window.isDraggable()); assertTrue(window.isResizable());
        window.resizable(false).draggable(true); assertTrue(window.isDraggable()); assertFalse(window.isResizable());
        window.title("Inspector"); assertEquals("Inspector", window.getTitle());
        NanoButton child = new NanoButton("Apply"); window.content(child); assertSame(window.getContentPane(), child.getParent());
        NanoPanel other = new NanoPanel(); NanoButton attached = new NanoButton(); other.add(attached);
        assertThrows(IllegalArgumentException.class, () -> window.content(attached));
        assertSame(child, window.getContentPane().get(0));
    }

    /**
     * Checks raising detached-tree children without replacing parent references or content.
     */
    @Test void raisingAWindowReordersWithoutDetachingItsChildren() {
        NanoPanel parent = new NanoPanel();
        var first = new valthorne.ui.nodes.nano.NanoWindow("First"); var second = new valthorne.ui.nodes.nano.NanoWindow("Second");
        NanoButton child = new NanoButton("Keep"); first.content(child); parent.add(first, second);
        first.bringToFront(); assertSame(first, parent.get(1)); assertSame(parent, first.getParent());
        assertSame(first.getContentPane(), child.getParent());
        assertThrows(IllegalArgumentException.class, () -> parent.bringToFront(new NanoPanel()));
        first.bringToFront(); assertEquals(2, parent.size());
    }

    /**
     * Verifies lazy tree expansion, ancestor reveal, collapse, and transactional scan failure.
     * @throws IOException if temporary directory creation fails
     */
    @Test void directoryTreeExpandsAndRevealsWithoutNavigationCallbacks() throws IOException {
        Path root = temporary.toRealPath();
        Path nested = Files.createDirectories(root.resolve("projects/scene"));
        Files.writeString(root.resolve("ordinary.txt"), "x");
        NanoDirectoryTree tree = new NanoDirectoryTree(List.of(root));
        var requests = new ArrayList<Path>(); var failures = new ArrayList<IOException>();
        tree.onNavigate(requests::add).onError(failures::add);
        assertEquals(1, tree.getVisibleBranches().size()); assertTrue(tree.expand(root));
        assertEquals(2, tree.getVisibleBranches().size()); tree.reveal(nested);
        assertEquals(nested, tree.getSelected()); assertTrue(requests.isEmpty());
        assertEquals(2, tree.getVisibleBranches().getLast().depth());
        List<NanoDirectoryTree.Branch> before = tree.getVisibleBranches();
        assertFalse(tree.expand(root.resolve("missing"))); assertSame(before, tree.getVisibleBranches()); assertEquals(1, failures.size());
        tree.collapse(root); assertEquals(1, tree.getVisibleBranches().size());
        assertTrue(tree.expand(root)); assertTrue(tree.getVisibleBranches().stream().anyMatch(branch -> branch.path().equals(nested)));
    }

    /**
     * Tests current-directory search and explicit folder creation without accidental approval.
     * @throws IOException if fixture setup or construction fails
     */
    @Test void chooserSearchAndNewFolderHaveConcreteFilesystemBehavior() throws IOException {
        Files.createDirectory(temporary.resolve("assets"));
        Files.writeString(temporary.resolve("scene.json"), "{}");
        Files.writeString(temporary.resolve("notes.txt"), "x");
        NanoFileChooser chooser = new NanoFileChooser(temporary);
        int[] approvals = {0}; chooser.onApprove(paths -> approvals[0]++);
        chooser.getSearchField().text("SCENE"); assertEquals(1, chooser.getExplorer().getEntries().size());
        chooser.getSearchField().text("assets"); assertTrue(chooser.getExplorer().getEntries().getFirst().directory());
        chooser.getSearchField().text(""); assertEquals(3, chooser.getExplorer().getEntries().size());
        assertTrue(chooser.createFolder("new-project")); assertTrue(Files.isDirectory(temporary.resolve("new-project")));
        assertFalse(chooser.createFolder("new-project")); assertFalse(chooser.createFolder("../escape"));
        assertFalse(chooser.createFolder("")); assertEquals(0, approvals[0]);
        chooser.getSearchField().text("assets"); assertTrue(chooser.navigate(temporary.resolve("assets")));
        assertEquals("", chooser.getSearchField().getText());
        assertEquals(chooser.getExplorer().getDirectory(), chooser.getDirectoryTree().getSelected());
    }

    /**
     * Checks sortable details against controlled timestamps while retaining folders first.
     * @throws IOException if temporary files or their timestamps cannot be prepared
     */
    @Test void explorerDetailsSortByNameAndModificationTime() throws IOException {
        Files.createDirectory(temporary.resolve("folder"));
        Path a = Files.writeString(temporary.resolve("a.txt"), "a");
        Path z = Files.writeString(temporary.resolve("z.txt"), "z");
        Files.setLastModifiedTime(a, java.nio.file.attribute.FileTime.fromMillis(2000));
        Files.setLastModifiedTime(z, java.nio.file.attribute.FileTime.fromMillis(1000));
        NanoFileExplorer explorer = new NanoFileExplorer(temporary).details(true).chrome(false);
        explorer.sort(true, false);
        assertEquals(List.of("folder", "z.txt", "a.txt"), explorer.getEntries().stream().map(entry -> entry.path().getFileName().toString()).toList());
        explorer.sort(false, false); assertEquals(a, explorer.getEntries().get(1).path());
        explorer.sort(false, true); assertEquals(z, explorer.getEntries().get(1).path());
    }

    /**
     * Checks wheel conversion, alpha preservation, wrapping, clamping, and shared editor state.
     */
    @Test void wheelEditsShareColorAndRetainHueThroughBlack() {
        NanoColorPicker picker = new NanoColorPicker().color(new Color(0x80445566)).mode(NanoColorPicker.Mode.WHEEL);
        var events = new ArrayList<String>(); picker.onChange(color -> events.add(color.toHex()));
        picker.selectHSV(0, 1, 1); assertEquals("#80FF0000", picker.getColor().toHex());
        picker.selectHSV(1f / 3, 1, 1); assertEquals("#8000FF00", picker.getColor().toHex());
        picker.selectHSV(2f / 3, 1, 1); assertEquals("#800000FF", picker.getColor().toHex());
        picker.selectHSV(2f / 3, 1, 0); assertEquals("#80000000", picker.getColor().toHex());
        picker.getBrightness().onKeyPress(new KeyPressEvent(Keyboard.END, 0));
        assertEquals("#800000FF", picker.getColor().toHex());
        picker.selectHSV(-1, 2, 2); assertEquals("#80FF0000", picker.getColor().toHex());
        int count = events.size(); picker.mode(NanoColorPicker.Mode.BOTH); picker.mode(NanoColorPicker.Mode.SLIDERS);
        assertEquals(count, events.size()); assertEquals(255, picker.getChannel(0).getValue());
        assertEquals("#80FF0000", picker.getHexField().getText());
        assertThrows(IllegalArgumentException.class, () -> picker.selectHSV(Float.NaN, 1, 1));
        picker.setEnabled(false); picker.selectHSV(.5f, 1, 1); assertEquals(count, events.size());
    }

    /**
     * Exercises canonical Open results, extension filtering, invalid names, and cancellation.
     * @throws IOException if temporary fixture setup or chooser construction fails
     */
    @Test void chooserOpenValidatesTypedNamesAndSelectionTypes() throws IOException {
        Path image = Files.writeString(temporary.resolve("image.PNG"), "png").toRealPath();
        Files.writeString(temporary.resolve("notes.txt"), "text");
        Path folder = Files.createDirectory(temporary.resolve("folder")).toRealPath();
        NanoFileChooser chooser = new NanoFileChooser(temporary);
        chooser.filters(List.of(new NanoFileChooser.Filter("Images", List.of("png"))));
        assertEquals(2, chooser.getExplorer().getEntries().size());
        var results = new ArrayList<List<Path>>(); chooser.onApprove(results::add);
        chooser.getFilenameField().text("notes.txt"); assertFalse(chooser.approve()); assertTrue(results.isEmpty());
        chooser.getFilenameField().text("absent.png"); assertFalse(chooser.approve());
        chooser.getFilenameField().text("image.PNG"); assertTrue(chooser.approve());
        assertEquals(List.of(image), results.getFirst()); assertThrows(UnsupportedOperationException.class, () -> results.getFirst().clear());
        chooser.getFilenameField().text("folder"); assertFalse(chooser.approve()); assertEquals(folder, chooser.getExplorer().getDirectory());
        chooser.selectionMode(NanoFileChooser.SelectionMode.DIRECTORIES); assertTrue(chooser.approve());
        assertEquals(List.of(folder), results.getLast());
        int[] cancelled = {0}; chooser.onCancel(() -> cancelled[0]++); chooser.cancel();
        assertEquals(1, cancelled[0]); assertEquals(2, results.size());
    }

    /**
     * Tests new Save destinations, extension completion, explicit overwrite approval,
     * stale-prompt invalidation, and the guarantee that choosing never writes a file.
     * @throws IOException if fixture creation or chooser setup fails
     */
    @Test void chooserSaveConfirmsExistingTargetsWithoutWriting() throws IOException {
        Path existing = Files.writeString(temporary.resolve("old.json"), "unchanged").toRealPath();
        NanoFileChooser chooser = new NanoFileChooser(temporary).mode(NanoFileChooser.Mode.SAVE);
        chooser.filters(List.of(new NanoFileChooser.Filter("Scenes", List.of("json"))));
        var results = new ArrayList<List<Path>>(); chooser.onApprove(results::add);
        chooser.getFilenameField().text("new"); assertTrue(chooser.approve());
        assertEquals(temporary.toRealPath().resolve("new.json"), results.getFirst().getFirst());
        assertFalse(Files.exists(temporary.resolve("new.json")));
        chooser.getFilenameField().text("old.json"); assertFalse(chooser.approve());
        assertEquals(existing, chooser.getPendingOverwrite()); assertEquals(1, results.size());
        chooser.getFilenameField().text("different.json"); assertNull(chooser.getPendingOverwrite()); assertFalse(chooser.confirmOverwrite());
        chooser.getFilenameField().text("old.json"); assertFalse(chooser.approve()); assertTrue(chooser.confirmOverwrite());
        assertEquals(List.of(existing), results.getLast()); assertEquals("unchanged", Files.readString(existing));
        chooser.getFilenameField().text("missing/scene.json"); assertFalse(chooser.approve());
        chooser.getFilenameField().text("wrong.txt"); assertFalse(chooser.approve()); assertEquals(2, results.size());
    }

    /**
     * Covers multiple row selection, collapsing to Save, and successful-only history navigation.
     * @throws IOException if temporary setup or scanning fails
     */
    @Test void chooserMultiSelectionAndHistoryWorkAcrossDirectories() throws IOException {
        Path folder = Files.createDirectory(temporary.resolve("folder")).toRealPath();
        Path first = Files.writeString(temporary.resolve("a.txt"), "a").toRealPath();
        Path second = Files.writeString(temporary.resolve("b.txt"), "b").toRealPath();
        NanoFileChooser chooser = new NanoFileChooser(temporary).multipleSelection(true);
        var results = new ArrayList<List<Path>>(); chooser.onApprove(results::add);
        chooser.getExplorer().select(1); chooser.getExplorer().select(2, false, true);
        assertEquals("", chooser.getFilenameField().getText()); assertTrue(chooser.approve());
        assertEquals(List.of(first, second), results.getFirst());
        chooser.mode(NanoFileChooser.Mode.SAVE); assertEquals(1, chooser.getExplorer().getSelectedPaths().size());
        assertTrue(chooser.navigate(folder)); assertTrue(chooser.back());
        assertEquals(temporary.toRealPath(), chooser.getExplorer().getDirectory());
        assertFalse(chooser.navigate(Path.of("missing"))); assertTrue(chooser.forward());
        assertEquals(folder, chooser.getExplorer().getDirectory());
        assertTrue(chooser.navigate(temporary.getParent()));
        assertEquals(temporary.getParent().toRealPath(), chooser.getExplorer().getDirectory());
    }

    /**
     * Ensures an application failure is propagated after successful chooser validation.
     * @throws IOException if fixture creation or scanning fails
     */
    @Test void chooserDoesNotSwallowApprovalCallbackFailures() throws IOException {
        Files.writeString(temporary.resolve("file.txt"), "x");
        NanoFileChooser chooser = new NanoFileChooser(temporary).onApprove(paths -> { throw new IllegalStateException("application"); });
        chooser.getFilenameField().text("file.txt");
        assertThrows(IllegalStateException.class, chooser::approve);
    }

    /**
     * Ensures dropdown state and labels survive invalid indices and formatter failures.
     */
    @Test void comboSelectionIsTransactionalAndCallbacksSeeCommittedState() {
        var source = new ArrayList<>(List.of("one", "two"));
        var combo = new NanoComboBox<String>().items(source);
        source.clear(); assertEquals(2, combo.getItems().size());
        var events = new ArrayList<String>();
        combo.onChange(value -> { assertEquals(value, combo.getSelected()); events.add(value); });
        combo.selectedIndex(0); assertTrue(events.isEmpty());
        combo.select(1); combo.select(1); assertEquals(List.of("two"), events);
        assertThrows(IndexOutOfBoundsException.class, () -> combo.selectedIndex(2));
        assertThrows(IllegalStateException.class, () -> combo.formatter(value -> { throw new IllegalStateException(); }));
        assertEquals("two", combo.getText()); assertEquals(1, combo.getSelectedIndex());
        combo.setEnabled(false); combo.select(0); assertEquals(1, combo.getSelectedIndex());
        combo.items(List.of()); assertNull(combo.getSelected()); assertEquals(-1, combo.getSelectedIndex());
        assertFalse(combo.isOpen());
    }

    /**
     * Checks disabled command handling, immutable menu data, and menu-bar composition.
     */
    @Test void menusRejectInvalidDataAndRunOnlyEnabledCommands() {
        int[] calls = {0};
        var items = new ArrayList<>(List.of(new NanoPopupMenu.Item("Disabled", () -> calls[0]++, false),
                new NanoPopupMenu.Item("Run", () -> calls[0]++, true)));
        NanoPopupMenu menu = new NanoPopupMenu().items(items); items.clear();
        menu.activate(0); assertEquals(0, calls[0]); menu.activate(1); assertEquals(1, calls[0]);
        menu.setEnabled(false); menu.activate(1); assertEquals(1, calls[0]);
        assertThrows(NullPointerException.class, () -> new NanoPopupMenu.Item(null, () -> {}, true));
        assertThrows(UnsupportedOperationException.class, () -> menu.getItems().clear());
        NanoMenuBar bar = new NanoMenuBar().addMenu("File", menu.getItems());
        assertEquals("File", bar.getHeading(0).getText());
        assertEquals(2, bar.getMenu(0).getItems().size()); bar.closeMenus(); bar.onDestroy();
    }

    /**
     * Tests bounded stepping, overflow saturation, invalid text rollback, and notifications.
     */
    @Test void spinnerClampsRollsBackAndSuppressesDuplicateEvents() {
        NanoNumberSpinner spinner = new NanoNumberSpinner(-2, 2, .5, 0);
        var values = new ArrayList<Double>(); spinner.onChange(values::add);
        spinner.step(1); assertEquals(.5, spinner.getValue());
        spinner.getEditor().text("99"); assertTrue(spinner.commit()); assertEquals(2, spinner.getValue());
        spinner.step(1); assertEquals(List.of(.5, 2.0), values);
        for (String text : List.of("NaN", "Infinity", "no", "")) {
            spinner.getEditor().text(text); assertFalse(spinner.commit()); assertEquals("2.0", spinner.getEditor().getText());
        }
        spinner.value(-2); assertEquals(2, values.size());
        assertThrows(IllegalArgumentException.class, () -> spinner.value(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new NanoNumberSpinner(2, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new NanoNumberSpinner(0, 1, 0, 0));
        NanoNumberSpinner huge = new NanoNumberSpinner(-Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
        huge.step(1); assertEquals(Double.MAX_VALUE, huge.getValue());
        spinner.setEnabled(false); spinner.step(1); assertEquals(-2, spinner.getValue());
    }

    /**
     * Proves picker copy boundaries, ARGB order, invalid-input recovery, and alpha handling.
     */
    @Test void pickerDefensivelyCopiesAndCommitsHexPrecisely() {
        NanoColorPicker picker = new NanoColorPicker();
        Color source = new Color(0x80336699); picker.color(source); source.r(1);
        assertEquals("#80336699", picker.getColor().toHex());
        Color returned = picker.getColor(); returned.a(0); assertEquals("#80336699", picker.getColor().toHex());
        var seen = new ArrayList<String>(); picker.onChange(color -> { seen.add(color.toHex()); color.a(0); });
        picker.getHexField().text("#ff0000"); assertTrue(picker.commitHex());
        assertEquals("#FFFF0000", picker.getColor().toHex()); assertEquals(255, picker.getChannel(3).getValue());
        picker.getHexField().text("00ff0000"); assertTrue(picker.commitHex());
        assertEquals("#00FF0000", picker.getColor().toHex());
        picker.getHexField().text("#GG0000"); assertFalse(picker.commitHex());
        assertEquals("#00FF0000", picker.getHexField().getText()); assertEquals(2, seen.size());
        picker.getHexField().text("#00ff0000"); picker.commitHex(); assertEquals(2, seen.size());
    }

    /**
     * Confirms radio exclusivity, wrapped navigation, and silent programmatic selection.
     */
    @Test void radioSelectionWrapsAndUpdatesEveryButton() {
        NanoRadioGroup radio = new NanoRadioGroup(List.of("A", "B", "C"));
        var changes = new ArrayList<Integer>(); radio.onChange(changes::add);
        radio.selectedIndex(2); assertTrue(changes.isEmpty());
        var key = new KeyPressEvent(Keyboard.RIGHT, 0);
        radio.onInputPreview(new UIInputEvent(key, radio.get(2), radio, Float.NaN, Float.NaN));
        assertTrue(key.isConsumed()); assertEquals(0, radio.getSelectedIndex());
        for (int i = 0; i < 3; i++) assertEquals(i == 0, radio.get(i).isSelected());
        radio.select(0); assertEquals(List.of(0), changes);
        assertThrows(IndexOutOfBoundsException.class, () -> radio.selectedIndex(-2));
        assertEquals(-1, new NanoRadioGroup(List.of()).getSelectedIndex());
    }

    /**
     * Checks ancestor snapshots and prevents lexical navigation above a breadcrumb root.
     */
    @Test void breadcrumbsEmitAncestorsWithoutChangingDisplayedPath() {
        var bar = new NanoBreadcrumbBar().path(temporary, temporary.resolve("one/two"));
        var requested = new ArrayList<Path>(); bar.onNavigate(requested::add);
        NanoButton root = (NanoButton) bar.get(0); root.getAction().perform(root);
        assertEquals(List.of(temporary.toAbsolutePath()), requested); assertEquals(3, bar.size());
        assertEquals(temporary.resolve("one/two").toAbsolutePath(), bar.getPath());
        assertThrows(IllegalArgumentException.class, () -> bar.path(temporary, temporary.resolve("../escape")));
        assertEquals(3, bar.size());
    }

    /**
     * Uses real directories to test ordering, file filtering, activation, and root limits.
     * @throws IOException if temporary setup or a required valid scan fails
     */
    @Test void explorerSortsFiltersAndSeparatesSelectionFromActivation() throws IOException {
        Files.createDirectory(temporary.resolve("folder"));
        Files.writeString(temporary.resolve("z.txt"), "text");
        Files.writeString(temporary.resolve("a.png"), "image");
        NanoFileExplorer explorer = new NanoFileExplorer(temporary);
        assertEquals(List.of("folder", "a.png", "z.txt"), explorer.getEntries().stream().map(e -> e.path().getFileName().toString()).toList());
        var opened = new ArrayList<Path>(); explorer.onOpen(opened::add);
        explorer.filter(path -> path.toString().endsWith(".png")); assertEquals(2, explorer.getEntries().size());
        explorer.select(1); assertTrue(opened.isEmpty()); explorer.openSelection();
        assertEquals(List.of(temporary.resolve("a.png").toRealPath()), opened);
        explorer.select(0); explorer.openSelection(); assertEquals(temporary.resolve("folder").toRealPath(), explorer.getDirectory());
        explorer.navigate(Path.of("..")); assertEquals(temporary.toRealPath(), explorer.getDirectory());
        assertThrows(IOException.class, () -> explorer.navigate(Path.of("..")));
        assertEquals(temporary.toRealPath(), explorer.getDirectory());
    }

    /**
     * Verifies failed rescans and disappeared selections preserve prior browser state.
     * @throws IOException if fixture creation or initial scanning fails
     */
    @Test void explorerFailuresRetainSnapshotAndNotifyOnlyUserErrors() throws IOException {
        Path file = Files.writeString(temporary.resolve("file.txt"), "x");
        NanoFileExplorer explorer = new NanoFileExplorer(temporary); explorer.select(0);
        List<NanoFileExplorer.Entry> before = explorer.getEntries();
        assertThrows(IllegalStateException.class, () -> explorer.filter(path -> { throw new IllegalStateException(); }));
        assertSame(before, explorer.getEntries()); assertEquals(file, explorer.getSelected());
        assertThrows(IOException.class, () -> explorer.navigate(Path.of("missing")));
        assertSame(before, explorer.getEntries());
        var failures = new ArrayList<IOException>(); explorer.onError(failures::add);
        Files.delete(file); explorer.openSelection(); assertEquals(1, failures.size());
        assertSame(before, explorer.getEntries());
        explorer.navigate(temporary); assertTrue(explorer.getEntries().isEmpty()); assertNull(explorer.getSelected());
        assertThrows(UnsupportedOperationException.class, () -> before.clear());
    }

    /**
     * Checks platform-specific hidden-file handling using the filesystem's own flag.
     * @throws IOException if temporary file attributes or scans fail
     */
    @Test void explorerHiddenPolicyKeepsDirectoriesAvailableThroughFileFilters() throws IOException {
        Path hidden = Files.writeString(temporary.resolve(".hidden.txt"), "x");
        if (Files.getFileStore(hidden).supportsFileAttributeView("dos")) Files.setAttribute(hidden, "dos:hidden", true);
        assertTrue(Files.isHidden(hidden));
        Files.createDirectory(temporary.resolve("folder"));
        NanoFileExplorer explorer = new NanoFileExplorer(temporary);
        assertEquals(1, explorer.getEntries().size());
        explorer.showHidden(true); assertEquals(2, explorer.getEntries().size());
        explorer.filter(path -> false); assertEquals(1, explorer.getEntries().size());
        assertTrue(explorer.getEntries().getFirst().directory());
        explorer.refresh(); assertEquals(1, explorer.getEntries().size());
    }

    /**
     * Ensures file callbacks can report their own exceptions without those exceptions
     * being misclassified as filesystem enumeration failures.
     * @throws IOException if fixture creation or scanning fails
     */
    @Test void explorerDoesNotSwallowApplicationCallbackFailures() throws IOException {
        Files.writeString(temporary.resolve("file"), "x");
        NanoFileExplorer explorer = new NanoFileExplorer(temporary);
        explorer.onOpen(path -> { throw new IllegalStateException("application"); });
        explorer.onError(failure -> fail("Application failure was treated as IO")); explorer.select(0);
        assertThrows(IllegalStateException.class, explorer::openSelection);
    }
}
