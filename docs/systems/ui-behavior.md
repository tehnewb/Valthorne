# Shared UI behavior and editing models

Author: Albert Beaupre

[System manual](README.md)

## Purpose

Behavior models keep interaction state reusable across standard and NanoVG controls. They cover activation, ranges, scrolling, text editing, and change notification. Use these models when you need custom painting without rewriting the underlying input policy.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Activation | Shared keyboard and pointer acceptance rules invoke an action and consume accepted input. |
| Range state | RangeModel converts values and pointer positions within configured bounds. |
| Text editing | TextEditModel tracks text, caret, selection, validation, and undo/redo state. |
| Scroll policy | ScrollBehavior computes clamped offsets and consumes only effective wheel movement. |
| Change signals | Listeners observe state changes so a view can refresh its caches. |

## Getting started

1. Create the relevant model or obtain the model exposed by an existing control.
2. Route accepted events through its behavior API.
3. Subscribe the view to changes and synchronize display state without creating a notification loop.
4. Render from model state and keep input consumption aligned with accepted actions.

## Ownership and lifecycle

Models contain mutable interaction state but do not own a root, font, or GPU texture. Callbacks are synchronous. Text selection offsets use UTF-16 units, so they are not arbitrary user-perceived character indices.

## Important behavior

- At a scroll boundary, an unchanged wheel event can continue to an ancestor.
- Validation can reject text even when the focused control consumes the routed input event.
- Use model selection and caret state rather than a stale visual copy for editing decisions.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`ActivationBehavior`](#type-activationbehavior)
- [`ChangeSignal`](#type-changesignal)
- [`RangeModel`](#type-rangemodel)
- [`ScrollBehavior`](#type-scrollbehavior)
- [`TextEditing`](#type-textediting)
- [`TextEditModel`](#type-texteditmodel)

<a id="type-activationbehavior"></a>

### ActivationBehavior

[Source](../../src/main/java/valthorne/ui/behavior/ActivationBehavior.java#L24)

Applies the activation policy shared by texture and NanoVG buttons and
checkboxes. Keyboard activation accepts Enter or Space on an enabled node;
release activation delegates to the node's left-button hit-test policy.
Accepted events are consumed before the action runs.

This utility supplies no focus routing, press tracking, repeat suppression,
or listener registration. Call it from a node's routed input callbacks so the
root remains responsible for choosing the receiver. An already-consumed event
is not filtered here; normal routing must avoid delivering it again.

Actions execute synchronously on the calling thread. Their exceptions
propagate and do not undo event consumption or earlier action side effects.

<details>
<summary>ActivationBehavior operation reference (2 declarations)</summary>

#### key

```java
public static void key(UINode node, KeyPressEvent event, Runnable action)
```

Consumes an Enter or Space press on an enabled node and immediately invokes
the action. Other keys and disabled nodes leave the event untouched.
This helper does not independently verify focus or suppress key repeats.

- **`node`** — the routed receiver whose enabled state gates activation
- **`event`** — the key press to inspect and consume if accepted
- **`action`** — the synchronous action to run for an accepted press

**Throws `NullPointerException`:** if node is null, event is null while the node
is enabled, or action is null when activation is accepted

#### release

```java
public static void release(UINode node, MouseReleaseEvent event, Runnable action)
```

Runs an action when `UINode#isActivationRelease(MouseReleaseEvent)`
accepts the release. That policy requires the left button, an enabled node
attached to a root, and this node as the clickable hit-test result at the
release position. It does not itself establish a matching earlier press.

- **`node`** — the routed receiver whose activation-release policy is checked
- **`event`** — the release consumed before an accepted action runs
- **`action`** — the synchronous action for an accepted release

**Throws `NullPointerException`:** if node or event is null, or action is null
when the release is accepted

</details>

<a id="type-changesignal"></a>

### ChangeSignal

[Source](../../src/main/java/valthorne/ui/behavior/ChangeSignal.java#L31)

Synchronous change notification shared by UI models such as text editing and
selection. Each registration receives an independent subscription handle and
callbacks run in registration order on the thread that calls `fire()`.
The signal carries no event payload; listeners read the owning model's state.

Dispatch captures the current registration array without copying it.
Subscribing or closing a subscription replaces that array, leaving an ongoing
dispatch unchanged. A listener removed by an earlier callback therefore still
runs in the current dispatch. A newly added listener first participates in a
subsequent call to fire, including a recursive call made by a callback.

Dispatch itself allocates no objects, excluding work performed by callbacks.
Registration and removal copy arrays and are linear in the listener count.
Callbacks are held strongly while registered; retain and close their handles
when the listening UI object no longer needs notifications. Registering the
same callback twice creates two independently removable registrations.

All operations are intended for the UI thread and are not synchronized.
Callback failures propagate immediately and skip remaining listeners in that
dispatch. Notifications are neither queued nor coalesced, and recursive
dispatch is not prevented.

<details>
<summary>ChangeSignal operation reference (2 declarations)</summary>

#### subscribe

```java
public AutoCloseable subscribe(Runnable callback)
```

Appends a listener without invoking it and returns a handle for removing
only this registration. Duplicate callback instances are allowed. Closing
the handle repeatedly is harmless and does not affect other registrations.
An ongoing dispatch retains its original snapshot and does not see this
addition; any later dispatch captures the updated array.

- **`callback`** — the nonnull action to invoke synchronously on each change

**Returns:** an independently closeable subscription handle

**Throws `NullPointerException`:** if callback is null

#### fire

```java
public void fire()
```

Invokes the listeners present at entry, in registration order. Subscription
changes made during callbacks do not alter this dispatch's membership.
Recursive calls run immediately with their own current snapshot; callers
must avoid unbounded notification recursion in model-update callbacks.

No work is performed when there are no listeners. A callback's unchecked
exception or error propagates to the caller without running the remaining
callbacks, and subscription changes already made are not rolled back.

</details>

<a id="type-changesignal-entry"></a>

### ChangeSignal.Entry — internal support type

[Source](../../src/main/java/valthorne/ui/behavior/ChangeSignal.java#L82)

Identity-based registration and removal handle tied to its enclosing
signal. The closed flag makes removal idempotent but is deliberately not
consulted during dispatch, allowing already captured snapshots to finish
invoking their original members. The callback remains held by this handle
even after removal, so callers should release unused closed handles as well.

<details>
<summary>ChangeSignal.Entry operation reference (1 declarations)</summary>

#### close

```java
        public void close()
```

Removes this entry by identity while preserving the order of remaining
registrations. The final removal restores the shared empty snapshot;
other removals allocate a replacement array. An already closed handle
returns immediately. Existing dispatch snapshots remain valid and may
still invoke this entry after close returns.

</details>

<a id="type-rangemodel"></a>

### RangeModel

[Source](../../src/main/java/valthorne/ui/behavior/RangeModel.java#L30)

Stores the finite numeric state shared by texture and NanoVG sliders.
Values are clamped to an inclusive range and can be snapped to increments
measured from its minimum. This model handles numeric and input calculations;
it does not draw, manage focus, or notify widget action listeners.

##### Range and Stepping

A maximum below the minimum collapses the range at the minimum. A zero
step disables snapping; negative steps are converted to zero. Positive steps
use nearest-integer rounding with ties to even through `Math#rint(double)`.
Both endpoints remain directly reachable even when the step does not divide
the range evenly. Changing the range or step reapplies clamping and snapping
to the current value.

```java
RangeModel range = new RangeModel(10, 97, 20);
range.step(5);
range.percent(1); // Exact maximum 97, even though it is off the step grid.
range.increment(-1); // Applies one negative step and snaps within the range.
```

Pointer coordinates are top-left-local track coordinates. Horizontal values
increase to the right; vertical values increase upward. Instances are mutable
and intended to be owned by the UI dispatch thread.

<details>
<summary>RangeModel operation reference (13 declarations)</summary>

#### Constructor

```java
public RangeModel(float min, float max, float value)
```

Initializes the inclusive range and clamps the initial value into it.
Snapping starts disabled. A reversed range collapses at its minimum.

- **`min`** — the finite lower endpoint
- **`max`** — the finite requested upper endpoint
- **`value`** — the finite initial value before clamping

**Throws `IllegalArgumentException`:** if an argument or the resulting range width is non-finite

#### min

```java
public float min()
```

Returns the inclusive lower endpoint, retained when a requested maximum
lies below it. Reading this value does not change the model.

**Returns:** the finite minimum in the slider's numeric units

#### max

```java
public float max()
```

Returns the effective inclusive upper endpoint after reversed-range
normalization. It is always greater than or equal to the minimum.

**Returns:** the finite effective maximum in the slider's numeric units

#### value

```java
public float value()
```

Returns the current value after the most recent clamping and optional
snapping operation. Reading the value does not perform another snap.

**Returns:** the current finite value within the inclusive endpoints

#### step

```java
public float step()
```

Returns the configured step size used for snapping and incremental input.
Zero disables snapping and selects the fallback increment size.

**Returns:** the nonnegative step in the slider's numeric units

#### range

```java
public void range(float min, float max)
```

Replaces the range and reapplies the current value to it. A maximum below
the minimum is raised to the minimum rather than swapping endpoints.
Validation occurs before the endpoints are modified.

- **`min`** — the finite lower endpoint
- **`max`** — the finite requested upper endpoint

**Throws `IllegalArgumentException`:** if either endpoint or the effective width is non-finite

#### value

```java
public void value(float next)
```

Clamps a finite candidate, optionally snaps interior values to the step
grid anchored at the minimum, and clamps the result again. Exact endpoints
bypass snapping. Halfway grid positions use ties-to-even rounding.

- **`next`** — the finite requested value in the range's numeric units

**Throws `IllegalArgumentException`:** if next is NaN or infinite

#### step

```java
public void step(float step)
```

Sets the snapping increment and reapplies it to the current value.
Negative finite inputs become zero, disabling snapping. A positive step
may exceed the range width; endpoint clamping still applies.

- **`step`** — the finite requested increment in numeric units

**Throws `IllegalArgumentException`:** if step is NaN or infinite

#### percent

```java
public float percent()
```

Converts the current value to a normalized fraction of the effective range.
A collapsed range returns zero to avoid division by zero.

**Returns:** a fraction from zero to one, or zero when both endpoints match

#### percent

```java
public void percent(float percent)
```

Clamps a finite fraction to zero through one, maps it into the numeric
range, then applies normal value snapping. The resulting fraction may
differ from the request when stepping is enabled.

- **`percent`** — the normalized requested fraction, not a zero-to-one-hundred percentage

**Throws `IllegalArgumentException`:** if percent is NaN or infinite

#### increment

```java
public void increment(float amount)
```

Moves by a multiple of the configured step, or a fallback increment when
stepping is disabled. The fallback is one hundredth of the range width,
with a minimum of `0.000001f`. The candidate is calculated in double
precision, clamped, then passed through normal value snapping.

- **`amount`** — the signed number of increments; fractional counts are supported

**Throws `IllegalArgumentException`:** if amount is NaN; infinite amounts clamp to an endpoint

#### pointer

```java
public void pointer(float coordinate, float length, float thumb, boolean vertical)
```

Maps a pointer coordinate to a value using the travel available to the
thumb center. Subtracts half the thumb length from the coordinate and uses
`length - thumb` as the usable travel. Nonpositive travel leaves the
value unchanged. Positions outside usable travel clamp to an endpoint.

- **`coordinate`** — pointer position along the track in top-left-local units
- **`length`** — full track length in the same units
- **`thumb`** — thumb length along the track in the same units
- **`vertical`** — whether to reverse the fraction so upward movement increases value

**Throws `IllegalArgumentException`:** if the calculated fraction is NaN

#### key

```java
public boolean key(int key, boolean vertical)
```

Applies a supported keyboard command to this range. Home and End select
the exact endpoints. Right/Left increment or decrement horizontal ranges;
Up/Down do so for vertical ranges. Other keys leave the value unchanged.

- **`key`** — the `valthorne.Keyboard` key code
- **`vertical`** — whether the range uses vertical arrow-key navigation

**Returns:** true for a recognized key, even if clamping leaves the value unchanged

</details>

<a id="type-scrollbehavior"></a>

### ScrollBehavior

[Source](../../src/main/java/valthorne/ui/behavior/ScrollBehavior.java#L21)

Computes scroll offsets consistently for texture and NanoVG scroll panels.
Wheel deltas are scaled into content-coordinate units and subtracted from
the current offsets. Both results are clamped to nonnegative scroll ranges.
The caller applies the returned offsets to its own panel state.
Fractional trackpad input is read through MouseScrollEvent's precise accessors,
avoiding truncation from its integer compatibility getters.

An event is consumed only when the resulting X or Y offset differs from
the supplied value, allowing an unchanged boundary scroll to continue through
normal routing to an ancestor. Existing consumption is never cleared. The
utility has no stored state and allocates a result vector for each call.

<details>
<summary>ScrollBehavior operation reference (1 declarations)</summary>

#### wheel

```java
public static Vector2f wheel(MouseScrollEvent event, boolean horizontal, boolean vertical,
                                 float x, float y, float maxX, float maxY, float speed)
```

Computes the next offsets and consumes the event if either changes.
Horizontal wheel input affects X when enabled. Vertical input affects Y
when vertical scrolling is enabled and has positive available range;
otherwise it falls back to X when horizontal scrolling is enabled and
the event has no horizontal delta.
The fallback depends on vertical capability and total available range,
not whether Y is already at its upper or lower boundary. A vertically
scrollable panel with positive maxY therefore does not redirect a blocked
vertical movement into X merely because it has reached an edge.

Both axes are clamped even when their scrolling flag is disabled, so
correcting an out-of-range starting offset can also consume the event.
Negative maximum offsets are treated as zero. Supply finite offsets,
extents, deltas and speed; this method does not validate numerical finiteness.

Positive deltas decrease offsets with a positive speed. Zero speed
suppresses wheel displacement but still clamps existing offsets; negative
speed reverses direction. The result preserves fractional movement and is
independent of future event reuse. No panel layout or scroll state is
modified by this helper.

- **`event`** — the wheel event to read and conditionally consume
- **`horizontal`** — whether horizontal wheel movement or fallback is enabled
- **`vertical`** — whether vertical wheel movement is enabled
- **`x`** — the current horizontal offset in content units
- **`y`** — the current vertical offset in content units
- **`maxX`** — the maximum horizontal offset, clamped to at least zero
- **`maxY`** — the maximum vertical offset, clamped to at least zero
- **`speed`** — content-coordinate units per wheel-delta unit

**Returns:** a newly allocated vector containing clamped X and Y offsets

</details>

<a id="type-textediting"></a>

### TextEditing

[Source](../../src/main/java/valthorne/ui/behavior/TextEditing.java#L27)

Shared keyboard-shortcut and system-clipboard adapter for texture and NanoVG
text fields backed by TextEditModel. Navigation and edits delegate to the model;
this utility does not own text, focus, enabled state or event routing.

Ctrl and Super are treated equivalently for shortcuts. Shift extends cursor
movement selections and changes Ctrl/Super+Z to redo. Secret fields suppress
copying and cutting through this adapter, while pasting and ordinary editing
remain available. This is an interaction policy, not secure text storage.

Keyboard dispatch returns whether it recognized a command, not whether text
changed. It does not consume the event; the receiving widget does so. Clipboard
operations use the platform clipboard synchronously and tolerate common access
failures. Run on the owning UI thread and let the model enforce insertion rules.

<details>
<summary>TextEditing operation reference (3 declarations)</summary>

#### key

```java
public static boolean key(TextEditModel model, KeyPressEvent event, boolean secret)
```

Dispatches supported editing keys to the model without consuming the event.
Ctrl/Super supports A, C, X, V, Z, Y, word-wise Left/Right and word deletion
with Backspace/Delete. Without those modifiers, Left/Right move by the model's
character boundaries, Home/End move to text endpoints and deletion is ordinary.
Shift extends navigation selections and selects redo for Ctrl/Super+Z.

Copy and cut are suppressed for secret fields; cut deletes only after a
successful clipboard write. Recognized commands return true even when no
edit occurs or clipboard access fails. Ctrl/Super+Home/End are not handled
by this switch. Focus, enabled-state and key-repeat policies belong to callers.

- **`model`** — the target editing model
- **`event`** — the nonnull key press whose key and modifiers are inspected
- **`secret`** — whether clipboard export must be suppressed

**Returns:** true for a recognized shortcut; false for an unhandled key combination

**Throws `NullPointerException`:** if event is null, or a handled operation dereferences a null model

#### copy

```java
public static boolean copy(TextEditModel model, boolean secret)
```

Writes selected text to the system clipboard without modifying the model.
Secret mode returns immediately, as does an empty selection. Clipboard busy,
headless-environment and security failures return false instead of interrupting
editing. A successful result confirms clipboard assignment, not later availability.

- **`model`** — the model supplying selected text
- **`secret`** — whether export is forbidden

**Returns:** whether selected text was successfully assigned to the clipboard

**Throws `NullPointerException`:** if model is null and secret is false

#### paste

```java
public static void paste(TextEditModel model)
```

Requests string-flavor clipboard data and passes a returned String to the
model's insertion operation. The model handles selection replacement and
input validation; this adapter does not sanitize or force acceptance.
Unsupported data, I/O failures, clipboard contention, headless operation
and security failures are ignored. No event is consumed and no success
status is returned. Other failures may propagate.

- **`model`** — the model that receives available clipboard text

**Throws `NullPointerException`:** if model is null when a String is retrieved

</details>

<a id="type-texteditmodel"></a>

### TextEditModel

[Source](../../src/main/java/valthorne/ui/behavior/TextEditModel.java#L32)

Single-line editing independent of fonts, rendering and native input.
Indices are UTF-16 offsets snapped to extended grapheme boundaries. Maximum length
is measured in Unicode code points, while movement and deletion preserve graphemes.
Validation reports acceptability but does not veto edits. Changes are synchronous
and unsynchronized; use the model on the owning UI thread.

```java
TextEditModel model = new TextEditModel();
model.maxLength(40);
model.text("Hello");
model.move(model.text().length(), false);
model.insert(" world");
model.undo();
```

Programmatic text replacement sanitizes content, collapses selection, and clears
history. Insertion and deletion retain up to 100 undo states. A lowered maximum
does not truncate existing text and still permits edits that do not increase an
already-over-limit code-point count. Notifications can report text, selection, or
validator changes and are not limited to text modifications.

<details>
<summary>TextEditModel operation reference (30 declarations)</summary>

#### sanitize

```java
public static String sanitize(String value)
```

Removes unpaired surrogate code points and replaces ISO control characters with
spaces, avoiding an extra space when the current output already ends in one.
Ordinary spaces are otherwise preserved. Valid unchanged input is returned directly.

- **`value`** — input text, or null for an empty result

**Returns:** sanitized single-line text

#### text

```java
public String text()
```

Returns the current immutable string without copying or validating it.

**Returns:** sanitized text

#### caret

```java
public int caret()
```

Returns the active selection end as a UTF-16 offset on a grapheme boundary.

**Returns:** current caret offset

#### anchor

```java
public int anchor()
```

Returns the fixed selection end as a UTF-16 offset. It can lie before or after
the caret depending on selection direction.

**Returns:** current anchor offset

#### start

```java
public int start()
```

Returns the smaller selection endpoint without changing selection direction.

**Returns:** inclusive selection start

#### end

```java
public int end()
```

Returns the larger selection endpoint without changing selection direction.

**Returns:** exclusive selection end

#### hasSelection

```java
public boolean hasSelection()
```

Tests whether anchor and caret differ. A collapsed selection has no selected text.

**Returns:** true when a nonempty range is selected

#### selectedText

```java
public String selectedText()
```

Extracts the text between the ordered endpoints. Returns an empty string when
selection is collapsed, without changing the caret or history.

**Returns:** selected substring

#### isValid

```java
public boolean isValid()
```

Runs the current validator against the current text each time it is called.
Validation exceptions propagate; edits are not rejected by this predicate.

**Returns:** predicate result for current text

#### validator

```java
public void validator(Predicate<String> validator)
```

Replaces the validity predicate and notifies listeners immediately without changing
text, selection, or history. The predicate is evaluated by isValid, not here.

- **`validator`** — replacement predicate

**Throws `NullPointerException`:** if validator is null

#### maxLength

```java
public void maxLength(int length)
```

Sets the insertion growth limit in code points. Does not truncate existing text,
clear history, or notify listeners. Programmatic text replacement bypasses this limit.

- **`length`** — nonnegative maximum code-point count

**Throws `IllegalArgumentException`:** if length is negative

#### onChange

```java
public AutoCloseable onChange(Runnable listener)
```

Adds a listener alongside existing widget synchronization listeners. Notifications
run synchronously; registration itself does not fire the callback.

- **`listener`** — change callback

**Returns:** subscription handle that removes the listener when closed

#### text

```java
public void text(String value)
```

Replaces text after sanitization, rebuilds grapheme boundaries, clamps and snaps the
old caret, collapses selection there, clears both histories, and notifies listeners.
Maximum length and validator do not prevent this assignment.

- **`value`** — replacement text, or null for empty text

#### clearHistory

```java
public void clearHistory()
```

Drops all undo and redo snapshots without modifying text or selection and without
notifying listeners.

#### canUndo

```java
public boolean canUndo()
```

Reports whether a prior edit snapshot is available without consuming it.

**Returns:** true when undo history is nonempty

#### canRedo

```java
public boolean canRedo()
```

Reports whether an undone snapshot can be restored without consuming it.

**Returns:** true when redo history is nonempty

#### undo

```java
public void undo()
```

Moves the current snapshot to redo and restores the most recent undo state.
Empty undo history is a no-op with no notification.

#### redo

```java
public void redo()
```

Moves the current snapshot to undo and restores the most recent redo state.
Empty redo history is a no-op with no notification.

#### insert

```java
public boolean insert(String value)
```

Replaces the selection with sanitized input, recording current endpoints for undo.
Rejects unchanged text and growth beyond the code-point limit; validator results
do not prevent insertion. Successful edits collapse selection and notify listeners.

- **`value`** — replacement text, or null for empty input

**Returns:** true if text changed

#### deleteBackward

```java
public void deleteBackward(boolean word)
```

Deletes the selection, or extends a temporary selection backward by one grapheme
or word segment before deleting. Saves original endpoints for undo; a boundary
no-op does not create history or notify listeners.

- **`word`** — true to use word navigation instead of one grapheme

#### deleteForward

```java
public void deleteForward(boolean word)
```

Deletes the selection, or selects the next grapheme or word segment for deletion.
Original selection endpoints are restored by undo after a successful change.

- **`word`** — true to use word navigation instead of one grapheme

#### deleteSelection

```java
public void deleteSelection()
```

Deletes a nonempty selection through the normal insertion/history path.
A collapsed selection is a no-op.

#### selectAll

```java
public void selectAll()
```

Sets anchor to zero and caret to the text end, then notifies listeners even if
the same range was already selected. Does not alter edit history.

#### move

```java
public void move(int offset, boolean extend)
```

Clamps a UTF-16 offset to text bounds and snaps it backward to a grapheme boundary.
Moves the caret there, preserves anchor only for extension, and always notifies.

- **`offset`** — requested UTF-16 caret offset
- **`extend`** — whether to retain the existing selection anchor

#### moveHorizontal

```java
public void moveHorizontal(boolean right, boolean extend, boolean word)
```

Moves by a grapheme or word boundary. Without extension or word movement, an
existing selection collapses to the endpoint in the requested direction. Other
moves start from the active caret and follow the normal move notification path.

- **`right`** — true to move forward, false backward
- **`extend`** — whether to preserve the selection anchor
- **`word`** — whether to use word segmentation

#### boundary

```java
public int boundary(int index)
```

Clamps an offset and returns the nearest grapheme boundary at or before it.
ASCII text uses every code-unit boundary; non-ASCII text uses the cached matcher result.

- **`index`** — requested UTF-16 offset

**Returns:** clamped preceding boundary

#### previous

```java
public int previous(int index)
```

Returns the boundary at or before index minus one, clamped to text bounds.
Offsets at or below zero return zero without reading text.

- **`index`** — UTF-16 offset from which to step backward

**Returns:** preceding clamped boundary

#### next

```java
public int next(int index)
```

Returns the first boundary strictly after an in-range offset. Negative offsets
return zero; offsets at or beyond the end return the text length.

- **`index`** — UTF-16 offset from which to step forward

**Returns:** next clamped boundary

#### previousWord

```java
public int previousWord(int index)
```

Snaps backward, skips preceding whitespace, then consumes a run with the same
letter-or-digit classification. Punctuation forms a separate run; this is a
navigation heuristic rather than locale-sensitive word analysis.

- **`index`** — starting UTF-16 offset

**Returns:** preceding word-segment boundary

#### nextWord

```java
public int nextWord(int index)
```

Snaps the starting offset, advances through matching letter-or-digit classification
until whitespace or a class change, then skips following whitespace. Traversal
uses grapheme boundaries and is not locale-sensitive word analysis.

- **`index`** — starting UTF-16 offset

**Returns:** following word-segment boundary

</details>

<a id="type-texteditmodel-state"></a>

### TextEditModel.State — internal support type

[Source](../../src/main/java/valthorne/ui/behavior/TextEditModel.java#L486)

Immutable edit-history snapshot sharing the immutable text string. Endpoints
represent the selection before an edit and are restored without revalidation.

Snapshots carry text and selection together so undo and redo restore a coherent editing
position. They contain no widget rendering state, clipboard handle, or callback.

- **`text`** — text at snapshot time
- **`anchor`** — fixed selection endpoint in UTF-16 units
- **`caret`** — active selection endpoint in UTF-16 units

## Related guides

- [Standard UI controls](ui-controls.md)
- [NanoVG UI controls](ui-nano.md)
- [Virtual lists, tables, and selection](ui-data.md)
