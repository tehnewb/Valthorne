# Performance overlays and UI inspection

Author: Albert Beaupre

[System manual](README.md)

## Purpose

Diagnostics expose performance and UI state without making those measurements part of application logic. PerformanceOverlay displays timing/render information; UIFrameStats captures work counters; UIInspector records node information for inspection.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Frame measurements | Counters show the amount of work performed during UI/render traversal. |
| Overlays | Visible diagnostics make regressions easier to observe while interacting. |
| Inspection snapshots | Recorded node bounds, clipping, focus, capture, and style help explain UI behavior. |
| Live references | Some inspection entries retain node identity while capturing selected display values. |

## Getting started

1. Enable the diagnostic component in the relevant render/UI phase.
2. Reproduce a representative interaction and examine both timings and work counts.
3. Use inspector bounds and clip information to investigate invisible or misrouted controls.
4. Remove or disable expensive diagnostic work when it is no longer needed.

## Ownership and lifecycle

Inspection does not own the lifecycle of referenced nodes. A snapshot containing a node reference does not keep that node valid after disposal. Measurement overhead should be considered when comparing runs.

## Important behavior

- High draw count and high layout count indicate different problems.
- Compare equivalent scenes, viewport sizes, and swap-interval settings.
- A copied style map can still contain shared mutable values.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`PerformanceOverlay`](#type-performanceoverlay)
- [`UIFrameStats`](#type-uiframestats)
- [`UIInspector`](#type-uiinspector)
- [`UIInspector.Bounds`](#type-uiinspector-bounds)
- [`UIInspector.Entry`](#type-uiinspector-entry)

<a id="type-performanceoverlay"></a>

### PerformanceOverlay

[Source](../../src/main/java/valthorne/graphics/debug/PerformanceOverlay.java#L28)

Small atlas-based FPS/frame-time display. Create/dispose on the GL thread.
Printable ASCII is rasterized once with AWT into an owned texture atlas. Frame
timing measures intervals between calls, independently of simulation time or GPU
queries, and updates the display after at least a quarter-second sample window.
Drawing reuses atlas regions; sampling still allocates a formatted string when
the displayed statistics change.

<details>
<summary>PerformanceOverlay operation reference (5 declarations)</summary>

#### Constructor

```java
public PerformanceOverlay()
```

Rasterizes 95 printable ASCII glyphs with a bold 22-point monospaced AWT font,
encodes the atlas as PNG, and uploads one texture. Glyph cells are 18 by 28
pixels; no application font resource is borrowed.

**Throws `UncheckedIOException`:** if atlas PNG encoding reports an I/O error

#### frame

```java
public void frame()
```

Call once per presented frame; measures wall time, independently of simulation delta.
The first call initializes timing without counting a frame interval. Once a
sample spans at least 0.25 seconds, computes average FPS and milliseconds per
interval and starts a new sample. Does not query actual display presentation.

#### draw

```java
public void draw(TextureBatch batch, float x, float y)
```

Draw inside an active 2D TextureBatch. No font rasterization or texture upload per frame.
Uses the most recently sampled text, so calling draw does not advance timing.

- **`batch`** — active batch with the desired projection
- **`x`** — left coordinate in batch space
- **`y`** — glyph-quad origin Y in batch space

#### drawText

```java
public void drawText(TextureBatch batch, String value, float x, float y)
```

Draws printable ASCII using 18-by-28 quads with a 14-unit advance. Unsupported
characters leave an advance-sized gap; newlines do not create additional rows.
Coordinates follow the active batch projection and current rendering state.

- **`batch`** — active texture batch
- **`value`** — text to draw
- **`x`** — left origin in batch coordinates
- **`y`** — quad origin Y in batch coordinates

**Throws `NullPointerException`:** if value is null

#### close

```java
    public void close()
```

Releases the owned atlas texture through its disposal path. Glyph regions
become unusable afterward; close on the owning graphics thread after drawing ends.

</details>

<a id="type-uiframestats"></a>

### UIFrameStats

[Source](../../src/main/java/valthorne/ui/UIFrameStats.java#L33)

Immutable snapshot of CPU timing and drawing counters published by a UI root.
Layout counters accumulate until the root records a draw's statistics, then
reset. Rendering counters come from that draw's mixed-backend context, while
texture draw calls are the batch's cumulative-counter difference for the draw.

Durations use nanoseconds from CPU wall-clock measurements, not GPU timer
queries. NanoVG flushes and texture draw calls are separate measures and must
not be interpreted as equivalent units of work. Node counts track visible-node
dispatch attempts, not unique nodes or GPU primitives.

The draw timer starts after style refresh and any required layout. It
includes inspector setup, viewport binding, tree and inspection drawing, and
batch end, but finishes before viewport unbinding.

The root records statistics during draw cleanup when a render context was
created and batch end succeeds; a snapshot can therefore describe a partially
drawn tree if a node callback threw. Construction itself performs no validation,
and the root initially exposes an all-zero snapshot.

- **`layoutPasses`** — accumulated Yoga calculation passes since the previous recorded draw
- **`layoutNanos`** — accumulated CPU time spent in root layout calls, in nanoseconds
- **`renderNanos`** — elapsed CPU time measured by the root's draw timer, in nanoseconds
- **`nodesDrawn`** — visible-node dispatch attempts made through the render context
- **`backendSwitches`** — transitions between texture painting and NanoVG painting
- **`nanoFlushes`** — NanoVG end-frame submissions made when returning to texture painting
- **`textureDrawCalls`** — texture-batch draw calls recorded during this draw

<a id="type-uiinspector"></a>

### UIInspector

[Source](../../src/main/java/valthorne/ui/UIInspector.java#L44)

Collects opt-in inspection data from nodes dispatched during a UI root draw.
Entries describe translated layout bounds, the effective batch clip, focus
and pointer-capture ownership, and resolved style values at recording time.
Recording follows actual dispatch order rather than traversing the tree again.

##### Usage

```java
UIInspector inspector = root.getInspector();
inspector.setEnabled(true);
inspector.setOutlines(true);
root.draw();
for (UIInspector.Entry entry : inspector.entries()) {
    System.out.println(entry.type() + " " + entry.bounds());
}
```

##### Coordinates and Lifetime

Rectangles use top-left render-space layout units, with accumulated batch
translation reflected in node bounds. Camera projection and zoom are not
baked into these values. A clip describes the current scissor state rather
than the intersection of that scissor with an individual node's bounds.

The root clears entries at the start of each draw. Visible nodes are recorded
before their callbacks execute, so repeated draws create repeated entries and
a callback failure can leave a partial frame's data. Disabled inspection skips
recording entirely; disabling also clears retained entries immediately.

List snapshots preserve membership, but retain live node references and
shallow style values. Avoid keeping them longer than needed, especially after
nodes are removed or disposed. Use this unsynchronized inspector on the UI's
drawing thread. Outlines require both inspection to be enabled and an available
NanoVG context in the root's render context.

<details>
<summary>UIInspector operation reference (5 declarations)</summary>

#### isEnabled

```java
public boolean isEnabled()
```

Reports whether node dispatches are currently eligible for recording.
This flag starts false and does not imply that any entries have been collected.

**Returns:** whether inspection recording is enabled

#### setEnabled

```java
public void setEnabled(boolean enabled)
```

Enables or disables collection. Disabling immediately clears current entries;
enabling leaves the collection as it is and does not retroactively inspect
previously drawn nodes. The stored outline preference is preserved.

- **`enabled`** — whether subsequent dispatches should be recorded

#### isOutlines

```java
public boolean isOutlines()
```

Returns the effective outline setting, requiring both recording and the
outline preference. This does not test whether a native drawing context exists.

**Returns:** true when inspection and outline drawing are both requested

#### setOutlines

```java
public void setOutlines(boolean outlines)
```

Changes the retained outline preference without enabling recording or
modifying existing entries. Setting true while disabled takes effect when
inspection is subsequently enabled and the root draws its inspection layer.

- **`outlines`** — whether recorded bounds should be outlined during root drawing

#### entries

```java
public List<Entry> entries()
```

Returns an unmodifiable snapshot of the current entry sequence. Later
recording or clearing does not change that sequence. Entry objects, node
references and nested style values are not deep-copied.

**Returns:** a list snapshot in node-dispatch order, possibly empty

</details>

<a id="type-uiinspector-bounds"></a>

### UIInspector.Bounds

[Source](../../src/main/java/valthorne/ui/UIInspector.java#L132)

Immutable rectangle captured in top-left render-space layout units.
Values describe a node box or a scissor rectangle before camera projection.
The record stores supplied values without normalization or validation.

- **`x`** — the left edge in layout units
- **`y`** — the top edge in layout units
- **`width`** — the captured horizontal extent
- **`height`** — the captured vertical extent

<a id="type-uiinspector-entry"></a>

### UIInspector.Entry

[Source](../../src/main/java/valthorne/ui/UIInspector.java#L150)

Captures one node-dispatch observation. Rectangle and boolean values are
snapshots; the node remains a live reference. Internally recorded style maps
are unmodifiable shallow snapshots whose contained values can still be mutable.
Constructing an entry directly performs no copying or null validation.

- **`node`** — the live node observed before its draw callback
- **`type`** — the node class's simple name, which can be empty for anonymous classes
- **`bounds`** — the translated node rectangle in top-left layout coordinates
- **`clip`** — the effective scissor rectangle, or null when clipping is disabled
- **`focused`** — whether this node owned keyboard focus when recorded
- **`captured`** — whether this node owned pointer capture when recorded
- **`style`** — the resolved style values indexed by registered key name

## Related guides

- [UI roots, nodes, and input routing](ui-core.md)
- [Textures, sprites, atlases, and batching](textures.md)
- [Application lifecycle and window management](runtime.md)
- [Existing ui performance guide](../ui-performance.md)
- [Existing performance program guide](../performance-program.md)
