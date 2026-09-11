# Advanced UI controls

All components use the existing UI tree and accept texture or Nano nodes without
conversion. They are UI-thread-only. Resources follow the normal attach/detach
lifecycle; retain durable data outside transient virtual rows.

## Variable-height lists and selection

```java
VirtualList records = new VirtualList(100_000, index -> new NanoButton("Record " + index));
records.rowHeight(40).gap(4).variableHeights().selectable(true);
records.getLayout().width(600).height(500);
root.add(records);
records.itemHeight(12, 96); // content height excluding gap
records.itemHeight(13, 72); // coalesced until the next layout/update
records.getSelection().onChange(() -> {
    int selectedCount = records.getSelection().selectedCount();
});
```

Measurements are explicit, not inferred by measuring every offscreen widget.
`rowHeight` supplies the initial estimate; calling it again returns to fixed-height
mode. Measurements preserve the visible scroll anchor and coalesce geometry work.
Variable heights require one column. Changing gap adjusts all measurements and
is a data-scale operation. `itemCount` preserves existing measurements and initializes
new indices from the estimate. Reordering data requires resetting/remapping heights.

`RowHeightIndex` uses primitive arrays and a Fenwick tree: O(log n) pixel lookup,
prefix sum and point update, approximately 12 bytes per row plus array headers.
Fixed-height lists do not allocate this index. Final Yoga/scroll coordinates remain
floats; extremely tall documents lose pixel precision. Supply reasonable positive
finite pixel heights. No automatic wrap-based measurement or variable-height grids.

Selection is opt-in. Shift-click extends from the anchor; Ctrl/Cmd-click toggles;
Ctrl/Cmd+Shift-click adds a range. Unconsumed arrows, Home/End and Ctrl/Cmd+A
navigate/select; nested editors retain consumed commands. Selection state is
restored when virtualization recreates a row. The professional theme highlights
selected buttons and tab headers; custom row skins can use `StyleState.SELECTED`.

Data indices, not stable keys, define identity. Sorting/reordering requires explicit
application remapping or clearing. The list owns the selection model's item count;
do not independently resize it. Focus/capture pin rows until interaction ends.

Standalone `SelectionModel` supports single/multiple selection, anchor/lead,
iteration and count changes with bit-packed storage. Storage grows with the
highest selected index; select-all uses approximately one bit per item. Range
operations are O(affected machine words), not universally O(1). Do not modify the
selection inside `forEachSelected`; iterate a separately captured index list if needed.

## Lazy tabs and split panes

```java
TabbedPane tabs = new TabbedPane();
tabs.addTab("Document", () -> new NanoTextField("Retained editor"));
tabs.addTab("Settings", Panel::new);
VirtualList records = new VirtualList(10_000, i -> new Button("Record " + i));
SplitPane workspace = new SplitPane(tabs, records)
        .ratio(.55f).minimumSizes(200, 180).dividerSize(10);
workspace.getLayout().width(1000).height(600);
root.add(workspace);
```

Tabs instantiate pages once on first selection, retain their state and skip updates
and drawing for inactive pages. Hidden pages remain attached and participate in
full-tree layout/lifecycle operations. Headers support Left/Right/Home/End and
skip disabled headers. Switching away from a focused page moves focus to the new
header. Removing a page runs normal destruction once. Factories must return fresh
unattached nodes; failures propagate. Use `addTab`/`removeTab`, not inherited
structural methods, to preserve the managed structure. Tabs are not dockable or
reorderable and overflowing header bars do not yet scroll automatically.

Split panes accept either renderer. The divider captures primary dragging, handles
cancellation, supports directional arrows (Shift for larger steps), and Home/End.
Minimum sizes clamp movement; when minima cannot fit, space is shared proportionally.
`vertical(true)` selects top/bottom; default is left/right. `getRatio` is the requested
ratio, `getEffectiveRatio` is the last layout's constrained ratio. Changes apply at
the next layout. `first()`/`second()` expose content containers; do not replace the
split's managed direct children. Nested splits can compose multi-pane workspaces.

## Searchable, sortable data tables

```java
DataTable<MyRecord> table = new DataTable<>(List.of(
    TableColumn.text("Name", 3, MyRecord::name),
    new TableColumn<>("Count", 1,
        row -> new Label(Integer.toString(row.count())),
        Comparator.comparingInt(MyRecord::count))
));
table.getLayout().width(800).height(500);
table.rows(records);
root.add(table);
table.filter(row -> row.name().contains(query));
```

The header stays fixed while the virtualized rows scroll. Click or keyboard-activate
a header to cycle ascending, descending and original order. Sorting is stable:
equal keys keep source order. Column widths are proportional weights, not pixels.
The convenience text column sorts lexicographically, with nulls first. Supply
typed comparators for numeric/date values and pure cell factories returning fresh
unattached nodes. A null comparator makes a column non-sortable. Cells clip both
painting and hit testing and can contain either renderer, including interactive controls.

`getSelection()` supports single/range/toggle selection. A projection change clears
index selection and resets scrolling to prevent silently selecting a different
record. Use `getModel().get(visibleIndex)` to resolve selected rows, or
`sourceIndex(visibleIndex)` for the original index. Selection does not survive
sort/filter automatically. Empty projections display "No matching rows".

`TableModel` takes a shallow immutable copy of the supplied row list. It maintains
primitive visible indices: reads allocate nothing, filtering is O(n), stable sorting
is O(k log k), and rebuilding uses temporary O(n + k) storage. Mutations happen
synchronously on the UI thread, never automatically per frame. Call `refresh()`
after mutable row values change. Predicates/comparators must be pure; their failures
leave the prior projection intact. Listener failures propagate after commit.
Debounce expensive search queries in application code for very large datasets.

This first table supports fixed-height rows and fixed column weights. It does not
yet provide column resizing/reordering, horizontal column virtualization, editing
transactions, stable-key selection remapping, or multi-column sorting. Per-cell
clipping can introduce rendering batch boundaries; it is not a free operation.

Try **05 Data tools** in the showcase: 10,000 records, text search, numeric ID sort,
mixed Nano/texture cells, and selection. The search skips identical queries, so
moving the caret does not rebuild the projection. Run `./gradlew benchmarkUITable`
to measure reads/filtering/sorting at 1,000 and 100,000 rows. Raw results are written
to `build/reports/ui-benchmark/table.json`; this suite is separate from the existing
16-case UI allocation gate because data mutations deliberately allocate.

## Collapsible sections

```java
CollapsibleSection details = new CollapsibleSection("Details", form);
details.expanded(false);
root.add(details);
```

The header supports mouse/Enter/Space activation and Left/Right collapse/expand.
Collapsed contents retain state but are not drawn or updated, and the body consumes
zero layout height. Collapsing focused or captured content returns focus to the
header and cancels active capture. Re-expanding does not recreate the contents.
The normal destruction lifecycle runs when the section is removed. Stack sections
inside a scroll panel for an inspector or settings form. This is a disclosure
component, not an exclusive-open accordion group; sections expand independently.

## Notifications and text costs

`ChangeSignal` has allocation-free dispatch with a copy-on-mutation listener array.
Subscribe/unsubscribe costs O(listener count); dispatch uses a stable snapshot.
Changes during callbacks affect the next dispatch, including nested dispatch.
Subscriptions are independent and closing them is idempotent. Callbacks are
synchronous; exceptions propagate and stop remaining callbacks. Close externally
owned subscriptions when their owner is disposed.

Text editing uses direct ASCII boundaries and a reusable primitive boundary buffer
for Unicode; Unicode forward navigation uses binary search. Sanitizing already-clean
text returns the same immutable string. Unicode edits still run grapheme matching;
edits and undo snapshots still allocate strings. Undoing deletion restores the
original caret/selection, not the temporary deletion range. This does not make
arbitrary typing, listener code or every frame allocation-free.

## Remaining capability roadmap

This is an expanded foundation, not "every possible UI feature". Significant
remaining systems include virtual trees and advanced table column management,
docking/reordering, drag-and-drop payload routing, menu bars/context menus,
multiline/rich text, full text shaping/bidi/font fallback/IME composition,
native accessibility bridges, animation scheduling, gamepad navigation, reusable
row pools, and GPU render-target caching. Each needs its own lifecycle/input
contract, tests and representative benchmarks before being called production-ready.
