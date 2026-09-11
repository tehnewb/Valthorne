# Themes, styles, and design tokens

Author: Albert Beaupre

[System manual](README.md)

## Purpose

Themes resolve typed style values from rules, node identity/state, and shared tokens. StyleKey gives a value a name and expected type; StyleMap stores values; ResolvedStyle is the result consumed by a control. Tokens let related controls share a consistent palette and sizing vocabulary.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Typed keys | Keys distinguish values such as colors, dimensions, and actions. |
| Rules and states | Rules select appearance for normal, hovered, pressed, focused, or disabled states. |
| Shared tokens | UITokens and ProfessionalTheme provide a common design vocabulary. |
| Change propagation | Theme listeners and invalidation refresh controls after theme data changes. |
| Local overrides | Control setters and explicit style values can supplement or override theme-derived values according to each control. |

## Getting started

1. Start with a theme and define shared tokens for the application.
2. Add rules or style values using keys accepted by the target controls.
3. Attach the theme through the root/tree's style configuration.
4. Invalidate or notify changes through the theme APIs so cached styles are resolved again.

## Ownership and lifecycle

Colors and style objects may be shared references. Mutating a shared object can affect multiple nodes. Resolving a style and applying its layout effects are separate phases.

## Important behavior

- A key accepted by one control may not affect another control family.
- Check state precedence when a node is simultaneously focused, hovered, and pressed.
- Changing a style object without notifying/invalidation can leave cached appearance unchanged.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`ProfessionalTheme`](#type-professionaltheme)
- [`ResolvedStyle`](#type-resolvedstyle)
- [`StyleKey`](#type-stylekey)
- [`StyleMap`](#type-stylemap)
- [`StyleState`](#type-stylestate)
- [`Theme`](#type-theme)
- [`ThemeData`](#type-themedata)
- [`ThemeDataChangeEvent`](#type-themedatachangeevent)
- [`ThemeListener`](#type-themelistener)
- [`ThemeRule`](#type-themerule)
- [`UITokens`](#type-uitokens)

<a id="type-professionaltheme"></a>

### ProfessionalTheme

[Source](../../src/main/java/valthorne/ui/theme/ProfessionalTheme.java#L34)

Builds a density-scaled light or dark skin for texture and NanoVG UI controls.
The theme owns its bundled font and all nine-patch textures allocated by
`create()`. Theme data shares these resources and palette colors, so keep
this object alive while any styled root uses them. Create and close it on the
graphics thread, detaching or disposing dependent roots before closing.

```java
ProfessionalTheme theme = new ProfessionalTheme(false, 1.25f);
ThemeData styles = theme.create();
// Apply styles to the UI and retain theme for the UI's lifetime.
// After disposing all dependent roots:
theme.close();
```

<details>
<summary>ProfessionalTheme operation reference (14 declarations)</summary>

#### surface

```java
public final Color surface,
```

Mutable colors shared with generated styles.

#### raised

```java
public final Color surface, raised,
```

Mutable colors shared with generated styles.

#### hover

```java
public final Color surface, raised, hover,
```

Mutable colors shared with generated styles.

#### text

```java
public final Color surface, raised, hover, text,
```

Mutable colors shared with generated styles.

#### muted

```java
public final Color surface, raised, hover, text, muted,
```

Mutable colors shared with generated styles.

#### accent

```java
public final Color surface, raised, hover, text, muted, accent,
```

Mutable colors shared with generated styles.

#### border

```java
public final Color surface, raised, hover, text, muted, accent, border,
```

Mutable colors shared with generated styles.

#### disabled

```java
public final Color surface, raised, hover, text, muted, accent, border, disabled
```

Mutable colors shared with generated styles.

#### error

```java
public final  Color error
```

Shared validation-error accent color.

#### Constructor

```java
public ProfessionalTheme(boolean light, float density)
```

Selects a palette and loads the bundled Atkinson Hyperlegible font at a
rounded pixel size of `16 * density`. Nine-patch skins are deferred
until `create()`; the font is allocated immediately.

- **`light`** — true for the light palette, false for the dark palette
- **`density`** — finite size multiplier in the inclusive range 0.5 to 3

**Throws `IllegalArgumentException`:** if density is outside the supported range

**Throws `IllegalStateException`:** if the bundled font is missing or cannot be read

#### Constructor

```java
public ProfessionalTheme()
```

Creates the dark palette at unit density and loads its owned UI font.
Has the same graphics-thread and resource-lifetime requirements as the
configurable constructor.

#### getFont

```java
public Font getFont()
```

Returns the font shared by generated styles. Ownership stays with this theme;
callers must not dispose it separately or use it after `close()`.

**Returns:** the shared font reference

#### create

```java
    public ThemeData create()
```

Builds a new style collection with semantic tokens and state-specific rules
for both UI backends. Nano control classes are initialized first to register
their keys. Each call allocates additional owned texture skins; previously
returned collections remain valid until this theme is closed.

**Returns:** a new collection sharing this theme's font and palette

**Throws `IllegalStateException`:** if the theme has already been closed

#### close

```java
    public void close()
```

Releases every generated skin, then the font and its data. Repeated calls
return immediately. Call on the graphics thread after dependent UI roots
have been detached or disposed; existing style collections retain references
to the released resources and must no longer be rendered.

</details>

<a id="type-resolvedstyle"></a>

### ResolvedStyle

[Source](../../src/main/java/valthorne/ui/theme/ResolvedStyle.java#L55)

`ResolvedStyle` represents the final computed style values for a UI element
after theme tokens, matching rules, and explicit overrides have been merged together.
It is the read-oriented result object returned by `ThemeData#resolve(Class, String, short, StyleMap)`
and related overloads.

Internally, the class wraps a `StyleMap` that contains the resolved values.
It does not itself perform any rule resolution logic. Instead, it acts as a stable
container that consumers can query for final values using `StyleKey` instances.

This class is intentionally lightweight. It provides:

- typed value lookup through `get(StyleKey)`

- presence checks through `contains(StyleKey)`

- direct access to the underlying map through `asMap()`

A resolved style is typically used by UI nodes during skinning, layout preparation,
drawing, and runtime state updates. Because it represents the final merged result,
callers generally treat it as the authoritative style view for the node at that moment.

##### Example Usage

```java
ThemeData theme = new ThemeData();
theme.setToken(MyStyleKeys.TEXT_COLOR, Color.WHITE);

ResolvedStyle style = theme.resolve(Button.class, StyleState.NONE, null);

Color textColor = style.get(MyStyleKeys.TEXT_COLOR);
boolean hasTextColor = style.contains(MyStyleKeys.TEXT_COLOR);
StyleMap allValues = style.asMap();
```

This example demonstrates the full intended use of the class: receiving a resolved
style, reading typed values from it, checking for key presence, and accessing the
underlying map when necessary.

<details>
<summary>ResolvedStyle operation reference (4 declarations)</summary>

#### Constructor

```java
public ResolvedStyle(StyleMap values)
```

Creates a new `ResolvedStyle` wrapping the provided value map.

The supplied map is stored directly and becomes the source for all future lookups.

- **`values`** — the resolved style values to wrap

#### get

```java
public <T> T get(StyleKey<T> key)
```

Returns the typed value associated with the supplied `StyleKey`.

- **`key`** — the style key to query
- **`<T>`** — the expected value type

**Returns:** the value associated with the key

#### contains

```java
public boolean contains(StyleKey<?> key)
```

Returns whether the supplied key exists in the resolved style.

- **`key`** — the style key to test

**Returns:** `true` if the key exists in the resolved values

#### asMap

```java
public StyleMap asMap()
```

Returns the underlying `StyleMap` used by this resolved style.

This allows callers to inspect or pass along the full resolved map when direct
access to the whole result is needed.

**Returns:** the underlying resolved style map

</details>

<a id="type-stylekey"></a>

### StyleKey

[Source](../../src/main/java/valthorne/ui/theme/StyleKey.java#L75)

`StyleKey` is the strongly typed identifier used throughout Valthorne's
theming and styling system. A style key represents a named property that can
be stored in a `StyleMap`, assigned in a `ThemeRule`, included in
`ThemeData` tokens, and later read from a `ResolvedStyle`.

Each key contains four important pieces of information:

- a unique numeric ID used for fast indexed lookup inside `StyleMap`

- a globally registered string name

- a Java type describing the expected value type

- an optional default value returned when no explicit value exists

The registry guarantees that repeated calls using the same key name return the
same shared `StyleKey` instance when the declared type matches; a conflicting
type is rejected. The first registration's default value is retained. This matters because the key ID is
used as the storage index in `StyleMap`. If multiple different key objects
were created for the same conceptual property, style lookup would become inconsistent.

Because the class is generic, each key carries its expected value type. That makes
style access much safer and more convenient than storing everything as untyped
objects. When a value is retrieved from a `StyleMap`, the key's declared
type is used to cast the value back to the expected type.

This class is typically used as a static constant holder inside style or theme key
definition classes. The usual pattern is to define keys once and reuse them across
the engine.

##### Example Usage

```java
StyleKey<Float> PADDING = StyleKey.of("padding", Float.class, 0f);
StyleKey<String> FONT_NAME = StyleKey.of("fontName", String.class, "Default");

StyleMap map = new StyleMap();
map.set(PADDING, 12f);

float padding = map.get(PADDING);
String fontName = map.get(FONT_NAME);

StyleKey<Float> samePaddingKey = StyleKey.of("padding", Float.class, 0f);
int id = samePaddingKey.getID();
String name = samePaddingKey.getName();
```

This example demonstrates the full intended use of the class: creating keys,
using them in a style map, reading typed values, and relying on the shared
registry to retrieve the same key instance again.

- **`<T>`** — the value type associated with this style key

<details>
<summary>StyleKey operation reference (10 declarations)</summary>

#### registeredKeys

```java
public static synchronized java.util.List<StyleKey<?>> registeredKeys()
```

Captures the currently registered keys while holding the registration lock.
The returned list is unmodifiable and does not reflect later registrations;
its elements are the shared keys, and their order is unspecified.

**Returns:** an unmodifiable snapshot of all registered key instances

#### of

```java
public static synchronized <T> StyleKey<T> of(String name, Class<T> type)
```

Returns a shared `StyleKey` for the given name and type with no explicit
default value.

This delegates to `of(String, Class, Object)` using `null` as the
default value. If a key with the same name already exists, that existing key is
returned instead of creating a new one.

- **`name`** — the unique key name
- **`type`** — the Java type associated with the key
- **`<T>`** — the value type associated with the key

**Returns:** the shared style key instance for the given name

**Throws `NullPointerException`:** if a new key is requested with null name or type

**Throws `IllegalArgumentException`:** if the registered name has a different type,
including a null requested type

#### of

```java
@SuppressWarnings("unchecked")
    public static synchronized <T> StyleKey<T> of(String name, Class<T> type, T defaultValue)
```

Returns a shared `StyleKey` for the given name, type, and default value.

If a key with the supplied name already exists in the registry, that existing
instance is returned after verifying the declared type. Otherwise a new key is created, assigned a new
ID, stored in the registry, and returned.

The registry is keyed only by name, so repeated calls using the same name but
different types are rejected. A different default value with the same type
is ignored: the original key and its default remain in effect. Defaults are
retained by reference without copying or additional runtime type validation.

- **`name`** — the unique key name
- **`type`** — the Java type associated with the key
- **`defaultValue`** — the default value returned when the key is absent
- **`<T>`** — the value type associated with the key

**Returns:** the shared style key instance for the given name

**Throws `NullPointerException`:** if a new key is requested with null name or type

**Throws `IllegalArgumentException`:** if the registered name has a different type,
including a null requested type

#### get

```java
@SuppressWarnings("unchecked")
    public static <T> StyleKey<T> get(String name)
```

Returns a registered style key by name.

This method performs a registry lookup without creating a new key. If no key with
the given name exists, `null` is returned.
The generic result is unchecked because no type token is supplied; callers
must request the registered value type. Unlike registration and snapshots,
this lookup is not synchronized, so coordinate it with concurrent registration.

- **`name`** — the key name to look up
- **`<T>`** — the expected value type

**Returns:** the registered key, or `null` if none exists

#### affectsLayout

```java
public boolean affectsLayout()
```

Classifies whether changing this property should conservatively invalidate
node layout. Keys declared exactly as Color or NodeAction are treated as
paint/action changes; all other declared types, including subclasses of
those types, are classified as potentially affecting geometry. This checks
the declared type, not the current value or a callback's eventual effects.

**Returns:** whether a node override change should mark layout dirty

#### getID

```java
public int getID()
```

Returns the unique numeric ID of this key.

This ID is used directly as the index into `StyleMap`'s backing value array.

**Returns:** the unique key ID

#### getName

```java
public String getName()
```

Returns the registry name of this key.

**Returns:** the key name

#### getType

```java
public Class<T> getType()
```

Returns the declared Java type associated with this key.

**Returns:** the value type class

#### getDefaultValue

```java
public T getDefaultValue()
```

Returns the default value associated with this key.

This value is typically used by `StyleMap#get(StyleKey)` when no explicit
value is stored for the key.
The original default is returned by reference; mutable defaults are shared
by every consumer that falls back to this key and are not defensively copied.

**Returns:** the default value, which may be `null`

#### toString

```java
    public String toString()
```

Returns a debug-friendly string representation of this style key.

The returned string includes both the unique ID and the name so keys are easier
to identify during debugging and logging.

**Returns:** a string representation of this key

</details>

<a id="type-stylemap"></a>

### StyleMap

[Source](../../src/main/java/valthorne/ui/theme/StyleMap.java#L86)

`StyleMap` is the core value container used by Valthorne's theming system.
It stores style values indexed by `StyleKey` ID, allowing very fast lookups
and merges without relying on repeated string-based map access during runtime.

Unlike a traditional hash map keyed directly by property names, this class uses the
integer ID assigned by each `StyleKey` as the storage index inside an internal
object array. This makes reads and writes extremely efficient once keys have been
created and registered.

A `StyleMap` is used in several important places across the UI styling system:

- as the token store inside `ThemeData`

- as the value store inside each `ThemeRule`

- as the resolved backing map inside `ResolvedStyle`

- as optional override data during theme resolution

The map supports:

- setting typed values with `set(StyleKey, Object)`

- getting typed values with `get(StyleKey)`

- checking whether a value exists with `contains(StyleKey)`

- removing a value

- merging values from another `StyleMap`

- copying itself

- clearing all stored values

A key detail of this class is that missing values do not simply return `null`.
Instead, `get(StyleKey)` falls back to the key's default value when no stored
value exists for that key.

##### Example Usage

```java
StyleKey<Float> PADDING = StyleKey.of("padding", Float.class, 0f);
StyleKey<String> FONT_NAME = StyleKey.of("fontName", String.class, "Default");

StyleMap map = new StyleMap();
map.set(PADDING, 12f);

float padding = map.get(PADDING);
String fontName = map.get(FONT_NAME); // Falls back to default value

boolean hasPadding = map.contains(PADDING);

StyleMap copy = map.copy();
copy.remove(PADDING);

map.putAll(copy);
map.clear();
```

This example demonstrates the complete intended use of the class: setting values,
reading values with defaults, checking presence, copying, removing, merging, and
clearing the map.

Values are retained by reference, and null represents absence rather than
an explicit override of a key's default. Mutations notify an optional callback
after updating storage; changing a mutable stored object directly does not
notify the map. Copies and snapshots share those value objects. Instances
are not synchronized and should be accessed on their owning UI thread.

<details>
<summary>StyleMap operation reference (10 declarations)</summary>

#### Constructor

```java
public StyleMap()
```

Creates an empty map with a no-op change callback. Keys use their defaults
until explicit nonnull values are stored; initial capacity is 16 slots.

#### Constructor

```java
public StyleMap(Runnable changed)
```

Creates an empty map that calls the supplied action after notified mutations.
The callback is retained by reference and is not invoked during construction.
Callback failures propagate after the map has changed, without rollback.

- **`changed`** — the nonnull synchronous mutation callback

**Throws `NullPointerException`:** if changed is null

#### snapshot

```java
public java.util.Map<String, Object> snapshot()
```

Copies explicitly stored values into an unmodifiable map ordered by key
name. Default-only values are omitted. The map structure is independent of
subsequent edits, but referenced values are shared and can remain mutable.
This operation scans registered keys and does not notify the change callback.

**Returns:** an unmodifiable, shallow snapshot indexed by registered key name

#### set

```java
public <T> void set(StyleKey<T> key, T value)
```

Stores a value for the supplied `StyleKey`.

The backing array is expanded if necessary so the key's ID can be used as a valid
index. The provided value is then written directly into that slot.
A value equal to the current slot under Objects.equals is ignored, retaining
the existing reference. Otherwise the new reference is stored and the callback
runs once. Null clears the slot and restores default lookup behavior; values
are not copied or runtime-checked until retrieval.

- **`key`** — the style key whose value should be set
- **`value`** — the value to store
- **`<T>`** — the value type

**Throws `NullPointerException`:** if key is null

#### get

```java
public <T> T get(StyleKey<T> key)
```

Returns the value associated with the supplied `StyleKey`.

If the key's ID falls outside the current array bounds, or if the slot exists but
contains `null`, the key's default value is returned instead.

When a stored value exists, the key's declared type is used to cast it back to
the expected result type.

- **`key`** — the style key to query
- **`<T>`** — the expected value type

**Returns:** the stored value, or the key's default value if none is present

**Throws `NullPointerException`:** if key is null

**Throws `ClassCastException`:** if an explicitly stored value does not match the key type

#### contains

```java
public boolean contains(StyleKey<?> key)
```

Returns whether an explicit value is currently stored for the supplied key.

This method only checks for a concrete stored value. It does not consider a key's
default value to mean the key is present.

- **`key`** — the style key to test

**Returns:** `true` if a concrete value is stored for the key

**Throws `NullPointerException`:** if key is null

#### remove

```java
public void remove(StyleKey<?> key)
```

Removes the explicitly stored value for the supplied key.

After removal, future lookups for that key will fall back to the key's default
value unless another value is stored later.
Removing a present value invokes the callback once after clearing its slot;
removing an absent value is silent and does not shrink the backing array.

- **`key`** — the style key whose stored value should be removed

**Throws `NullPointerException`:** if key is null

#### putAll

```java
public void putAll(StyleMap other)
```

Copies all explicitly stored values from another `StyleMap` into this one.

The backing array is expanded as needed to fit the other map. Only non-null values
are copied, which means absent values in the source map do not clear values that
already exist in this map.
Equal values retain their existing references. The callback runs once after
the entire merge if any slot changed, and does not run for an unchanged merge.
Copied values remain shared with the source; no deep copy is performed.

- **`other`** — the source map whose values should be copied into this map

**Throws `NullPointerException`:** if other is null

#### copy

```java
public StyleMap copy()
```

Creates and returns a shallow copy of this `StyleMap`.

The backing value array is duplicated, but the individual stored objects are not
cloned. The resulting map therefore shares the same referenced values while
maintaining its own independent storage array.
The copy uses the no-op callback from the default constructor; it does not
inherit the original map's observer or notify it during copying.

**Returns:** a copy of this style map

#### clear

```java
public void clear()
```

Clears all explicitly stored values from the map.

After clearing, all keys behave as though no explicit values are stored and will
therefore resolve to their default values when queried.
Capacity is retained. The callback runs once even when the map was already
empty, so callers can use this operation as an explicit invalidation.

</details>

<a id="type-stylestate"></a>

### StyleState

[Source](../../src/main/java/valthorne/ui/theme/StyleState.java#L82)

`StyleState` is a compact mutable container used to represent the active
visual and interaction state of a UI element in Valthorne's theming system.
It wraps a `ShortBits` instance and stores state flags inside a single
`short`, allowing very fast checks, additions, removals, and rule matching.

These state flags are used by the theme system to determine which
`ThemeRule` instances apply to a given UI element at a given moment.
For example, a button may currently be hovered, pressed, focused, disabled,
checked, or selected. A `StyleState` object makes it easy to build,
inspect, and compare those flag combinations.

The class defines a standard set of built-in flags such as:

- `HOVERED`

- `PRESSED`

- `FOCUSED`

- `DISABLED`

- `CHECKED`

- `SELECTED`

- `DRAGGING`

- `ACTIVE`

- `ERROR`

This class is mutable and chainable. Methods such as `add(short)`,
`remove(short)`, `set(short, boolean)`, and `clear()`
return the current instance so callers can build state combinations fluently.

It also provides a convenience `matches(short, short)` method that mirrors
the matching behavior used by `ThemeRule`. This makes it useful both for
runtime state tracking and for direct comparisons against theme rule requirements.

##### Example Usage

```java
StyleState state = new StyleState();

state.add(StyleState.HOVERED)
     .add(StyleState.FOCUSED)
     .set(StyleState.PRESSED, true);

boolean hovered = state.has(StyleState.HOVERED);
boolean matches = state.matches(
        (short) (StyleState.HOVERED | StyleState.FOCUSED),
        StyleState.DISABLED
);

short flags = state.getFlags();

state.remove(StyleState.PRESSED);
state.clear();

StyleState restored = new StyleState(flags);
```

This example demonstrates the full intended use of the class: creating state,
adding flags, testing flags, matching against required and blocked states,
reading the raw bitmask, removing flags, clearing all flags, and restoring
state from an existing bitmask.

<details>
<summary>StyleState operation reference (21 declarations)</summary>

#### NONE

```java
public static final  short NONE
```

Represents no active state flags.

#### HOVERED

```java
public static final  short HOVERED
```

State flag indicating that the element is currently hovered.

#### PRESSED

```java
public static final  short PRESSED
```

State flag indicating that the element is currently pressed.

#### FOCUSED

```java
public static final  short FOCUSED
```

State flag indicating that the element is currently focused.

#### DISABLED

```java
public static final  short DISABLED
```

State flag indicating that the element is currently disabled.

#### CHECKED

```java
public static final  short CHECKED
```

State flag indicating that the element is currently checked.

#### SELECTED

```java
public static final  short SELECTED
```

State flag indicating that the element is currently selected.

#### DRAGGING

```java
public static final  short DRAGGING
```

State flag indicating that the element is currently being dragged.

#### ACTIVE

```java
public static final  short ACTIVE
```

State flag indicating that the element is currently active.

#### ERROR

```java
public static final  short ERROR
```

State flag indicating that the element is currently in an error state.

#### Constructor

```java
public StyleState()
```

Creates an empty `StyleState` with no active flags.

This is equivalent to constructing the class with `NONE`.

#### Constructor

```java
public StyleState(short flags)
```

Creates a `StyleState` initialized with the provided raw state flags.

- **`flags`** — the initial state bitmask

#### has

```java
public boolean has(short state)
```

Returns whether all bits in the supplied state mask are currently present.

This performs a full bitmask inclusion test, not a single-bit equality test.
That means callers may pass a combination of flags and this method returns
`true` only if every bit in that combination is currently active.

- **`state`** — the state bitmask to test

**Returns:** `true` if all supplied bits are currently active

#### add

```java
public StyleState add(short state)
```

Adds the supplied state bits to the current state.

Existing active bits remain active. The method returns this instance so calls
may be chained fluently.

- **`state`** — the state bits to add

**Returns:** this state instance

#### remove

```java
public StyleState remove(short state)
```

Removes the supplied state bits from the current state.

Any matching active bits are cleared. Non-matching bits remain unchanged.
The method returns this instance so calls may be chained fluently.

- **`state`** — the state bits to remove

**Returns:** this state instance

#### set

```java
public StyleState set(short state, boolean enabled)
```

Enables or disables the supplied state bits based on the provided boolean.

When `enabled` is `true`, this behaves like `add(short)`.
When `enabled` is `false`, it behaves like `remove(short)`.

- **`state`** — the state bits to enable or disable
- **`enabled`** — whether those bits should be enabled

**Returns:** this state instance

#### clear

```java
public StyleState clear()
```

Clears all active state flags.

After this call, `getFlags()` returns `NONE`.

**Returns:** this state instance

#### getFlags

```java
public short getFlags()
```

Returns the raw short bitmask representing the current active state flags.

**Returns:** the current state flag bitmask

#### setFlags

```java
public void setFlags(short flags)
```

Replaces the current state with the provided raw bitmask.

- **`flags`** — the new raw state flags

#### matches

```java
public boolean matches(short requiredStates, short blockedStates)
```

Returns whether the current state satisfies a required/blocked rule pair.

A match occurs only when:

- all bits in `requiredStates` are present

- none of the bits in `blockedStates` are present

This uses the same matching logic as `ThemeRule#matches(short)`.

- **`requiredStates`** — the bits that must all be present
- **`blockedStates`** — the bits that must all be absent

**Returns:** `true` if the current state matches the required and blocked masks

#### toString

```java
@Override
    public String toString()
```

Returns the string form of the underlying `ShortBits` state.

This is mainly useful for debugging and logging.

**Returns:** a string representation of the current state flags

</details>

<a id="type-theme"></a>

### Theme

[Source](../../src/main/java/valthorne/ui/theme/Theme.java#L69)

`Theme` is the root abstraction for creating UI theme definitions in Valthorne.
It exists as a simple factory-style contract whose responsibility is to build and
return a fully configured `ThemeData` instance.

Implementations of this interface typically define a complete visual language for
the UI system, including:

- global style tokens

- named resources

- rules for specific element types

- rules for style names

- rules for different interaction states

The purpose of keeping this as a dedicated interface is to make themes easy to
package, swap, recreate, and organize. A theme implementation can build a fresh
`ThemeData` object every time `create()` is called, allowing the UI
system to apply new themes cleanly without mutating old theme instances.

This interface is intentionally minimal. It does not prescribe how a theme should
be stored internally, only that it must be able to produce a `ThemeData`
object when requested.

##### Example Usage

```java
public final class DarkTheme implements Theme {

    @Override
    public ThemeData create() {
        ThemeData data = new ThemeData();

        data.setToken(MyStyleKeys.BACKGROUND, new Color(0.12f, 0.12f, 0.14f, 1f));
        data.setToken(MyStyleKeys.FOREGROUND, Color.WHITE);

        data.rule(Button.class)
            .set(MyStyleKeys.PADDING, 8f)
            .set(MyStyleKeys.CORNER_RADIUS, 6f);

        return data;
    }
}

Theme theme = new DarkTheme();
ThemeData themeData = theme.create();
```

This example demonstrates the complete intended use of the interface: implement it,
build a `ThemeData` instance inside `create()`, and then retrieve that
theme data for application to the UI.

<details>
<summary>Theme operation reference (1 declarations)</summary>

#### create

```java
ThemeData create()
```

Creates and returns a fully configured `ThemeData` instance.

Implementations should build all desired tokens, resources, and rules into the
returned object before handing it back to the caller.

**Returns:** a newly created theme data instance

</details>

<a id="type-themedata"></a>

### ThemeData

[Source](../../src/main/java/valthorne/ui/theme/ThemeData.java#L98)

`ThemeData` is the central runtime representation of a UI theme in Valthorne.
It stores style tokens, named resources, and rule sets that can later be resolved
into a final `ResolvedStyle` for a specific UI element type, optional style
name, and active state combination.

This class is the main data container used by the theming system. It supports
several layers of styling:

- global tokens that act as base values

- named resources for non-style data such as shared drawables or assets

- type-based rules for specific UI element classes

- style-name-specific rules

- state-based rules using required and blocked state flags

- final per-node override maps

Resolution works by starting with a copy of the token map, collecting all matching
rules for the requested element type and optional style name, sorting those rules by
priority, applying their values in order, and then finally applying explicit override
values if provided. The result is wrapped in a `ResolvedStyle`.

The class also integrates with the event system. Any time tokens or resources are
changed, a reusable `ThemeDataChangeEvent` is updated and published through
`JGL`. This allows theme-aware systems to react to theme changes automatically.
Editing the value map of a registered rule also publishes changes. Creating an
empty rule alone does not publish. Token and resource setters publish even if
the supplied value is unchanged; rule maps use StyleMap's mutation detection.

Rules are stored by element type and then by style name. During collection, the class
walks up the inheritance chain of the provided element type so that superclass rules
can participate in resolution.
Implemented interfaces are not traversed. Tokens, resources and rule values
are retained by reference; theme resolution copies map storage, not the stored
objects. Resource replacement does not dispose the old resource.

Theme data is intended for UI-thread use and is not synchronized. All theme
instances reuse one change event, so listeners should read its theme reference
immediately and avoid retaining the event or causing nested theme publications.
Listener registration is global to the event route, not filtered to this theme.

##### Example Usage

```java
ThemeData theme = new ThemeData();

theme.setToken(MyStyleKeys.TEXT_COLOR, Color.WHITE);
theme.setResource("buttonIcon", someDrawable);

theme.rule(Button.class)
     .set(MyStyleKeys.PADDING, 8f)
     .set(MyStyleKeys.CORNER_RADIUS, 4f);

theme.rule(Button.class, "primary", StyleState.HOVERED)
     .set(MyStyleKeys.BACKGROUND, hoverDrawable);

StyleMap overrides = new StyleMap();
overrides.set(MyStyleKeys.PADDING, 12f);

ResolvedStyle style = theme.resolve(Button.class, "primary", StyleState.HOVERED, overrides);
Color textColor = style.get(MyStyleKeys.TEXT_COLOR);

theme.addThemeDataChangeListener(event -> {
    if (event.getData() == theme) System.out.println("Theme changed");
});
```

This example demonstrates the complete workflow of the class: adding tokens,
adding resources, creating rules, resolving a final style, and listening for
theme changes.

<details>
<summary>ThemeData operation reference (17 declarations)</summary>

#### setToken

```java
public <T> void setToken(StyleKey<T> key, T value)
```

Stores or replaces a theme token value.

Tokens act as the base style layer for all later style resolution. After the token
is updated, a shared `ThemeDataChangeEvent` is published through `JGL`
with this theme data instance attached.
Publication also occurs for an equal value. Null removes the explicit token
and restores the key default; nonnull values are retained without copying.

- **`key`** — the style key to set
- **`value`** — the value associated with the key
- **`<T>`** — the value type

**Throws `NullPointerException`:** if key is null

#### getToken

```java
public <T> T getToken(StyleKey<T> key)
```

Returns the current token value associated with the supplied style key.
Missing or null slots return the key default. No rule resolution is performed,
and mutable values are returned by reference rather than copied.

- **`key`** — the style key to query
- **`<T>`** — the expected value type

**Returns:** the token value associated with the key

**Throws `NullPointerException`:** if key is null

**Throws `ClassCastException`:** if a stored token does not match the key type

#### setResource

```java
public void setResource(String name, Object value)
```

Stores or replaces a named resource in the theme.

Resources are not part of the style rule map itself, but instead provide a place
to store shared named objects such as images, drawables, fonts, or any other data
that theme users may want to retrieve by name. After the resource is updated, a
theme data change event is published.
Names and values may be null. The new value is retained by reference and
any previous resource is replaced without disposal; publication occurs even
for an unchanged mapping. Resource lifetime remains the caller's responsibility.

- **`name`** — the resource name
- **`value`** — the resource value

#### getResource

```java
public <T> T getResource(String name, Class<T> type)
```

Returns a named resource cast to the requested type.
A missing name or explicitly null resource returns null. The stored object
is borrowed, not copied, and this lookup does not consult style tokens.

- **`name`** — the resource name
- **`type`** — the expected resource type
- **`<T>`** — the expected resource type

**Returns:** the stored resource cast to the requested type, or null when absent

**Throws `NullPointerException`:** if type is null

**Throws `ClassCastException`:** if a nonnull resource is incompatible with type

#### rule

```java
public ThemeRule rule(Class<?> elementType)
```

Creates and registers a new rule for the given element type with no style name
and no required or blocked states.

- **`elementType`** — the UI element type the rule should target

**Returns:** the newly created rule

#### rule

```java
public ThemeRule rule(Class<?> elementType, String styleName)
```

Creates and registers a new rule for the given element type and style name with
no required or blocked states.

- **`elementType`** — the UI element type the rule should target
- **`styleName`** — the optional style name

**Returns:** the newly created rule

#### rule

```java
public ThemeRule rule(Class<?> elementType, StyleState state)
```

Creates and registers a new rule for the given element type and state.

If the supplied state is `null`, `StyleState#NONE` is used.

- **`elementType`** — the UI element type the rule should target
- **`state`** — the required state

**Returns:** the newly created rule

#### rule

```java
public ThemeRule rule(Class<?> elementType, String styleName, StyleState state)
```

Creates and registers a new rule for the given element type, style name, and state.

If the supplied state is `null`, `StyleState#NONE` is used.

- **`elementType`** — the UI element type the rule should target
- **`styleName`** — the optional style name
- **`state`** — the required state

**Returns:** the newly created rule

#### rule

```java
public ThemeRule rule(Class<?> elementType, short requiredStates)
```

Creates and registers a new rule for the given element type using raw required
state flags.

- **`elementType`** — the UI element type the rule should target
- **`requiredStates`** — the required state flags

**Returns:** the newly created rule

#### rule

```java
public ThemeRule rule(Class<?> elementType, String styleName, short requiredStates)
```

Creates and registers a new rule for the given element type, style name, and raw
required state flags.

- **`elementType`** — the UI element type the rule should target
- **`styleName`** — the optional style name
- **`requiredStates`** — the required state flags

**Returns:** the newly created rule

#### rule

```java
public ThemeRule rule(Class<?> elementType, String styleName, short requiredStates, short blockedStates)
```

Creates and registers a new rule with full control over element type, style name,
required states, and blocked states.

Rules are stored first by element type and then by style name. A `null`
style name is normalized to an empty string key. The created rule is appended to
the list for later resolution.
Repeated calls append independent rules rather than updating an existing
rule. Registration itself is silent; changes through the returned value map
publish a theme event. Target and masks are not validated: overlapping
required/blocked bits never match, and a null target is never collected.

- **`elementType`** — the UI element type the rule should target
- **`styleName`** — the optional style name
- **`requiredStates`** — the bit flags that must be present
- **`blockedStates`** — the bit flags that must not be present

**Returns:** the newly created rule

#### resolve

```java
public ResolvedStyle resolve(Class<?> elementType, String styleName, StyleState state, StyleMap overrides)
```

Resolves a style for the given element type, style name, state object, and override map.

If the provided state is `null`, `StyleState#NONE` is used.

- **`elementType`** — the element type to resolve for
- **`styleName`** — the optional style name
- **`state`** — the active state
- **`overrides`** — explicit override values

**Returns:** the resolved style

#### resolve

```java
public ResolvedStyle resolve(Class<?> elementType, StyleState state, StyleMap overrides)
```

Resolves a style for the given element type, state object, and override map.
Only unnamed rules are considered. A null state means no active flags;
null overrides are ignored. The result has independent map storage but
shares mutable values with the theme and overrides.

- **`elementType`** — the element type to resolve for
- **`state`** — the active state
- **`overrides`** — explicit override values

**Returns:** the resolved style

#### resolve

```java
public ResolvedStyle resolve(Class<?> elementType, String styleName, short states, StyleMap overrides)
```

Resolves a final `ResolvedStyle` for the given element type, optional style
name, active state flags, and optional overrides.

Resolution starts with a copy of the base token map. Matching unnamed rules for
the element type hierarchy are collected first. If a style name is provided,
matching named rules are also collected. All matches are then sorted by
`ThemeRule#getPriority()` and applied in order. Finally, explicit overrides
are applied last so they always win.
Only nonnull explicit values are merged, so an absent override does not
erase an earlier layer. Resolution allocates fresh result storage and does
not publish a change event or mutate its input maps.

Priority is ascending, so later values override earlier ones. Equal
priorities retain collection order: each pass visits the requested class
before its superclasses and each class's rules in registration order.
The unnamed pass precedes the named pass. An empty, nonnull style name
collects the unnamed group a second time. A null element type collects no
rules and resolves only tokens and overrides. Implemented interfaces are
not searched.

- **`elementType`** — the element type to resolve for
- **`styleName`** — the optional style name
- **`states`** — the active state flags
- **`overrides`** — explicit override values applied last

**Returns:** the resolved style

#### resolve

```java
public ResolvedStyle resolve(Class<?> elementType, short states, StyleMap overrides)
```

Resolves a final style for the given element type using raw state flags and
optional overrides.
This uses only unnamed rules and delegates to the full resolution method,
preserving its priority ordering, shallow-copy semantics and null handling.

- **`elementType`** — the element type to resolve for
- **`states`** — the active state flags
- **`overrides`** — explicit override values

**Returns:** the resolved style

#### addThemeDataChangeListener

```java
public void addThemeDataChangeListener(ThemeListener listener)
```

Registers a listener for theme data change events.

The listener is subscribed to `EventTypes#THEME_DATA_CHANGE` through `JGL`.
This is a global subscription and receives changes from other theme instances
too. Compare event.getData() with the desired theme inside the callback.
Registration does not send an initial event; retain the listener reference
so it can be removed when no longer needed.

- **`listener`** — the listener to register

#### removeThemeDataChangeListener

```java
public void removeThemeDataChangeListener(ThemeListener listener)
```

Unregisters a previously added theme data change listener.
Removal delegates to the global event route rather than a per-theme listener
list. Pass the original listener instance; calling this on a different
ThemeData instance still targets the same route.

- **`listener`** — the listener to remove

</details>

<a id="type-themedatachangeevent"></a>

### ThemeDataChangeEvent

[Source](../../src/main/java/valthorne/ui/theme/ThemeDataChangeEvent.java#L20)

Represents an event that carries `ThemeData` as its payload, typically emitted when
theme-related data changes in the system. This event extends the `Event` class, enabling
it to be processed and optionally consumed by subscribers or listeners.

The `ThemeDataChangeEvent` contains a `ThemeData` object that holds the
associated information about the theme that has been modified, updated, or changed.

This class provides methods to retrieve and modify the contained `ThemeData`.
The payload is a shared reference, not a snapshot; replacing it does not itself
publish another event. Null payloads are accepted by both the constructor and setter.

<details>
<summary>ThemeDataChangeEvent operation reference (3 declarations)</summary>

#### Constructor

```java
public ThemeDataChangeEvent(ThemeData data)
```

Constructs a new `ThemeDataChangeEvent` with the specified `ThemeData`.
This event signifies a change in theme-related data and carries `ThemeData`
as its payload, which clients can process or utilize as necessary.

- **`data`** — the `ThemeData` object representing the new or updated theme-related data

#### getData

```java
public ThemeData getData()
```

Retrieves the theme-related data associated with this event.

**Returns:** the `ThemeData` object representing the current theme-related data

#### setData

```java
public ThemeDataChangeEvent setData(ThemeData data)
```

Sets the theme-related data for this event.

- **`data`** — the `ThemeData` object representing the new or updated theme-related data

**Returns:** the current `ThemeDataChangeEvent` instance with the updated `ThemeData`

</details>

<a id="type-themelistener"></a>

### ThemeListener

[Source](../../src/main/java/valthorne/ui/theme/ThemeListener.java#L18)

A `ThemeListener` listens for changes in theme data and performs actions upon receiving
theme-related events.

This interface extends `EventHandler<ThemeDataChangeEvent>` to define event handling
specific to `ThemeDataChangeEvent`. It provides a default method for handling events and
delegates the handling logic to `onThemeChanged`.

Implementing classes should override `onThemeChanged` to define custom behavior when
a theme change occurs.

<details>
<summary>ThemeListener operation reference (2 declarations)</summary>

#### handle

```java
default void handle(ThemeDataChangeEvent event)
```

Handles the ThemeDataChangeEvent when published by an `EventPublisher`.

If the event is consumed within this method (via `Event.consume()`), no subsequent
listeners will process it. Exceptions thrown here may disrupt the event handling chain,
depending on the publisher's implementation.

- **`event`** — the event to handle

#### onThemeChanged

```java
void onThemeChanged(ThemeDataChangeEvent theme)
```

Called when a theme change event occurs. This method is intended to be overridden
in implementing classes to define specific behavior that should be executed when the
theme changes.

- **`theme`** — the ThemeDataChangeEvent containing information about the new theme data

</details>

<a id="type-themerule"></a>

### ThemeRule

[Source](../../src/main/java/valthorne/ui/theme/ThemeRule.java#L77)

`ThemeRule` represents a single rule entry inside the Valthorne theming system.
A rule targets a specific UI element type, an optional style name, and a set of
required and blocked state flags. When a theme is resolved, matching rules contribute
their values into the final `ResolvedStyle`.

Conceptually, a theme rule answers the question:

<blockquote>
What style values should be applied when a certain type of UI element, optionally
with a specific style name, is in a certain combination of states?
</blockquote>

Each rule contains:

- an element type the rule applies to

- an optional style name for named style variants

- a set of required state flags that must all be present

- a set of blocked state flags that must all be absent

- a `StyleMap` of key/value pairs contributed by the rule

Rules are later collected and sorted by priority during theme resolution.
More specific rules generally receive higher priority than broader ones. For example,
a rule with a style name and several required states will typically sort after a
generic base rule, allowing its values to override less specific entries.

This class also provides a fluent `set(StyleKey, Object)` method so rules
can be configured in a compact chained style when building theme definitions.
Target metadata is fixed after construction, but the value map is live and mutable.
Rules created through ThemeData publish theme changes through that map's callback;
directly constructed rules have no change observer and are not automatically
registered in a theme. Use on the owning UI thread, without concurrent mutation.

##### Example Usage

```java
ThemeRule base = new ThemeRule(Button.class, null)
        .set(MyStyleKeys.PADDING, 8f)
        .set(MyStyleKeys.CORNER_RADIUS, 6f);

ThemeRule hoveredPrimary = new ThemeRule(
        Button.class,
        "primary",
        StyleState.HOVERED,
        StyleState.DISABLED
).set(MyStyleKeys.BACKGROUND, hoverDrawable)
 .set(MyStyleKeys.TEXT_COLOR, Color.WHITE);

boolean matches = hoveredPrimary.matches(StyleState.HOVERED);
int priority = hoveredPrimary.getPriority();
StyleMap values = hoveredPrimary.getValues();
```

This example demonstrates the complete use of the class: creating rules,
assigning style values, checking whether a state combination matches, reading
computed priority, and accessing the stored values.

<details>
<summary>ThemeRule operation reference (11 declarations)</summary>

#### Constructor

```java
public ThemeRule(Class<?> elementType, String styleName)
```

Creates a new theme rule for the given element type and style name with no
required or blocked states.

This is the broadest rule form and is useful for defining baseline styles
that apply regardless of interaction state.
This creates an empty, standalone rule without registering it in ThemeData.

- **`elementType`** — the element type targeted by this rule
- **`styleName`** — the optional style name targeted by this rule

#### Constructor

```java
public ThemeRule(Class<?> elementType, String styleName, short requiredStates)
```

Creates a new theme rule for the given element type, style name, and required
state flags with no blocked states.
The required mask is stored unchanged; no state bits are validated or inferred.
The new rule is standalone and initially contributes no values.

- **`elementType`** — the element type targeted by this rule
- **`styleName`** — the optional style name targeted by this rule
- **`requiredStates`** — the state flags that must be present for a match

#### Constructor

```java
public ThemeRule(Class<?> elementType, String styleName, short requiredStates, short blockedStates)
```

Creates a new theme rule with full control over element type, style name,
required states, and blocked states.

The rule does not copy these values into another structure; they are stored
directly and later used by `matches(short)` and `getPriority()`.
No target or mask validation is performed. Overlapping required and blocked
bits are accepted but can never match. This public constructor installs a
no-op change callback and does not register the rule in a theme.

- **`elementType`** — the element type targeted by this rule
- **`styleName`** — the optional style name targeted by this rule
- **`requiredStates`** — the state flags that must all be present
- **`blockedStates`** — the state flags that must all be absent

#### getElementType

```java
public Class<?> getElementType()
```

Returns the element type targeted by this rule.
The same Class reference supplied at construction is returned. This metadata
is used for theme rule selection, not checked by the state-only matches method.

**Returns:** the targeted element type, possibly null if constructed that way

#### getStyleName

```java
public String getStyleName()
```

Returns the optional style name targeted by this rule.

A `null` or empty style name generally represents an unnamed base rule.
The constructor preserves the original value rather than normalizing null
to an empty string; neither form receives the named-style priority bonus.

**Returns:** the style name, or `null` if none was assigned

#### getRequiredStates

```java
public short getRequiredStates()
```

Returns the required state flags for this rule.
All of these bits must occur in a matching state mask; zero imposes no
positive state requirement. The stored mask is returned unchanged.

**Returns:** the required state bitmask

#### getBlockedStates

```java
public short getBlockedStates()
```

Returns the blocked state flags for this rule.
Any shared bit rejects a match; zero blocks no states. Bits may overlap
the required mask, in which case no state combination can satisfy the rule.

**Returns:** the blocked state bitmask

#### getValues

```java
public StyleMap getValues()
```

Returns the style values stored by this rule.

These values are merged into resolved theme output when the rule matches.
This is the owned, mutable map rather than a copy. Editing it directly uses
the same callback as set; mutable value objects remain shared by reference,
and changing those objects internally does not trigger map notifications.

**Returns:** the rule's style value map

#### matches

```java
public boolean matches(short states)
```

Returns whether this rule matches the provided state flags.

A match occurs only when:

- all required state flags are present in `states`

- none of the blocked state flags are present in `states`

This tests state masks only: it does not compare element classes or style
names. ThemeData selects candidate rules before invoking this predicate.
Additional active bits that are neither required nor blocked are allowed.

- **`states`** — the active state flags to test against this rule

**Returns:** `true` if the rule matches the provided state combination

#### getPriority

```java
public int getPriority()
```

Computes and returns this rule's priority value.

The priority is used during theme resolution to sort matching rules before
their values are applied. Higher priority rules are considered more specific.

The priority calculation considers:

- type depth in the class hierarchy

- whether a non-empty style name is present

- how many required state bits are specified

- how many blocked state bits are specified

The exact score is the number of classes in the target's superclass chain,
plus 1000 for a nonempty style name, plus 100 per required bit, minus 10 per
blocked bit. Bit counts use the unsigned 16-bit masks. A null target contributes
zero type depth; interfaces are not traversed as a separate hierarchy.
More blocked bits therefore lower the score rather than increasing specificity.

**Returns:** the computed priority for this rule

#### set

```java
public <T> ThemeRule set(StyleKey<T> key, T value)
```

Stores a style value in this rule and returns the rule for fluent chaining.

This method is commonly used while building themes so multiple values can be
assigned in a concise chained form.
Values are retained by reference. Null removes an explicit contribution,
and an equal replacement is ignored by StyleMap. A changed value invokes
the map's callback after storage; this does not register a standalone rule.

- **`key`** — the style key to assign
- **`value`** — the value to store for the key
- **`<T>`** — the value type

**Returns:** this rule for fluent configuration

**Throws `NullPointerException`:** if key is null

</details>

<a id="type-uitokens"></a>

### UITokens

[Source](../../src/main/java/valthorne/ui/theme/UITokens.java#L31)

Shared semantic style keys for UI dimensions and palette roles. ThemeData can
supply these as base tokens, while rules and per-node overrides may replace
them during resolution. Sizes use layout units; this holder does not scale
values for display density or apply them directly to widgets.

```java
ThemeData theme = new ThemeData();
theme.setToken(UITokens.CONTROL_HEIGHT, 36f);
theme.setToken(UITokens.SPACING, 8f);
theme.setToken(UITokens.ACCENT, new Color(0.2f, 0.5f, 1f, 1f));
```

ProfessionalTheme populates these roles alongside concrete widget style
properties. A role affects rendering only where a consumer reads it; setting
a semantic color does not automatically rewrite every widget-specific color
key. CONTROL_HEIGHT is also read by UINode when applying control styling.

Keys are globally registered by name through StyleKey. The defaults below
apply when these names are first registered here; an earlier registration of
the same name and type keeps its original default. Color defaults and control
height are null, so consumers must handle unspecified values. Mutable colors
supplied by a theme are shared by reference rather than copied by the key.

<details>
<summary>UITokens operation reference (8 declarations)</summary>

#### CONTROL_HEIGHT

```java
public static final  StyleKey<Float> CONTROL_HEIGHT
```

Minimum height applied by UINode to texture and Nano buttons and text fields
when both height and minimum height are automatic. No key-level default is
supplied; ProfessionalTheme assigns a density-scaled value in layout units.
An unspecified token leaves the control's existing sizing policy in effect.

#### SPACING

```java
public static final  StyleKey<Float> SPACING
```

Semantic spacing scale in layout units, initially defaulting to 8.
Consumers decide which gaps or padding values use this scale.

#### RADIUS

```java
public static final  StyleKey<Float> RADIUS
```

Semantic corner radius in layout units, initially defaulting to 6.
This does not automatically update a widget's concrete radius property.

#### FONT_SIZE

```java
public static final  StyleKey<Float> FONT_SIZE
```

Semantic font size in layout units, initially defaulting to 16.
Font selection and application of this size remain the consumer's responsibility.

#### SURFACE

```java
public static final  StyleKey<Color> SURFACE
```

Base surface color role, with no default color. Themes supply the shared
color used by components that explicitly consume this role.

#### TEXT

```java
public static final  StyleKey<Color> TEXT
```

Primary text color role, with no default color. This semantic value is
separate from individual text widgets' concrete style keys.

#### ACCENT

```java
public static final  StyleKey<Color> ACCENT
```

Accent color role for emphasis and selection styling, with no default.
Widgets or theme builders choose where to apply the shared color value.

#### ERROR

```java
public static final  StyleKey<Color> ERROR
```

Error color role for validation or failure styling, with no default.
Assigning it does not set a node's error state or validate input.

</details>

## Related guides

- [UI roots, nodes, and input routing](ui-core.md)
- [Standard UI controls](ui-controls.md)
- [NanoVG UI controls](ui-nano.md)
- [UI layout and alignment](ui-layout.md)
