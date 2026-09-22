# File, menu, and editing widgets

Author: Albert Beaupre

These eight controls live in `valthorne.ui.nodes` and participate in the existing
`UIRoot` layout, focus, pointer, theme, and mixed-renderer lifecycle. Configure them
on the UI thread and attach them to an existing root or container. The snippets
below assume an initialized application; they are integration fragments.

| Widget | Use it for | Main interactions |
| --- | --- | --- |
| `Window` | Floating inspectors and tool panels | Editable title, optional dragging, optional edge/corner resizing, focus and stacking |
| `FileExplorer` | Browsing local assets inside a chosen directory | Select rows, Open/Enter, Up/Backspace, refresh, breadcrumbs |
| `FileChooser` | Choosing Open items or a Save destination | Filename entry, filters, multiple selection, history, drives, approval/cancellation |
| `DirectoryTree` | Navigating expandable folder hierarchies | Lazy expansion, arrow keys, synchronized directory selection |
| `BreadcrumbBar` | Requesting navigation to an ancestor | Pointer or keyboard button activation |
| `MenuBar` | Grouping application commands | Activate headings; Left/Right switches open menus |
| `PopupMenu` | A scrollable command or option menu | Up/Down, Home/End, Enter/Space, Escape, outside dismissal |
| `ComboBox<T>` | Choosing one value from a dropdown | Button activation, popup navigation and selection |
| `ColorPicker` | Editing packed RGBA colors | HSV wheel, brightness, RGBA sliders, hex entry, alpha preview |
| `NumberSpinner` | Editing bounded numeric values | Decimal entry, stepping buttons, Up/Down, Home/End |
| `RadioGroup` | Showing a small set of mutually exclusive choices | Buttons and wrapping arrow navigation |

## Shared setup and ownership

For floating titled containers, see the [window guide](ui-windows.md). It covers
drag/resize policies, geometry limits, parent containment, clipping, and notifications.

Use an existing theme or `ProfessionalTheme` to supply button, label, text-field,
slider, and scroll-panel styles. Composite controls reuse those existing rules.
Labels need a font supplied by your theme. The color preview uses NanoVG through
the root's normal mixed-renderer dispatch; no separate NanoVG frame is needed.

Assign dimensions through `getLayout()`. The file explorer especially needs a
bounded height, such as 400 units, to establish its virtualized viewport.

The hierarchy owns child nodes. Accessors such as `getEditor()`, `getChannel()` and
`getHeading()` expose those children for styling and focus; do not reparent them or
replace the composite's internal children. Dispose the root before disposing the
theme resources used by its descendants. Popup-owning controls close their overlays
when detached, including when a containing panel is removed.

Callbacks are synchronous. They see committed state, may open another modal after
a menu command closes, and propagate application exceptions. Most value widgets
distinguish silent programmatic setters from notifying user operations, as described
below. None of these widgets introduces a background thread or a global shortcut.

## File explorer

```java
FileExplorer explorer = new FileExplorer(Path.of("assets"));
explorer.getLayout().width(520).height(400);
explorer.filter(path -> path.getFileName().toString().endsWith(".png"));
explorer.onOpen(path -> openImageInEditor(path));
explorer.onError(failure -> showError(failure.getMessage()));
root.add(explorer);
```

Construction resolves the supplied directory to a real path and makes it the
browsing root. Use `new FileExplorer(initialDirectory, false)` to allow navigation
outside that initial directory, including other filesystem roots. The browser never writes, deletes, renames, or launches files.
`onOpen` hands an existing canonical file path to your application. Directory
activation navigates instead of calling `onOpen`.

The toolbar supplies **Up**, **Refresh**, and **Open**. A single row activation
selects it; double-click, Open, or Enter activates that selection. Up/Down and Home/End change the
selection and reveal the chosen row. Backspace requests the parent and stops at the
root. Breadcrumb buttons navigate to displayed ancestors; a horizontal scroll area
contains long paths.

`navigate(path)` resolves relative destinations against the current directory.
`refresh()` rescans the current location. Both throw `IOException` to direct callers.
Toolbar and keyboard IO failures retain the previous listing, display the error in
the status line, and call `onError`. Application callback exceptions are propagated
without being relabeled as filesystem failures.

`filter(predicate)` applies only to files so folders remain navigable.
`showHidden(true)` includes entries reported as hidden by the active filesystem;
the meaning of hidden is platform-dependent. Entries sort directories first, then
case-insensitive filename order with exact-name tie breaks. Successful scans reset
selection; failed scans preserve the previous directory, selection, and listing.

`multipleSelection(true)` enables Ctrl/Super-click toggles, Shift-click or Shift-arrow
ranges, and Ctrl/Super+A. `getSelectedPaths()` returns an immutable snapshot in displayed
order. `onSelectionChange` observes selection changes and clearing during successful
scans. `onDirectoryChange` observes actual directory changes, including breadcrumbs and
double-click navigation, but not a refresh of the same directory.

## File chooser

Use `FileChooser` for the Open/Save workflow normally provided by `JFileChooser`.
It uses Valthorne controls and the current theme, with asynchronous result delivery
through callbacks while the normal render loop continues.

```java
FileChooser chooser = new FileChooser(Path.of("assets"));
chooser.filters(List.of(
        new FileChooser.Filter("Images (*.png, *.jpg)", List.of("png", "jpg")),
        new FileChooser.Filter("All files", List.of())));
chooser.onApprove(paths -> importImages(paths));
chooser.onCancel(() -> showStatus("Nothing selected"));
chooser.multipleSelection(true);
chooser.showDialog(openButton); // openButton must already belong to a UIRoot
```

The chooser starts in **Open** mode. Single-click selects a row, double-click enters
a folder or approves a file, and the bottom **Open** button approves the current
candidate. **Cancel** and Escape dismiss the modal and call `onCancel` without an
approval. Escape inside an open filter dropdown dismisses that dropdown first. Modal
dismissal restores prior focus before notifying the application. Reopen with the same
owner using `showDialog(owner)`; the chooser retains its current directory and policy.

The chooser follows a desktop file-dialog layout: title and path/search row at the
top, an expandable directory tree on the left, a file-details pane on the right,
and labeled filename/type fields with right-aligned Open/Save and Cancel buttons below.
`ProfessionalTheme` supplies square fields, flat rows, folder/document icons, white
content panes in light mode, and corresponding dark-mode colors. Drag the divider to
resize the two panes.

**Back**, **Forward**, **Up**, and **Refresh** sit beside the breadcrumb path. Click a
path segment to visit an ancestor. Use the **...** button or Ctrl/Super+L to edit an
absolute or current-directory-relative path; Enter navigates and Escape returns to
breadcrumbs without cancelling the dialog. Alt+Left/Right traverse history, Alt+Up
visits the parent, and F5 refreshes. Failed navigation retains the listing/history
and reports the error inline. The current folder stays visible at the end of long
breadcrumb paths.

The left pane lists existing Desktop, Documents, Downloads, Pictures, Music, and
Videos directories, the user's home, and filesystem roots/drives. Disclosure buttons
expand immediate child folders; clicking a folder navigates the right pane. Right/Left
expand/collapse branches, Up/Down move focus, and Enter navigates. Successful navigation
reveals the current directory in the tree. Only expanded branches are scanned, and
visible row widgets are virtualized. Standard expansion omits hidden folders and
symbolic links to avoid cycles; an explicitly navigated path can still be revealed.

The right pane shows icons, **Name**, and **Date modified** columns. Click a heading
to sort, then again to reverse direction; folders stay first. Search filters both file
and folder names in the current directory using case-insensitive substring matching.
It does not recursively scan descendants and clears when the directory changes.
The **Hidden files** toolbar toggle includes hidden listing entries. Directory and
extension filtering remain independent.

**New folder** opens an inline name editor. Create or Enter creates one child directory
and refreshes the listing; Cancel dismisses that editor. Empty names, separators,
dot/parent names, and existing entries are rejected without replacing anything.
This command does change the filesystem. Open/Save approval itself only returns paths.
`createFolder(name)` exposes the same operation for application commands.

The filename field accepts an absolute or current-directory-relative path. Enter
performs the same operation as the approval button. A nonempty typed name takes
precedence over row selection. Selecting one row fills the field; selecting multiple
rows clears it, allowing approval of the complete selected set. Navigation and rescans
clear selection and the filename field. An empty field with no row selection selects
the current directory in a directory-capable Open policy.

| Configuration | Behavior |
| --- | --- |
| `mode(Mode.OPEN)` | Requires existing readable items; the default mode. |
| `mode(Mode.SAVE)` | Returns one writable file destination; it does not write a file. |
| `selectionMode(SelectionMode.FILES)` | Open accepts regular files; approving a folder navigates into it. |
| `selectionMode(SelectionMode.DIRECTORIES)` | Open accepts folders, including the current directory. |
| `selectionMode(SelectionMode.FILES_AND_DIRECTORIES)` | Open accepts both supported item types. |
| `multipleSelection(true)` | Enables modifier/range selection in Open; Save always selects one file. |
| `filters(list)` | Installs a nonempty list and selects the first filter after a successful rescan. |

Filters match case-insensitive suffixes. Supply extensions without a leading dot;
compound extensions such as `tar.gz` are supported. An empty extension list means
all files. Folders remain visible regardless of filter so navigation always works.
Approval also checks the active filter, preventing typed names from bypassing it.

```java
FileChooser save = new FileChooser(Path.of("projects"))
        .mode(FileChooser.Mode.SAVE)
        .onApprove(paths -> saveProject(paths.getFirst()));
save.filters(List.of(new FileChooser.Filter("Scene (*.json)", List.of("json"))));
save.getFilenameField().text("untitled"); // resolves to untitled.json
save.showDialog(saveButton);
```

Save appends the first allowed extension only when the final name contains no dot.
The parent directory must already exist and be writable. An existing file must be
writable and requires an explicit **Replace** decision; **Keep editing** dismisses
that decision. `getPendingOverwrite()` exposes the exact pending target, and
`confirmOverwrite()` revalidates it before approval. Editing the filename or changing
directory, filter, or mode invalidates the previous confirmation. Nothing is written
by either action. The application owns opening/saving and must handle IO failures or
filesystem changes that occur after the chooser returns.

`onApprove` receives an immutable list of canonical paths after modal dismissal.
`approve()` returns true only when it delivered an approval immediately; false can
mean navigation, a validation error, or a pending overwrite decision. `getStatus()`
returns the inline message. Application callback exceptions propagate.

For an embedded chooser, assign a bounded size (560×440 or larger recommended) and
add it directly to a container instead of calling `showDialog`. Embedded approval and
cancellation notify the owner while leaving the control visible. Do not reparent its
children or replace the browser callbacks that the chooser uses internally. This is
a synchronous local-filesystem chooser: it supplies no OS shell thumbnails, native
places integration, filesystem watching, or background network-directory scanning.

`getDirectoryTree()`, `getSearchField()`, `getFilterBox()`, and `getExplorer()` expose
owned controls for focus, inspection, or styling. Their internal synchronization
callbacks belong to the chooser. `editAddress()` begins path entry; `refresh()`
refreshes the right listing and current tree branch. Light/dark appearance follows
the active theme rather than invoking an OS-owned dialog.

## Directory tree and explorer details

```java
DirectoryTree tree = new DirectoryTree(List.of(projectRoot));
tree.onNavigate(path -> requestDirectory(path));
tree.onError(failure -> showError(failure.getMessage()));
tree.reveal(currentDirectory);
```

`expand(path)` scans one branch and returns whether it succeeded. `collapse(path)`
hides descendants while retaining their expansion state. `reveal(path)` selects and
expands ancestors without emitting navigation back to the application.
`getVisibleBranches()` returns immutable `Branch(path, depth)` values. Failed scans
preserve the previous tree snapshot. The tree is synchronous and does not watch for
external changes; re-expanding a branch refreshes its immediate children.

For a standalone explorer, `details(true)` enables the icon/name/date presentation,
and `chrome(false)` hides its own toolbar, breadcrumbs, and status when a parent
supplies those controls. `sort(modified, descending)` changes name/date ordering
transactionally. `search(text)` filters current-directory names, including folders.
`getList()` exposes its owned virtual viewport for focus and inspection.

`getEntries()` returns an immutable snapshot of `Entry(path, directory)` values.
`getSelected()` returns the selected snapshot path or null. `select(index)` accepts
a displayed index or -1 to clear; it does not open the item. Metadata can become
stale between scans, so activation resolves the selected path again. In a confined
explorer, symbolic-link destinations outside the construction root are rejected. This navigation policy
does not replace filesystem permissions or protect against concurrent filesystem
changes as an operating-system sandbox would.

Visible row widgets are virtualized; the full directory listing still resides in
memory. Scans and file predicates run synchronously, including initial construction.
For slow network shares or huge directories, use an application-managed asynchronous
listing model rather than invoking a synchronous scan in an animation loop.

## Breadcrumb bar

```java
BreadcrumbBar bar = new BreadcrumbBar()
        .path(projectRoot, currentDirectory)
        .onNavigate(path -> requestNavigation(path));
```

`path(root, current)` normalizes absolute paths and rejects lexical paths outside
the root before replacing the displayed buttons. Clicking a button emits that
ancestor to `onNavigate`; it does not update the bar or query the filesystem. After
successful navigation, update `path` yourself. `getPath()` returns the displayed
location, or null before configuration. Use horizontal scrolling for deep paths.

## Menu bar and popup menu

```java
MenuBar bar = new MenuBar();
bar.addMenu("File", List.of(
        new PopupMenu.Item("Save", this::save, true),
        new PopupMenu.Item("Export", this::export, canExport)));
bar.addMenu("Edit", List.of(
        new PopupMenu.Item("Undo", this::undo, canUndo)));
root.add(bar);
```

A `PopupMenu.Item` is an immutable label, callback, and enabled flag. Disabled rows
remain visible and are skipped during arrow navigation. To change availability,
replace the menu's item snapshot through `bar.getMenu(index).items(newItems)`.
Replacing items dismisses any open menu. `getHeading(index)` exposes the heading
button for styling or disabling. An empty menu does not open.

Heading buttons open menus through normal click, Enter, or Space activation.
Inside an open menu, Up/Down wrap through enabled commands, Home/End choose the
first/last available command, and Enter/Space activate the focused choice. Tab-driven
focus also determines activation. Left/Right switches between available menus in
the same bar. Escape and outside presses dismiss the popup. Modal routing prevents
that outside gesture from activating controls underneath. Closing restores the
previous eligible focus target.

Commands run after their popup closes. A command can therefore create another
dialog without retaining the old menu's modal focus scope. `closeMenus()` dismisses
all menus owned by a bar without changing its commands.

For an independently anchored popup:

```java
PopupMenu commands = new PopupMenu().items(List.of(
        new PopupMenu.Item("Inspect", this::inspectSelection, true)));
commands.showBelow(inspectButton);
```

`showBelow(anchor)` requires an attached enabled anchor, places the popup below it
or flips above, clamps it to the root, and virtualizes more than eight rows into a
scrollable viewport. A standalone popup's owner must call `close()` when its anchor
is removed. `isOpen()` reports actual root attachment. `activate(index)` also exposes
enabled-command dispatch for application code; it closes before invoking the command.
These are flat menus; nested submenu trees and global shortcut registration are not
implemented by this API.

## Combo box

```java
ComboBox<String> quality = new ComboBox<String>()
        .items(List.of("Low", "Medium", "High"))
        .selectedIndex(1)
        .onChange(value -> setQuality(value));
```

`items` snapshots a nonnull list with no null elements and clears selection.
`selectedIndex(index)` silently selects an index or -1 for no selection. The empty
state displays `Select...`; `getSelected()` then returns null. `select(index)` is
the notifying operation, used by user interaction: repeated selection of the same
index does not notify. `getItems()` returns the immutable list, but values themselves
are borrowed rather than deep-copied.

`formatter(function)` maps values to nonnull labels. Formatting happens on the UI
thread; keep it inexpensive and deterministic. Failure while formatting a new
selection preserves the prior selection and trigger label. `open`, `close`, and
`isOpen` manage the dropdown explicitly. Removing it closes its owned popup.

## Color picker

```java
ColorPicker picker = new ColorPicker()
        .mode(ColorPicker.Mode.WHEEL)
        .color(new Color(0x80336699))
        .onChange(color -> updateMaterialTint(color));
picker.getLayout().width(280);
```

The four sliders edit red, green, blue, and alpha as integer values from 0 through
255. A checkerboard preview shows transparency. Hex entry accepts six RGB digits
or eight **ARGB** digits, optionally prefixed with `#`, with surrounding whitespace
ignored. Six-digit RGB sets alpha to 255. For example, `#80FF0000` is half-transparent
red. Eight-digit text is **not** interpreted as CSS RRGGBBAA.

`color(value)` silently copies and synchronizes the complete value. `getColor()` and
`onChange` provide defensive copies. Committing invalid hex text restores the previous
hex value and returns false from `commitHex()` without firing a change. Valid input
returns true even if unchanged; callbacks fire only for changed packed values.

`getChannel(0..3)` and `getHexField()` expose child controls for focus and styling.
Changing a slider directly with its silent `value` setter is not a complete-picker
operation: use `color` for programmatic updates. Calling `commitHex()` applies pending
field text with the same semantics as Enter.

`mode(Mode.SLIDERS)` is the backward-compatible default. `Mode.WHEEL` replaces the
RGB rows with a hue/saturation disc and **Value** slider, keeping alpha, preview, and
hex input. `Mode.BOTH` displays both editors. Mode changes preserve color and do not
notify. Reserve at least 204 units of height for sliders, 360 for the wheel, or 456
for both; the wheel surface is 220 units tall by default.

On the wheel, angle chooses hue and distance from the center chooses saturation:
the center is neutral and the rim is fully saturated. Click inside the disc or drag
from it; drags beyond the disc clamp to its rim. Red begins at the right, and hue
increases clockwise. Left/Right adjust hue by two degrees; Up/Down adjust saturation
by one percent; Home/End choose neutral/full saturation. **Value** controls HSV
brightness independently of alpha. Lowering Value to black retains hue/saturation
so raising it restores the previous hue. The checkerboard preview shows opacity;
the wheel itself remains opaque for readability.

`selectHSV(hue, saturation, brightness)` applies the same notifying user operation:
hue is measured in turns and wraps, saturation/value clamp to 0..1, nonfinite inputs
are rejected, and alpha is preserved. All editing surfaces stay synchronized.
`getWheel()` and `getBrightness()` expose focus/style targets without transferring
ownership. Direct slider setters remain silent; use `color` for a complete update.

## Number spinner

```java
NumberSpinner speed = new NumberSpinner(0, 100, 0.5, 10)
        .onChange(value -> setMovementSpeed(value));
```

The constructor takes inclusive minimum, maximum, positive step, and initial value.
All must be finite and bounds must be ordered. Initial and later values clamp to
the range. `value(number)` is silent; `getValue()` reads committed state, which may
differ from pending editor text.

The buttons and Up/Down step from the committed value. Home/End choose the bounds.
Steps saturate rather than wrap, including arithmetic overflow. Manually entered
values are not quantized to step multiples. Enter or `commit()` parses decimal text,
rejects NaN/infinity/malformed input, and restores the previous value on failure.
Callbacks fire only after an actual numeric change. `getEditor()` exposes the owned
text field; `step(direction)` uses negative, positive, or zero direction for decrease,
increase, or no change respectively.

## Radio group

```java
RadioGroup mode = new RadioGroup(List.of("Windowed", "Borderless", "Fullscreen"))
        .selectedIndex(0)
        .onChange(index -> setDisplayMode(index));
```

Nonempty groups initially select the first option. `selectedIndex` silently changes
selection; -1 clears it. `select` performs a notifying user-style change and focuses
the corresponding button when attached. Repeating the same selection does not notify.
Left/Up and Right/Down wrap through choices. The selected state is shared with the
theme, so a selection remains visible after focus moves elsewhere. `getLabels()` is
an immutable snapshot and `getSelectedIndex()` returns -1 for an empty selection.

## Regression tests

Run `./gradlew verifyWidgets` (`gradlew.bat verifyWidgets` on Windows). The committed
tests are in `src/widgetTest/java/valthorne/ui/widgets`, which is part of the normal
test source set and survives without the repository's ignored local `src/test` tree.

The behavior suite covers snapshots, validation, callback ordering, disabled controls,
hex alpha, bounded stepping, directory sorting/filtering, hidden files, failed scans,
and disappeared selections. The graphics suite creates a hidden native window and
checks real pixels, pointer and keyboard routing, popup focus restoration, menu
switching, mixed texture/NanoVG trees, owner removal, and directory-row virtualization.
It requires a working OpenGL display/driver and does not silently skip pixel checks.

`verifyUI` also runs these widgets alongside the existing UI regressions. Each test
class uses a separate process because native fixtures own global Window/GLFW state.
The normal `test` task excludes graphics-tagged tests for headless runs. These checks
cover the host platform on which they run; they do not imply cross-platform validation.

See [UI foundations](ui-system.md), [advanced controls](ui-advanced.md),
[standard controls](systems/ui-controls.md), and [themes](systems/ui-themes.md).
