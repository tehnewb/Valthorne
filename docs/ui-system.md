# UI system guide

Runnable demos and assets are maintained in the public
[examples project](https://github.com/tehnewb/Valthorne-examples). Run demo launch tasks there; the engine's local
`src/examples/` files remain ignored and excluded from library artifacts.
See the [example catalog](examples.md). Historical measurements retain their
original commands and source revisions.

Valthorne has one UI tree with texture and NanoVG rendering. Existing widget
classes remain available; nodes can be nested across families without converting
assets. See [mixed rendering](ui-rendering.md) for the drawing contract.

## Run and verify

```powershell
.\gradlew.bat runUIShowcase
.\gradlew.bat build
.\gradlew.bat benchmarkUITable
.\gradlew.bat runUIShowcase --args=--smoke
.\gradlew.bat build
```

The showcase includes core controls, mixed grids/images, nested scrolling,
cross-renderer modals, text editing, a dropdown, light/dark themes, a 10,000-item
virtual grid, searchable/sortable table, collapsible sections, and an opt-in inspector.
The smoke run creates an invisible window,
captures seven pages into `build/ui-showcase`, checks OpenGL errors, and exits.
`verifyUI` uses only versioned UI tests, independently of local ignored demo
sources. Native tests and the smoke run require a working OpenGL/GLFW context.
The UI showcase compiles in an isolated source set, so unrelated examples cannot
break its launch. See [performance methodology and results](ui-performance.md)
for the forked JMH suite and optional allocation-budget gate.

## Shared behavior, distinct skins

- `ActivationBehavior` supplies the same primary-release and Enter/Space policy
  to both buttons, both checkboxes, and hyperlinks.
- `RangeModel` supplies finite range validation, endpoint clamping, snapping,
  pointer positioning, wheel increments, and keyboard stepping to both sliders.
  Programmatic setters remain silent; user changes invoke the widget action.
- `ScrollBehavior` supplies fractional wheel handling and boundary consumption
  to both scroll panels. Backend-specific scrollbar geometry remains in its skin.
- `TextEditModel` and `TextEditing` own selection, editing, shortcuts, clipboard
  policy, validation, and bounded undo/redo for both text fields. Widgets retain
  only rendering snapshots and font-specific measurement.

These are compositional building blocks, not a replacement inheritance hierarchy.
Custom nodes can use them without implementing a second renderer.

## Input routing

`UIRoot` routes input through a snapshot of the target's ancestor path:

1. `onInputPreview(UIInputEvent)` runs from root/focus scope to target.
2. The target's existing handler runs, unless preview consumed the event.
3. `onInputBubble(UIInputEvent)` runs back toward the root/focus scope.

Calling `consume()` stops propagation, including subsequent global event
subscribers. Keyboard and scroll events also invoke ancestor legacy handlers
until consumed. Pointer handlers are target-only; parents observe them using
the preview/bubble hooks, avoiding accidental parent-button activation.
Preview interception cancels capture without a synthetic click.

`event.localPosition()` is relative to the receiving node's top-left corner.
For keyboard/text events there is no pointer position (coordinates are NaN).
Do not retain the dispatch context or its underlying reusable event.

Capture is retained outside widget bounds, belongs to one button code, and
ends on matching release, detachment, invalidation, or window deactivation.
`onPointerCancel()` ends a gesture without activation. Override it when a
custom control owns additional dragging state. `root.cancelInput()` explicitly
clears capture, hover, focus, and tooltips.

Tab and Shift+Tab traverse visible, enabled focusable nodes. Both modal classes
use `showModal`/`hideModal`, trap input/focus inside the active scope, handle
Escape from child controls, and restore the invoker when closed. Nested scopes
are supported. Dropdown options use the same scope mechanism and virtualized
rows; arrows, Home/End, Enter, and Escape are supported.

Native movement events now carry correct destination coordinates and drag
button **codes**, not the held-button mask. Multiple held buttons produce one
drag event per button. `MouseScrollEvent.preciseXOffset/preciseYOffset` preserve
trackpad fractions; integer accessors remain for compatibility.

## Text input and validation

Physical key commands and committed text are separate. GLFW character input
publishes `TextInputEvent`; UI text fields no longer derive characters from
physical key codes. Code that synthesizes typing should publish text explicitly:

```java
JGL.publish(new TextInputEvent("café"));

NanoTextField field = new NanoTextField("Display name");
field.getEditor().maxLength(80); // user edits, measured in Unicode code points
field.getEditor().validator(value -> !value.isBlank());
AutoCloseable subscription = field.getEditor().onChange(() -> {
    boolean valid = field.getEditor().isValid();
    // Update an error label or validation style here.
});
```

Adding a change listener does not replace the widget's internal synchronization.
Close the returned subscription when no longer needed. Validation reports
validity and gates Enter submission, but does not prohibit temporarily invalid
text while editing. Programmatic `text(...)` assignments reset history; the
maximum length constrains user edits rather than silently truncating model data.

Cursor/selection offsets remain UTF-16 offsets for compatibility, but are snapped
to extended grapheme boundaries. Backspace/delete do not split emoji or combining
sequences. Ctrl/Cmd+A/C/X/V/Z, redo (Ctrl/Cmd+Y or Shift+Z), word movement,
selection extension, and word deletion share one implementation. Undo retains
at most 100 edit snapshots. Masked fields prohibit copying/cutting their contents;
this is UI policy, not encrypted storage.

## Theme ownership

```java
ProfessionalTheme skin = new ProfessionalTheme(false, 1f); // dark, standard density
root.setTheme(skin.create());
// ... use the root ...
root.dispose();
skin.close(); // free owned font atlas and nine-patch textures on the graphics thread
```

`UITokens` defines semantic colors, spacing, radius, typography, and minimum
control height. The default theme maps these to the existing skin keys and
provides texture nine-patches and Nano colors with normal, hover, focus, pressed,
and disabled states. Density is configurable from 0.5 to 3. Existing explicit
widget dimensions and local style overrides remain authoritative.

Use a fresh light/dark theme or modify `ThemeData` tokens/rules at runtime.
Attached roots invalidate their affected styles when theme rules or resources
change. Same-name style keys with incompatible Java types fail immediately.
Local color/action changes refresh style without requesting a layout pass;
other key types conservatively invalidate layout.

The default font is versioned with the library. No files from ignored `assets/`
are needed by the showcase or UI verification. The legacy system-property font
override is retained for Nano rendering; the packaged professional texture skin
uses its own bundled atlas.

## Coordinates and custom drawing

`node.screenToLocal(x, y)` converts bottom-left screen coordinates to top-left
node coordinates, accounting for the viewport and ancestor scrolling.
Existing `screenToWorld`, `screenToContent`, and `screenToLayout` remain available.

Custom containers can use balanced shared scopes:

```java
UIRenderContext context = getRoot().getRenderContext();
try (var clip = context.clip(getAbsoluteX(), getAbsoluteY(), getWidth(), getHeight());
     var offset = context.translate(0, -scrollOffset)) {
    child.render(context.getBatch());
}
```

The clip uses top-left layout coordinates; the translation uses top-left deltas.
Both apply to both backends. Nested scopes restore in reverse order, including
when drawing throws. Raw backend transformations are only suitable for a node's
own drawing, not shared mixed-renderer child space.

## Layout and performance

Layout setters invalidate their owner automatically and ignore identical values,
including positioning shortcuts and reset methods. Default zero-point and full-percent
values are shared immutable objects; numeric setters compare before allocating.
Root updates coalesce layout work rather than recalculating in every child update.
Automatic passes resubmit only dirty/style-invalidated nodes' inputs to Yoga.
Yoga still recalculates dependent constraints and refreshes descendant bounds.
Calling `root.layout()` explicitly forces every node to reapply its inputs; this
supports custom nodes whose layout reads external state. Custom controls must
mark layout dirty when such state changes if they rely on automatic updates.
`afterLayout` can create virtual rows; a bounded stabilization pass
finishes their geometry before drawing. A non-converging custom layout fails
with a descriptive exception after eight passes.

`Layout.fill()` means flexible growth/shrink, **not** 100% width and height.
Use explicit percentages for a full-window absolute container, and a zero basis
or zero width/height plus growth for a flexible viewport that must not expand to
the intrinsic size of its scrolling content.

Adjacent same-renderer siblings share batches without changing painter order.
The renderer restores the parent's backend before foreground drawing.
`root.getFrameStats()` reports layout count/time, render time, nodes drawn,
backend switches, Nano flushes, and actual texture draw calls. Nano flush count
is not a claim about NanoVG's internal GPU draw-call count.

Render coordinates reuse the existing cached bounds instead of walking ancestors
and reading native Yoga positions repeatedly. No coordinate fields were added.
Bounds represent the last completed layout. Internal draw/style walks use indexed
child access. `getChildren()` returns a cached read-only **live view**, including
after internal array growth. Use `List.copyOf(...)` for a structural snapshot.
Do not mutate the tree while iterating/drawing it.

```java
VirtualList grid = new VirtualList(10_000, index ->
    index % 2 == 0 ? new Button("Texture " + index) : new NanoButton("Nano " + index));
grid.columns(4).rowHeight(44).gap(8).overscan(2);
grid.getLayout().width(800).height(500);
root.add(grid);
grid.scrollToIndex(9999);
```

`VirtualList` handles lists and grids with fixed row heights, or measured variable
heights in single-column mode. It keeps only
visible rows plus overscan attached, and pins focused/captured rows until their
interaction ends. Factories must return fresh unattached nodes. Keep persistent
item state in the data model; offscreen row nodes are destroyed and recreated.
`getItemNode(index)` returns a currently attached row or null. `refreshItems()`
rebuilds rows after data changes; `itemCount(...)` updates the extent and clamps
scrolling. Always constrain the viewport's size.

See [advanced controls](ui-advanced.md) for variable-height measurements,
multi-selection, lazy tabs, split panes and notification contracts.

## Inspector

Enable `root.getInspector().setEnabled(true)` and optionally `setOutlines(true)`.
`entries()` provides actual translated bounds, effective clips, resolved style
values, focused state, and capture state for the last frame. The showcase also
shows the hovered/focused control's details. Inspection is opt-in and allocates
snapshots only when enabled; counters remain available independently.

## Boundaries

- Variable-height lists require explicitly supplied measurements and a single column;
  automatic wrapping-based measurement, variable-height grids and reusable row pools
  are not provided.
- Keyboard navigation and readable focus states are implemented; native
  screen-reader bridges and gamepad navigation are not provided.
- Text editing is Unicode-safe, but glyph coverage still depends on the supplied
  font. The packaged texture atlas covers basic Latin/Latin-1. Full shaping, bidi
  layout, font fallback, and an IME composition/preedit UI are not implemented.
  Committed text is supported through the platform character callback.
- Tests exercise the current Windows OpenGL implementation, native input
  callbacks, and orthographic viewport scaling. Other operating systems, unusual
  camera projections, and custom OpenGL-state changes require additional testing.
- This upgrade is additive at the widget API level, but new routed-event
  consumption and separate committed-text events intentionally clarify input
  semantics. Review custom handlers that previously relied on unconsumed input
  reaching game controls behind the UI.

## Bundled font provenance

Atkinson Hyperlegible Regular, copyright 2020 Braille Institute of America, Inc.,
is distributed under the SIL Open Font License 1.1. The complete license is in
`src/main/resources/ui/OFL-AtkinsonHyperlegible.txt` and is packaged with the jar.
The unmodified font comes from [Google Fonts](https://github.com/google/fonts/tree/1b22086d13bdbbf5ec63d01a39a49246e4ac355f/ofl/atkinsonhyperlegible).

SHA-256: `7fb917c89019896d0b52ee84b7cbb3304c18cb90b19a62f5e32712bd23e97669`.
