# Virtual lists, tables, and selection

Author: Albert Beaupre

[System manual](README.md)

## Purpose

VirtualList and DataTable render large datasets by materializing only the visible row range. TableModel stores the source and filtered/sorted projection; SelectionModel handles selection policy. RowHeightIndex supports efficient offsets when rows are not all the same height.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Virtualization | Visible-range materialization limits the number of active row widgets. |
| Binding | Factories create row widgets and binders update them for the current data item. |
| Sorting and filtering | A visible projection can differ from source order and membership. |
| Columns | TableColumn describes displayed values, sizing, and optional comparison behavior. |
| Selection and scrolling | Selection tracks logical choices while scroll methods reveal projection indices. |

## Getting started

1. Choose the model and populate source items.
2. Supply a row factory/binder or table columns appropriate to the data.
3. Configure row heights, selection policy, and optional sorting/filtering.
4. Update the model through its API and let the virtualized control refresh visible widgets.
5. Treat row indices as projection indices where the method contract specifies them.

## Ownership and lifecycle

A materialized row widget can later represent another item. Keep persistent data in the model rather than in recycled widget identity. Callbacks should avoid restructuring the same collection during an operation that forbids reentrancy.

## Important behavior

- A filtered index is not necessarily the original source index.
- `scrollToRow` rejects indices outside the current visible projection.
- Variable heights need consistent indexing; stale height data can produce incorrect offsets.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`RowHeightIndex`](#type-rowheightindex)
- [`SelectionModel`](#type-selectionmodel)
- [`TableModel`](#type-tablemodel)
- [`DataTable`](#type-datatable)
- [`TableColumn`](#type-tablecolumn)
- [`VirtualList`](#type-virtuallist)

<a id="type-rowheightindex"></a>

### RowHeightIndex

[Source](../../src/main/java/valthorne/ui/behavior/RowHeightIndex.java#L35)

Maintains measured row strides and cumulative offsets for a variable-height
list without scanning every preceding row. A Fenwick tree stores partial sums
in double precision, while individual heights remain floats. Point updates,
prefix sums and position lookup take O(log n) time; construction and resizing
take O(n) time and storage uses approximately 12 bytes per row, plus array overhead.

Use one consistent coordinate unit, normally UI pixels. Each stored height
includes any trailing gap; the index does not track content height and spacing
separately. Rows occupy intervals from their starting offset, inclusive, to
the next offset, exclusive. All strides must be finite and strictly positive.

```java
RowHeightIndex rows = new RowHeightIndex(1000, 28f);
rows.set(3, 52f);
double fifthRowTop = rows.offset(4);
int firstVisible = rows.indexAt(120);
if (firstVisible < rows.size()) {
    float visibleRowStride = rows.height(firstVisible);
}
```

This mutable index is intended for UI-thread use and provides no
synchronization. Successful queries and point updates allocate no objects;
resizing replaces backing arrays. Empty indexes are supported, with zero
total height and zero as the end sentinel returned by position lookup.

<details>
<summary>RowHeightIndex operation reference (8 declarations)</summary>

#### Constructor

```java
public RowHeightIndex(int count, float estimatedHeight)
```

Creates an index with every row initialized to the same estimated stride.
The estimate is validated even for an empty index, and the cumulative
structure is built immediately in linear time.

- **`count`** — the initial row count, from zero through Integer.MAX_VALUE - 1
- **`estimatedHeight`** — the finite positive initial stride in UI coordinate units

**Throws `IllegalArgumentException`:** if count is negative or Integer.MAX_VALUE,
or the estimate is non-finite or not positive

#### size

```java
public int size()
```

Returns the current row count. This is also the valid exclusive endpoint
for `offset(int)` and the past-end sentinel for `indexAt(double)`.

**Returns:** the number of indexed rows, possibly zero

#### height

```java
public float height(int index)
```

Reads a row's current stride in constant time, including any gap captured
in the estimate or most recent measurement. The result is not a prefix sum.

- **`index`** — the zero-based row index

**Returns:** the row's finite positive stride

**Throws `IndexOutOfBoundsException`:** if index is outside the current rows

#### set

```java
public void set(int index, float height)
```

Replaces one row's stride and propagates its difference through the partial
sums in logarithmic time. An unchanged value returns without updating the
tree. Validation completes before any state changes; callers must separately
update layout or scroll anchors affected by the new measurement.

- **`index`** — the zero-based row to update
- **`height`** — the finite positive replacement stride, including any gap

**Throws `IndexOutOfBoundsException`:** if index is outside the current rows

**Throws `IllegalArgumentException`:** if height is non-finite or not positive

#### offset

```java
public double offset(int endExclusive)
```

Sums row strides in the half-open range `[0, endExclusive)` in
logarithmic time. Passing a row index gives that row's starting position;
zero returns zero and `size()` returns the entire indexed extent.

- **`endExclusive`** — the number of leading rows to include, from zero through size

**Returns:** the cumulative stride in the same units as the stored heights

**Throws `IndexOutOfBoundsException`:** if the endpoint is negative or greater than size

#### totalHeight

```java
public double totalHeight()
```

Returns the sum of all row strides, including the final row's trailing gap
if one was supplied. Empty indexes return zero. This delegates to a
logarithmic prefix query rather than maintaining a separate cached total.

**Returns:** the total indexed extent in UI coordinate units

#### indexAt

```java
public int indexAt(double offset)
```

Locates a position within the cumulative strides in logarithmic time.
A row's starting boundary belongs to that row; positions in its trailing
gap also belong to it because spacing is included in the stride.

Negative positions, including negative infinity, clamp to zero. Positions
at or beyond the total extent, including positive infinity, return
`size()`, which is a sentinel rather than a valid row index. An
empty index returns zero for every non-NaN input.

- **`offset`** — the position measured from the first row's start

**Returns:** the containing zero-based row, or size when past the indexed rows

**Throws `IllegalArgumentException`:** if offset is NaN

#### resize

```java
public void resize(int count, float estimatedHeight)
```

Changes the row count while retaining measurements at surviving indices.
Growing fills only new rows with the estimate; shrinking discards removed
measurements, which are not recovered by a later expansion. A changed
count reallocates storage and rebuilds partial sums in linear time.

The estimate and count are validated even when the count is unchanged.
For that case, valid arguments produce no allocation or measurement change.

- **`count`** — the replacement row count, from zero through Integer.MAX_VALUE - 1
- **`estimatedHeight`** — the finite positive stride for newly added rows

**Throws `IllegalArgumentException`:** if count is negative or Integer.MAX_VALUE,
or the estimate is non-finite or not positive

</details>

<a id="type-selectionmodel"></a>

### SelectionModel

[Source](../../src/main/java/valthorne/ui/behavior/SelectionModel.java#L35)

Mutable index-based selection for lists, grids and trees, with single-item,
toggle and inclusive range policies. Membership uses a bit set without
allocating an object for each item. The anchor starts an extended range and
the lead tracks the most recent selection target; either can refer to an
unselected item after a toggle, or be -1 when unset.

```java
SelectionModel selection = new SelectionModel(100, true);
selection.select(10, false, false);
selection.select(15, true, false);
selection.select(30, false, true);
selection.forEachSelected(index -> System.out.println(index));
```

Selections refer to positions, not data identities. Changing item order
requires application-level remapping or clearing; changing the item count
only removes out-of-range membership and adjusts invalid anchor/lead values.
This model does not manipulate focus, scroll position or rendered item state.

Use on the UI thread; operations are not synchronized. Notifications are
synchronous and occur after state updates, at most once per direct mutator
call. Some no-op cases return silently, while repeated range requests still
notify. Listener failures propagate without rolling back the updated state;
listener-driven mutations can produce nested notifications.

<details>
<summary>SelectionModel operation reference (15 declarations)</summary>

#### Constructor

```java
public SelectionModel(int count, boolean multiple)
```

Creates an empty selection over the supplied number of items. Anchor and
lead start unset, regardless of whether multiple selection is enabled.

- **`count`** — the nonnegative number of selectable positions
- **`multiple`** — whether more than one position may be selected

**Throws `IllegalArgumentException`:** if count is negative

#### itemCount

```java
public int itemCount()
```

Returns the current upper exclusive bound for valid item indices. This
describes the available data range, independently of selection membership.

**Returns:** the nonnegative item count

#### anchor

```java
public int anchor()
```

Returns the origin used for extended selection ranges. Toggling an item
sets this origin even when that operation removes the item from selection.

**Returns:** the range-origin index, or -1 when unset

#### lead

```java
public int lead()
```

Returns the latest selection target used by keyboard navigation and range
extension. The target need not currently belong to the selected set.

**Returns:** the lead index, or -1 when unset

#### isMultiple

```java
public boolean isMultiple()
```

Reports whether toggle and range policies are enabled. When false,
selection requests replace membership with a single target.

**Returns:** whether multiple selection is enabled

#### isSelected

```java
public boolean isSelected(int index)
```

Tests membership without requiring the caller to validate an index first.
Negative indices and positions at or beyond the item count return false.

- **`index`** — the position to test

**Returns:** true only for an in-range selected item

#### selectedCount

```java
public int selectedCount()
```

Counts the currently selected bits, independently of anchor and lead.
The count is computed from membership rather than maintained separately.

**Returns:** the number of selected items

#### nextSelected

```java
public int nextSelected(int fromIndex)
```

Finds the next selected position at or after the supplied starting index.
This supports ascending traversal without copying the selected set.

- **`fromIndex`** — the nonnegative inclusive search start

**Returns:** the next selected index, or -1 if none remains

**Throws `IndexOutOfBoundsException`:** if fromIndex is negative

#### onChange

```java
public AutoCloseable onChange(Runnable listener)
```

Registers a synchronous observer of selection and configuration changes.
The listener receives no payload and should read the current model state.
Registration does not invoke it; close the returned handle to unsubscribe.
Dispatch ordering and subscription changes follow `ChangeSignal`.

- **`listener`** — the nonnull change callback

**Returns:** an independently removable subscription

**Throws `NullPointerException`:** if listener is null

#### forEachSelected

```java
public void forEachSelected(IntConsumer action)
```

Visits selected positions in ascending order without taking a snapshot.
Callbacks should not mutate this model during traversal: each next position
is read from live membership, so changes can alter which later items run.
Callback failures propagate and stop traversal immediately.

- **`action`** — the nonnull consumer for each selected index

**Throws `NullPointerException`:** if action is null, even for an empty selection

#### itemCount

```java
public void itemCount(int count)
```

Updates the available item range while retaining surviving selected
positions. Shrinking clears out-of-range bits and unsets an invalid anchor.
An invalid lead moves to the highest surviving selected index, or -1 if
none remains. Growing does not restore previously removed selections.

A changed count emits one notification even if membership is unchanged;
the same count returns silently.

- **`count`** — the replacement nonnegative item count

**Throws `IllegalArgumentException`:** if count is negative

#### multiple

```java
public void multiple(boolean value)
```

Changes whether multiple items may be selected. Disabling the mode with
nonempty membership keeps the selected lead if possible, otherwise the
lowest selected index, and sets both anchor and lead to the retained item.
With empty membership, existing anchor and lead values are preserved.

- **`value`** — the desired multiple-selection mode; a changed mode emits one notification, while the existing mode returns silently

#### clear

```java
public void clear()
```

Removes all membership and resets anchor and lead to -1. A notification
is emitted if any of these values needed clearing, including an empty set
that still had a toggle target. An already fully cleared model is unchanged.

#### select

```java
public void select(int index, boolean extend, boolean toggle)
```

Applies the selection policy used by pointer and keyboard interactions.
Single-selection mode ignores both modifiers and replaces membership with
the target. In multiple mode, an unmodified request does the same; a toggle
without extension flips the target bit and sets both anchor and lead to it.

Extension selects the inclusive range between anchor and target, creating
an anchor at the target if none exists. Without toggle the range replaces
membership; with toggle it is added to existing membership rather than
flipping each range bit. Extension preserves the anchor and updates lead.

Replacement returns silently only when membership already contains just
the target and both anchor and lead match it. Other valid requests notify
once, including an extended range whose membership was already selected.

- **`index`** — the zero-based target within the current item range
- **`extend`** — whether to extend from anchor, normally the Shift modifier
- **`toggle`** — whether to toggle one item or add an extended range, normally Ctrl/Cmd

**Throws `IndexOutOfBoundsException`:** if index is outside the current item range

#### selectAll

```java
public void selectAll()
```

Selects the entire item range in multiple mode, setting anchor to zero and
lead to the final item. If every item is already selected, returns without
changing anchor or lead or notifying. An empty model also returns silently.

In single-selection mode this selects the current lead, or index zero
when lead is unset, using the normal replacement policy. A changed
selection emits one notification.

</details>

<a id="type-tablemodel"></a>

### TableModel

[Source](../../src/main/java/valthorne/ui/behavior/TableModel.java#L23)

UI-thread model projecting a shallow immutable row snapshot through filtering
and stable sorting. Visible membership is stored as primitive source indices;
reads allocate no new row objects. Predicates and comparators run only when
mutated or refreshed and must be pure and must not reenter this model.

A rebuild computes new membership before publishing state, so predicate or
comparator failure preserves the old projection. Notifications run synchronously
after publication; listener failure does not undo the committed state. Mutating
a row object requires refresh to reconsider its visibility or position.

- **`<T>`** — row object type

<details>
<summary>TableModel operation reference (10 declarations)</summary>

#### size

```java
public int size()
```

Reads filtered membership length, including all rows surviving the last rebuild.

**Returns:** current visible row count

#### sourceSize

```java
public int sourceSize()
```

Reads source snapshot membership independent of filtering and sorting.

**Returns:** source row count

#### comparator

```java
public Comparator<? super T> comparator()
```

Returns the retained comparator identity used by the current projection.

**Returns:** active ordering, or null for source order

#### get

```java
public T get(int index)
```

Resolves a visible index through the primitive projection to a borrowed row.
The row object is not copied; refresh after changes affecting filter or order.

- **`index`** — zero-based visible index

**Returns:** original row object

**Throws `IndexOutOfBoundsException`:** if index is outside visible membership

#### sourceIndex

```java
public int sourceIndex(int index)
```

Maps a visible index to its position in the current source snapshot.
The mapping may change after any successful rebuild.

- **`index`** — zero-based visible index

**Returns:** source snapshot index

**Throws `IndexOutOfBoundsException`:** if index is outside visible membership

#### onChange

```java
public AutoCloseable onChange(Runnable listener)
```

Registers a synchronous listener for every successful rebuild, including
refreshes that preserve identical membership. The listener observes committed
state; close the returned handle to remove this registration.

- **`listener`** — non-null callback

**Returns:** independent removal handle

**Throws `NullPointerException`:** if listener is null

#### rows

```java
public void rows(List<? extends T> rows)
```

Copies source membership and reapplies the retained filter and comparator.
Row objects remain borrowed. Publication occurs only after filtering/sorting
succeeds, followed by synchronous change notification.

- **`rows`** — non-null source list without null rows

**Throws `NullPointerException`:** if rows or any element is null

#### filter

```java
public void filter(Predicate<? super T> filter)
```

Replaces the acceptance predicate and rebuilds the projection in the existing
order. Use an always-true predicate to include every source row. Predicate
failure leaves current state intact.

- **`filter`** — non-null pure acceptance predicate

**Throws `NullPointerException`:** if filter is null

#### sort

```java
public void sort(Comparator<? super T> comparator)
```

Rebuilds ordering with the supplied comparator; null restores source order.
Equal comparisons retain original source order, not the previous projection's
order. The current filter remains applied.

- **`comparator`** — pure row ordering, or null for source order

#### refresh

```java
public void refresh()
```

Reevaluates the current source objects using the retained filter and ordering.
Use after mutable row fields change. Successful refresh always emits a change
notification even if the visible indices are unchanged.

</details>

<a id="type-datatable"></a>

### DataTable

[Source](../../src/main/java/valthorne/ui/nodes/DataTable.java#L31)

Sortable, filterable table with fixed headers and virtualized fixed-height rows.
Columns divide available width by relative weight and may create cells using
either renderer. Row data remains in a separate model while offscreen widgets
are discarded. Projection changes clear index selection and reset scrolling.
Use the model and column APIs to manage contents; inherited structural mutation
can break the internal header, viewport, and empty-message arrangement.

```java
DataTable<String> table = new DataTable<>(List.of(
        TableColumn.text("Name", 1f, value -> value)));
table.rows(List.of("Maple", "Cedar", "Oak"));
table.getLayout().width(400).height(300);
table.toggleSort(0);
```

- **`<T>`** — row data type

<details>
<summary>DataTable operation reference (12 declarations)</summary>

#### Constructor

```java
public DataTable(List<TableColumn<T>> columns)
```

Copies column membership and builds a 36-unit header plus selectable virtual
rows initially 36 units high with a two-unit gap. Registers synchronous model
change handling for rows, empty-state visibility, selection, and sort markers.

- **`columns`** — nonempty list of non-null immutable column definitions

**Throws `NullPointerException`:** if the list or any column is null

**Throws `IllegalArgumentException`:** if no columns are supplied

#### getModel

```java
public TableModel<T> getModel()
```

Exposes the live projection model. Its successful mutations rebuild visible
row membership and reset table selection and scrolling through a listener.

**Returns:** internally owned row model

#### getSelection

```java
public SelectionModel getSelection()
```

Exposes selection in current visible-row indices, not source-row indices.
Sorting, filtering, refreshing, or replacing model data clears this selection.

**Returns:** live virtual-list selection model

#### getLiveRowCount

```java
public int getLiveRowCount()
```

Reads the number of realized row widgets, which includes overscan and any
rows retained for input ownership. It is not the model's total row count.

**Returns:** currently live row widget count

#### getHeader

```java
public Button getHeader(int index)
```

Returns a live header button for styling or inspection. Non-sortable columns
have non-clickable, non-focusable headers; model updates rewrite sort labels.

- **`index`** — zero-based column index

**Returns:** owned header button

**Throws `IndexOutOfBoundsException`:** if index is outside the column list

#### rows

```java
public DataTable<T> rows(List<? extends T> data)
```

Replaces source membership with a shallow immutable snapshot, retaining row
objects and reapplying the current filter and comparator. Successful rebuild
resets selection and scroll position.

- **`data`** — non-null source rows with no null elements

**Returns:** this table

**Throws `NullPointerException`:** if data or any row is null

#### filter

```java
public DataTable<T> filter(Predicate<? super T> predicate)
```

Rebuilds the visible projection using a non-null predicate and current sort.
Use a predicate returning true to remove filtering. Predicate failure leaves
the old projection intact; successful rebuild resets selection and scrolling.

- **`predicate`** — pure row acceptance test that must not mutate the model

**Returns:** this table

**Throws `NullPointerException`:** if predicate is null

#### rowHeight

```java
public DataTable<T> rowHeight(float height)
```

Sets the virtual list's fixed row height and invalidates its geometry.
All cells share this height; the header remains 36 units high.

- **`height`** — finite positive row height in UI units

**Returns:** this table

**Throws `IllegalArgumentException`:** if height is nonpositive or non-finite

#### scrollToRow

```java
public void scrollToRow(int index)
```

Delegates scrolling to the virtual list so a visible-projection row is brought
into view. The index refers to filtered/sorted membership rather than source order.

- **`index`** — requested visible row index

**Throws `IndexOutOfBoundsException`:** if index is outside the visible row projection

#### getSortedColumn

```java
public int getSortedColumn()
```

Matches the model's comparator by identity against original and cached reverse
column comparators. External comparators and null produce no recognized column.

**Returns:** first matching column index, or minus one

#### isDescending

```java
public boolean isDescending()
```

Checks whether the recognized column uses its cached reversed comparator.
Arbitrary external orderings are not inferred from their comparison behavior.

**Returns:** true only for a recognized reversed column comparator

#### toggleSort

```java
public void toggleSort(int index)
```

Cycles a sortable column from ascending to descending to original source order.
Selecting a different column starts ascending. A column without a comparator
is unchanged; successful model sorting resets selection and scroll position.

- **`index`** — zero-based column index

**Throws `IndexOutOfBoundsException`:** if index is outside the column list

</details>

<a id="type-datatable-cell"></a>

### DataTable.Cell — internal support type

[Source](../../src/main/java/valthorne/ui/nodes/DataTable.java#L216)

Internal cell container limiting both hit testing and drawing to its bounds.
Texture-batch clipping includes the active translation and is unwound even
when child drawing fails, preserving the enclosing table's clipping stack.

<details>
<summary>DataTable.Cell operation reference (2 declarations)</summary>

#### findNodeAt

```java
@Override public UINode findNodeAt(float x, float y, int bit)
```

Rejects hits outside this cell before searching its descendants.

- **`x`** — query X in the coordinate space expected by contains
- **`y`** — query Y in the coordinate space expected by contains
- **`bit`** — hit-test capability mask forwarded to descendants

**Returns:** matching descendant or this cell, or null outside/no match

#### draw

```java
@Override public void draw(valthorne.graphics.texture.TextureBatch batch)
```

Pushes a scissor at translated render bounds, draws the panel subtree, and
restores the enclosing scissor in a finally block.

- **`batch`** — active texture batch carrying current translation and clipping

</details>

<a id="type-tablecolumn"></a>

### TableColumn

[Source](../../src/main/java/valthorne/ui/nodes/TableColumn.java#L23)

Immutable column definition for a virtual DataTable. Width is proportional
to the sum of column weights, and each visible cell is built from a fresh
unattached node returned by the factory. Factories may use either UI renderer.
Comparators sort row objects; a null comparator disables header sorting.

- **`<T>`** — row data type
- **`title`** — non-null header label
- **`weight`** — positive finite relative width
- **`cell`** — factory returning a fresh unattached node for each visible row
- **`comparator`** — optional row comparator; null makes the column unsortable

<details>
<summary>TableColumn operation reference (2 declarations)</summary>

#### Constructor

```java
public TableColumn
```

Validates required metadata while retaining the supplied callbacks. Factory
results are checked later when a table realizes visible cells.

- **`title`** — non-null header label
- **`weight`** — positive finite relative width
- **`cell`** — non-null cell factory
- **`comparator`** — optional row ordering

**Throws `NullPointerException`:** if title or cell is null

**Throws `IllegalArgumentException`:** if weight is nonpositive or non-finite

#### text

```java
public static <T> TableColumn<T> text(String title, float weight, Function<? super T, String> text)
```

Creates fresh non-clickable NanoLabel cells from an extracted string and a
null-first natural string comparator. Null extracted values display as empty
text but sort before non-null values. The extractor may be invoked repeatedly
by sorting and rendering, so it should provide stable results.

- **`<T>`** — row data type
- **`title`** — non-null header label
- **`weight`** — positive finite relative width
- **`text`** — non-null row-to-text extractor

**Returns:** immutable sortable text column

**Throws `NullPointerException`:** if title or text is null

**Throws `IllegalArgumentException`:** if weight is nonpositive or non-finite

</details>

<a id="type-virtuallist"></a>

### VirtualList

[Source](../../src/main/java/valthorne/ui/nodes/VirtualList.java#L38)

Fixed-height virtualized list/grid or measured variable-height single-column list.
Factories can return either renderer's nodes.
Only visible items, overscan and any focused/captured item remain attached.
Store durable item state in your data model, not in a transient row widget.
Default geometry uses one column, 40-unit rows, four-unit gaps, and two overscan
rows at each end. Factories run synchronously during layout or updates and must
return fresh unattached nodes. Removed row widgets follow the node lifecycle.

```java
VirtualList list = new VirtualList(10000, index -> new Button("Item " + index));
list.rowHeight(36).overscan(3).selectable(false);
list.getLayout().width(400).height(500);
```

Measured heights are supplied explicitly, not automatically read from row
widgets. Internal row storage owns the scroll content: do not replace it through
inherited content setters. Use the list on its owning UI thread.

<details>
<summary>VirtualList operation reference (22 declarations)</summary>

#### Constructor

```java
public VirtualList(int itemCount, IntFunction<? extends UINode> factory)
```

Creates a vertical scroll list with internal row content and no horizontal bar.
Item widgets remain deferred until synchronization with an attached root.

- **`itemCount`** — nonnegative model item count
- **`factory`** — supplier of a fresh unattached node for an item index

**Throws `NullPointerException`:** if factory is null

**Throws `IllegalArgumentException`:** if itemCount is negative

#### getItemCount

```java
public int getItemCount()
```

Returns the model size independently of how many row widgets exist.

**Returns:** total item count

#### getLiveItemCount

```java
public int getLiveItemCount()
```

Counts attached widgets, including overscan and offscreen rows retained for
focus or pointer capture.

**Returns:** current materialized item count

#### selectable

```java
public VirtualList selectable(boolean multiple)
```

Enables model selection and makes the list focusable on first use. Synchronizes
selected state on live rows through a change subscription. Later calls update
single/multiple selection mode without replacing the model.

- **`multiple`** — whether multiple indices may be selected

**Returns:** this list

#### getSelection

```java
public SelectionModel getSelection()
```

Returns the optional live selection model. The list controls its item count and
updates live row flags when selection changes.

**Returns:** selection model, or null before selectable is called

#### onInputPreview

```java
    public void onInputPreview(UIInputEvent context)
```

After normal scroll-panel preview, selects the live row containing a left-press
target when selection is enabled. Shift extends and Control/Super toggles according
to the selection model. Does not consume the press, allowing row controls to handle it.

- **`context`** — routed preview with the original target

#### onKeyPress

```java
    public void onKeyPress(KeyPressEvent event)
```

Handles selection navigation on an enabled nonempty list. Supports Home/End,
Up/Down by column stride, horizontal arrows for grids, and Control/Super+A.
Recognized navigation scrolls to the item and focuses the list to avoid pinning
old focused rows, then consumes the key. Other keys are ignored.

- **`event`** — routed unhandled key press

#### getItemNode

```java
public UINode getItemNode(int index)
```

Looks up a materialized widget without creating it or validating the model index.
The node belongs to the list and may be removed after scrolling.

- **`index`** — model item index

**Returns:** attached node, or null if not materialized

#### getColumns

```java
public int getColumns()
```

Returns the configured grid column count; measured-height mode requires one.

**Returns:** positive number of columns

#### columns

```java
public VirtualList columns(int columns)
```

Changes fixed-grid column count and invalidates item geometry. Equal values do
nothing. Variable-height mode rejects every value other than one.

- **`columns`** — positive grid width in items

**Returns:** this list

**Throws `IllegalArgumentException`:** if columns is below one

**Throws `IllegalStateException`:** if measured heights are active and columns is not one

#### rowHeight

```java
public VirtualList rowHeight(float height)
```

Sets fixed row height and disables measured-height storage. Invalidates geometry
when the fixed height or mode changes; existing widgets can be retained.

- **`height`** — finite positive height in layout units

**Returns:** this list

**Throws `IllegalArgumentException`:** if height or its gap-inclusive stride is invalid

#### gap

```java
public VirtualList gap(float gap)
```

Changes spacing and adjusts measured strides by the gap difference when needed.
Validates all adjusted strides before mutation and invalidates item geometry.

- **`gap`** — finite nonnegative spacing in layout units

**Returns:** this list

**Throws `IllegalArgumentException`:** if gap is invalid or a stride overflows

#### itemCount

```java
public VirtualList itemCount(int count)
```

Updates model size, resizes any measured-height index using the estimate for new
items, synchronizes selection count, and refreshes all widgets. Even an unchanged
count discards current widgets through the refresh path.

- **`count`** — nonnegative item count

**Returns:** this list

**Throws `IllegalArgumentException`:** if count is negative

#### overscan

```java
public VirtualList overscan(int rows)
```

Sets the extra row count materialized beyond each visible end. A changed value
invalidates the cached range; zero limits materialization to visible and pinned rows.

- **`rows`** — nonnegative overscan row count

**Returns:** this list

**Throws `IllegalArgumentException`:** if rows is negative

#### refreshItems

```java
public void refreshItems()
```

Cancels root input when focus or capture belongs to row content, removes every
live widget, and invalidates the range and extent. Durable data and selection stay
in their models; widgets will be recreated when synchronized.

#### variableHeights

```java
public VirtualList variableHeights()
```

Enables measured single-column rows using current height plus gap as the estimate.
Allocates per-item height indexing on first use; repeated calls preserve measurements.

**Returns:** this list

**Throws `IllegalStateException`:** if the configured column count is not one

#### hasVariableHeights

```java
public boolean hasVariableHeights()
```

Reports whether per-item measured strides are active.

**Returns:** true for measured single-column mode

#### itemHeight

```java
public VirtualList itemHeight(int index, float height)
```

Enables measured mode if needed and replaces one row's gap-inclusive stride.
Preserves the top visible row and its relative scroll offset through a deferred
scroll adjustment applied after layout. Equal measured values are a no-op.

- **`index`** — item whose height changed
- **`height`** — finite positive content height excluding gap

**Returns:** this list

**Throws `IndexOutOfBoundsException`:** if index is invalid

**Throws `IllegalArgumentException`:** if height or stride is invalid

**Throws `IllegalStateException`:** if multiple columns prevent measured mode

#### getItemHeight

```java
public float getItemHeight(int index)
```

Returns fixed or measured content height, excluding the inter-row gap.
Does not instantiate or measure a widget.

- **`index`** — valid model index

**Returns:** content height in layout units

**Throws `IndexOutOfBoundsException`:** if index is invalid

#### scrollToIndex

```java
public void scrollToIndex(int index)
```

Requests the item's row start as vertical scroll offset, laying out a dirty root
first when attached, then synchronizes widgets. Scroll-panel bounds can clamp the
offset near the end, so the requested item need not land exactly at the top.

- **`index`** — item to reveal

**Throws `IndexOutOfBoundsException`:** if index is invalid

#### update

```java
    public void update(float delta)
```

Synchronizes the materialized range before the normal scroll-panel update.
Factories and child lifecycle callbacks can therefore run during this call.

- **`delta`** — elapsed seconds

#### afterLayout

```java
    protected void afterLayout()
```

Runs normal scroll-panel layout, applies any pending anchor-preserving scroll
offset, clears that pending value, and synchronizes row widgets.

</details>

## Related guides

- [Standard UI controls](ui-controls.md)
- [Shared UI behavior and editing models](ui-behavior.md)
- [UI layout and alignment](ui-layout.md)
- [Existing ui advanced guide](../ui-advanced.md)
