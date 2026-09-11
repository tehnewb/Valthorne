# Events and listeners

Author: Albert Beaupre

[System manual](README.md)

## Purpose

The event system separates event data from handlers and dispatch. Event types identify routes; `EventPublisher` maintains ordered subscriptions. The `JGL` convenience methods connect application code to the engine publisher. An event can be consumed during dispatch, so ownership of an interaction can be decided before later handlers act.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Typed routes | `EventType` and `EventTypes` associate handlers with the intended payload type. |
| Priorities | Registration priority and insertion sequence establish deterministic dispatch order. |
| Consumption | A handler can consume an accepted event to prevent unwanted downstream handling. |
| Adapters | Listener adapters let you implement only the keyboard or pointer callbacks you need. |

## Getting started

1. Choose the concrete event route from `EventTypes`.
2. Create and retain a handler, then subscribe it through the publisher or `JGL`.
3. Inspect the event and consume it only when your handler accepts the interaction.
4. Unsubscribe the same handler object during screen or component cleanup.

## Ownership and lifecycle

Dispatch invokes handlers synchronously on the publishing thread. Immutable routing snapshots permit registration changes without rebuilding the route inside every publish, but they do not make the handler's own mutable state thread-safe.

## Important behavior

- Do not assume publishing automatically moves work onto the render thread.
- Avoid constructing a new lambda when unsubscribing an earlier lambda; retain its identity.
- See each event's fields for units and coordinate conventions.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`Event`](#type-event)
- [`EventHandler`](#type-eventhandler)
- [`EventPublisher`](#type-eventpublisher)
- [`KeyEvent`](#type-keyevent)
- [`KeyPressEvent`](#type-keypressevent)
- [`KeyReleaseEvent`](#type-keyreleaseevent)
- [`MouseDragEvent`](#type-mousedragevent)
- [`MouseEvent`](#type-mouseevent)
- [`MouseMoveEvent`](#type-mousemoveevent)
- [`MousePressEvent`](#type-mousepressevent)
- [`MouseReleaseEvent`](#type-mousereleaseevent)
- [`MouseScrollEvent`](#type-mousescrollevent)
- [`TextInputEvent`](#type-textinputevent)
- [`WindowFocusEvent`](#type-windowfocusevent)
- [`WindowResizeEvent`](#type-windowresizeevent)
- [`EventType`](#type-eventtype)
- [`EventTypes`](#type-eventtypes)
- [`KeyAdapter`](#type-keyadapter)
- [`KeyListener`](#type-keylistener)
- [`MouseAdapter`](#type-mouseadapter)
- [`MouseListener`](#type-mouselistener)
- [`MouseScrollListener`](#type-mousescrolllistener)
- [`WindowResizeListener`](#type-windowresizelistener)

<a id="type-event"></a>

### Event

[Source](../../src/main/java/valthorne/event/Event.java#L41)

Base class for every event dispatched by `EventPublisher`.

##### Routing model

Every event instance permanently stores a compact integer type identifier. The identifier is
supplied by an `EventType` when the event is constructed and is used directly as an array
index by `EventPublisher`. Publication therefore does not require a `Class` lookup,
a hash operation, reflection, annotation inspection, or a superclass walk.

##### Consumption

A handler may call `consume()` to stop propagation of the current event. The publisher
resets the consumed state immediately before every dispatch, so the same event object may be
reused safely by the same owner for many sequential publications.

##### Thread ownership

Event instances are deliberately **not thread-safe**. In particular, the consumed
flag is a plain `boolean`, not a volatile or atomic value. A single event instance must be
owned by one producer/dispatch thread at a time and must never be published concurrently.
This avoids synchronization costs on every handler invocation and is essential for the intended
high-throughput use case.

Multiple different event instances may of course be published concurrently through the same
`EventPublisher`; the publisher itself is designed for that usage.

<details>
<summary>Event operation reference (6 declarations)</summary>

#### Constructor

```java
protected Event(EventType<?> type)
```

Creates an event permanently associated with the supplied event type.

- **`type`** — type descriptor whose ID will be stored by this event

**Throws `NullPointerException`:** if `type` is `null`

#### typeId

```java
public final int typeId()
```

Returns the dense numeric route ID for this event.

The value never changes during the lifetime of the event instance.

**Returns:** zero-based event type identifier

#### isConsumed

```java
public final boolean isConsumed()
```

Returns whether a handler has consumed the current publication.

**Returns:** `true` when propagation should stop

#### consume

```java
public final void consume()
```

Stops propagation to any handlers that have not yet executed.

Calling this method repeatedly is harmless. The old event system threw when an event was
consumed twice; the high-throughput system intentionally avoids that exceptional branch in
normal handler code.

#### unconsume

```java
public final void unconsume()
```

Explicitly clears the consumed state.

Application code normally does not need to call this because `EventPublisher#publish(Event)`
resets the state automatically. The method is retained as a convenient compatibility utility.

#### toString

```java
    public String toString()
```

Returns a lightweight diagnostic representation.

**Returns:** class name, type ID, and consumed state

</details>

<a id="type-eventhandler"></a>

### EventHandler

[Source](../../src/main/java/valthorne/event/EventHandler.java#L32)

Minimal synchronous handler used by the high-throughput event dispatcher.

Registration itself declares which event route a handler accepts, so this interface has no
`canHandle(...)` method. If a specific handler needs dynamic filtering, the predicate
belongs inside `handle(Event)`. Handlers that do not need filtering therefore avoid an
unnecessary virtual call and conditional branch for every event they receive.

##### Execution guarantees

- Handlers execute synchronously on the thread calling `EventPublisher#publish(Event)`.

- Higher numeric registration priority executes before lower priority.

- Equal-priority handlers execute in registration order.

- `Event#consume()` prevents all remaining handlers for that publication from running.

- Runtime exceptions propagate directly to the publisher caller.

##### Exception policy

The publisher does not wrap every invocation in a try/catch block. A subsystem that requires
isolation should register a wrapper handler that applies its own logging/recovery policy. This
keeps the universal hot path free of work that many events do not need.

- **`<E>`** — event type accepted by this handler

<details>
<summary>EventHandler operation reference (1 declarations)</summary>

#### handle

```java
void handle(E event)
```

Handles one synchronously dispatched event.

- **`event`** — event being dispatched

</details>

<a id="type-eventpublisher"></a>

### EventPublisher

[Source](../../src/main/java/valthorne/event/EventPublisher.java#L74)

High-throughput synchronous event dispatcher built around immutable route snapshots.

##### Why this replaces the old publisher

The previous implementation used a `ConcurrentHashMap<Class<? extends Event>, ...>`,
`TreeSet`, reflection-backed priority discovery, superclass traversal, and a per-listener
`canHandle(...)` call. Those features made publication flexible but placed substantial
general-purpose machinery directly on the hottest path.

This implementation moves work to registration time. Publication performs a numeric array lookup
and iterates a dense immutable handler array. It does not perform reflection, hashing, class-tree
traversal, sorting, listener-map synchronization, or dispatcher-owned allocation.

##### Publication hot path

```java
EventHandler<Event>[] handlers = routes[event.typeId()];
for (int i = 0; i < handlers.length; i++) {
    handlers[i].handle(event);
    if (event.isConsumed()) {
        return;
    }
}
```

##### Thread safety and snapshot semantics

Multiple threads may call `publish(Event)` concurrently. Listener registration and
unregistration are serialized by a private mutation lock, but publication never acquires that
lock. Route mutations build new immutable arrays and publish a replacement top-level table via
a volatile reference.

An event already being dispatched may finish against the old snapshot after a listener is
unregistered. A later publication observes either the complete old snapshot or the complete new
snapshot, never a partially modified route.

The publisher is thread-safe; individual mutable `Event` instances are not. Never publish
the same event object concurrently.

##### Priorities

Priority is explicit at registration time. Higher values execute first. Equal values use a
monotonic registration sequence as a deterministic tie-breaker. No reflection or annotation is
required.

##### Asynchronous processing

This class intentionally contains no executor or queue. If producers must be decoupled from
handlers, place a bounded queue/ring-buffer and sharding layer in front of one or more publishers.
Separating scheduling from dispatch keeps backpressure, ownership, and ordering policy explicit.

<details>
<summary>EventPublisher operation reference (9 declarations)</summary>

#### Constructor

```java
public EventPublisher()
```

Creates a publisher sized for the built-in `EventTypes` registry.

This is the normal constructor for the Valthorne event package. The explicit-size constructor
remains available for tests or applications that maintain a different compatible registry.

#### Constructor

```java
public EventPublisher(int eventTypeCount)
```

Creates a publisher capable of routing IDs in `[0, eventTypeCount)`.

- **`eventTypeCount`** — number of route slots to allocate

**Throws `IllegalArgumentException`:** if `eventTypeCount <= 0`

#### register

```java
public <E extends Event> boolean register(EventType<E> type, int priority, EventHandler<? super E> handler)
```

Registers a handler at an explicit priority.

Registration identity uses `==`, not `Object#equals(Object)`. The exact same
handler object cannot be inserted twice on one route, while unrelated handler objects remain
independent even if they implement unusual equality semantics.

- **`type`** — event route to receive
- **`priority`** — execution priority; larger values execute first
- **`handler`** — handler instance
- **`<E>`** — concrete event type

**Returns:** `true` if added, `false` if the same instance was already registered

**Throws `NullPointerException`:** if `type` or `handler` is null

**Throws `IllegalArgumentException`:** if the type ID is outside this publisher's route table

#### register

```java
public <E extends Event> boolean register(EventType<E> type, EventHandler<? super E> handler)
```

Registers a handler at normal priority `0`.

- **`type`** — event route
- **`handler`** — handler instance
- **`<E>`** — concrete event type

**Returns:** `true` if the handler was added

#### unregister

```java
public <E extends Event> boolean unregister(EventType<E> type, EventHandler<? super E> handler)
```

Removes one exact handler instance from one event route.

An in-flight publication that already captured the previous immutable route may still invoke
the handler once. Publications observing the new snapshot will not.

- **`type`** — event route
- **`handler`** — exact handler instance to remove
- **`<E>`** — concrete event type

**Returns:** `true` if a registration was removed

#### publish

```java
public void publish(Event event)
```

Publishes an event synchronously.

This method is the performance-critical path. It resets consumption, captures one immutable
route-table snapshot, directly indexes the route by `Event#typeId()`, and invokes the
already-ordered handler array.

No dispatcher-owned object is allocated during a successful publication. Runtime exceptions
from handlers are allowed to propagate directly to the caller.

- **`event`** — event to dispatch

**Throws `NullPointerException`:** if `event` is null

**Throws `ArrayIndexOutOfBoundsException`:** if the event's type ID is not valid for this publisher

**Throws `RuntimeException`:** if a handler throws one

#### isRegistered

```java
public boolean isRegistered(EventType<?> type)
```

Returns whether a route currently has at least one handler.

- **`type`** — event route

**Returns:** `true` if one or more handlers are registered

#### listenerCount

```java
public int listenerCount(EventType<?> type)
```

Returns the currently visible handler count for one route.

- **`type`** — event route

**Returns:** number of handlers in the immutable route snapshot

#### clear

```java
public void clear()
```

Removes all listener registrations.

Existing in-flight publications may finish using snapshots they captured before the clear.
New publications observe an entirely empty route table.

</details>

<a id="type-eventpublisher-registration"></a>

### EventPublisher.Registration — internal support type

[Source](../../src/main/java/valthorne/event/EventPublisher.java#L385)

Cold-path registration metadata. These objects are never traversed by `publish(Event)`.

Priority and sequence determine the order used when rebuilding immutable routes.
The handler is a borrowed reference; this record does not invoke it or manage its lifetime.

<a id="type-keyevent"></a>

### KeyEvent

[Source](../../src/main/java/valthorne/event/events/KeyEvent.java#L35)

Keyboard event payload containing a key code and modifier bit mask.

This class remains the shared payload superclass for press and release events, but inheritance no
longer controls listener routing. A directly-created `KeyEvent` uses `EventTypes#KEY`;
`KeyPressEvent` and `KeyReleaseEvent` pass their own concrete type descriptors through
the protected constructor.

Instances are mutable to support reuse. They must not be modified while dispatch is in progress
and must not be shared concurrently between publishing threads.

Key codes are narrowed to signed shorts and modifier masks to bytes without range
validation. Payload setters do not reset consumption or change the numeric route.
Character lookup is a key-code mapping, not Unicode text-input composition.

<details>
<summary>KeyEvent operation reference (11 declarations)</summary>

#### Constructor

```java
public KeyEvent(int key, int modifiers)
```

Creates a raw key event routed as `EventTypes#KEY`.

- **`key`** — GLFW key code
- **`modifiers`** — GLFW modifier bit mask

#### Constructor

```java
protected KeyEvent(EventType<?> type, int key, int modifiers)
```

Constructor used by concrete key-event subclasses to select their own numeric route.

- **`type`** — concrete event type
- **`key`** — GLFW key code
- **`modifiers`** — GLFW modifier bit mask

#### set

```java
public KeyEvent set(int key, int modifiers)
```

Replaces both payload fields for object reuse.

- **`key`** — GLFW key code
- **`modifiers`** — modifier mask

**Returns:** this event

#### setModifiers

```java
public void setModifiers(int modifiers)
```

Replaces this payload component without changing other values, the numeric route,
or consumption state. Storage uses the declared field type without range validation.

- **`modifiers`** — modifier bit mask

#### getKey

```java
public short getKey()
```

Returns the current stored payload component without querying native input or
window state. Copy the value if it is needed after this reusable event is updated.

**Returns:** key code

#### setKey

```java
public void setKey(int key)
```

Replaces this payload component without changing other values, the numeric route,
or consumption state. Storage uses the declared field type without range validation.

- **`key`** — key code

#### getChar

```java
public char getChar()
```

Resolves the current key code to the application's character representation.

**Returns:** character corresponding to the current key code

#### isShiftDown

```java
public boolean isShiftDown()
```

Tests the stored modifier bit without polling current keyboard state.
The result describes this event's payload at the time it is read.

**Returns:** whether Shift was active

#### isCtrlDown

```java
public boolean isCtrlDown()
```

Tests the stored modifier bit without polling current keyboard state.
The result describes this event's payload at the time it is read.

**Returns:** whether Control was active

#### isAltDown

```java
public boolean isAltDown()
```

Tests the stored modifier bit without polling current keyboard state.
The result describes this event's payload at the time it is read.

**Returns:** whether Alt was active

#### isSuperDown

```java
public boolean isSuperDown()
```

Tests the stored modifier bit without polling current keyboard state.
The result describes this event's payload at the time it is read.

**Returns:** whether Super/Command/Windows was active

</details>

<a id="type-keypressevent"></a>

### KeyPressEvent

[Source](../../src/main/java/valthorne/event/events/KeyPressEvent.java#L13)

Keyboard press notification carrying a GLFW key code and modifier mask.
Uses the dedicated KEY_PRESS event route and inherits mutable event-consumption
state. The payload represents a key transition; Unicode text entry is handled
by TextInputEvent rather than inferred from this key code.

<details>
<summary>KeyPressEvent operation reference (1 declarations)</summary>

#### Constructor

```java
public KeyPressEvent(int key, int modifiers)
```

Creates a press notification with the corresponding numeric event type.
Key and modifier values are retained without querying current keyboard state.

- **`key`** — GLFW key code
- **`modifiers`** — GLFW modifier bit mask

</details>

<a id="type-keyreleaseevent"></a>

### KeyReleaseEvent

[Source](../../src/main/java/valthorne/event/events/KeyReleaseEvent.java#L13)

Keyboard release notification carrying a GLFW key code and modifier mask.
Uses the dedicated KEY_RELEASE event route and inherits mutable event-consumption
state. The payload represents a key transition; Unicode text entry is handled
by TextInputEvent rather than inferred from this key code.

<details>
<summary>KeyReleaseEvent operation reference (1 declarations)</summary>

#### Constructor

```java
public KeyReleaseEvent(int key, int modifiers)
```

Creates a release notification with the corresponding numeric event type.
Key and modifier values are retained without querying current keyboard state.

- **`key`** — GLFW key code
- **`modifiers`** — GLFW modifier bit mask

</details>

<a id="type-mousedragevent"></a>

### MouseDragEvent

[Source](../../src/main/java/valthorne/event/events/MouseDragEvent.java#L20)

Mouse movement event emitted while a button is being dragged.

Although this class extends `MouseMoveEvent` to reuse movement payload fields, it has its
own numeric route (`EventTypes#MOUSE_DRAG`). No superclass traversal is required during
publication.

Deltas are computed from the current payload rather than accumulated input.
Ending coordinates inherit signed-short storage, so their range differs from
the integer starting coordinates. Do not retain reusable events as snapshots.

<details>
<summary>MouseDragEvent operation reference (3 declarations)</summary>

#### Constructor

```java
public MouseDragEvent(int button, int modifiers, int fromX, int fromY, int toX, int toY)
```

Creates a reusable notification on its concrete numeric route. Supplied payload
values are stored using the field types without additional range validation.

- **`button`** — dragged mouse button
- **`modifiers`** — modifier bit mask
- **`fromX`** — starting X coordinate
- **`fromY`** — starting Y coordinate
- **`toX`** — ending X coordinate
- **`toY`** — ending Y coordinate

#### getDeltaX

```java
public int getDeltaX()
```

Computes the current horizontal displacement as ending minus starting coordinate.
No input is accumulated or normalized; signed-short endpoint storage can affect the result.

**Returns:** horizontal movement delta

#### getDeltaY

```java
public int getDeltaY()
```

Computes the current vertical displacement as ending minus starting coordinate.
No input is accumulated or normalized; signed-short endpoint storage can affect the result.

**Returns:** vertical movement delta

</details>

<a id="type-mouseevent"></a>

### MouseEvent

[Source](../../src/main/java/valthorne/event/events/MouseEvent.java#L33)

Shared payload superclass for mouse-related events.

Java inheritance here is strictly for payload/API reuse; publication routing is controlled by
the numeric `EventType` passed to `Event`. A directly-created `MouseEvent`
routes through `EventTypes#MOUSE`, while subclasses use their own concrete descriptors.

The payload is mutable to allow object reuse. An instance must have exclusive ownership while it
is being published.

Button codes and modifier masks are narrowed to bytes without validation; X/Y
remain integers in the producer's cursor coordinate space. Setters change payload
only and preserve the event's route and consumption state.

<details>
<summary>MouseEvent operation reference (14 declarations)</summary>

#### Constructor

```java
public MouseEvent(int button, int modifiers, int x, int y)
```

Creates a raw mouse event routed as `EventTypes#MOUSE`.

- **`button`** — mouse button code
- **`modifiers`** — modifier bit mask
- **`x`** — cursor X coordinate
- **`y`** — cursor Y coordinate

#### Constructor

```java
protected MouseEvent(EventType<?> type, int button, int modifiers, int x, int y)
```

Constructor used by subclasses to choose a concrete numeric route.

- **`type`** — concrete event type
- **`button`** — mouse button code
- **`modifiers`** — modifier bit mask
- **`x`** — cursor X coordinate
- **`y`** — cursor Y coordinate

#### set

```java
public MouseEvent set(int button, int modifiers, int x, int y)
```

Replaces the complete base mouse payload for reuse without resetting consumption.
Button and modifier values are narrowed to bytes; coordinates remain integers.

- **`button`** — mouse button code
- **`modifiers`** — modifier mask
- **`x`** — cursor X coordinate
- **`y`** — cursor Y coordinate

**Returns:** this event

#### setModifiers

```java
public void setModifiers(int modifiers)
```

Replaces this payload component without changing other values, the numeric route,
or consumption state. Storage uses the declared field type without range validation.

- **`modifiers`** — modifier bit mask

#### getX

```java
public int getX()
```

Returns the current stored payload component without querying native input or
window state. Copy the value if it is needed after this reusable event is updated.

**Returns:** cursor X coordinate

#### setX

```java
public void setX(int x)
```

Replaces this payload component without changing other values, the numeric route,
or consumption state. Storage uses the declared field type without range validation.

- **`x`** — new cursor X coordinate

#### getY

```java
public int getY()
```

Returns the current stored payload component without querying native input or
window state. Copy the value if it is needed after this reusable event is updated.

**Returns:** cursor Y coordinate

#### setY

```java
public void setY(int y)
```

Replaces this payload component without changing other values, the numeric route,
or consumption state. Storage uses the declared field type without range validation.

- **`y`** — new cursor Y coordinate

#### getButton

```java
public int getButton()
```

Returns the current stored payload component without querying native input or
window state. Copy the value if it is needed after this reusable event is updated.

**Returns:** mouse button code

#### setButton

```java
public void setButton(int button)
```

Replaces this payload component without changing other values, the numeric route,
or consumption state. Storage uses the declared field type without range validation.

- **`button`** — mouse button code

#### isShiftDown

```java
public boolean isShiftDown()
```

Tests the stored modifier bit without polling current keyboard state.
The result describes this event's payload at the time it is read.

**Returns:** whether Shift was active

#### isCtrlDown

```java
public boolean isCtrlDown()
```

Tests the stored modifier bit without polling current keyboard state.
The result describes this event's payload at the time it is read.

**Returns:** whether Control was active

#### isAltDown

```java
public boolean isAltDown()
```

Tests the stored modifier bit without polling current keyboard state.
The result describes this event's payload at the time it is read.

**Returns:** whether Alt was active

#### isSuperDown

```java
public boolean isSuperDown()
```

Tests the stored modifier bit without polling current keyboard state.
The result describes this event's payload at the time it is read.

**Returns:** whether Super/Command/Windows was active

</details>

<a id="type-mousemoveevent"></a>

### MouseMoveEvent

[Source](../../src/main/java/valthorne/event/events/MouseMoveEvent.java#L22)

Mouse movement event containing starting and ending cursor coordinates.

A normal instance routes through `EventTypes#MOUSE_MOVE`. The protected constructor allows
`MouseDragEvent` to reuse this payload implementation while selecting
`EventTypes#MOUSE_DRAG` as its actual route.

Starting coordinates use the inherited integer X/Y fields; ending coordinates
are stored as signed shorts, so larger input values wrap during construction or
set. This object is reusable and must not be mutated during publication.

<details>
<summary>MouseMoveEvent operation reference (7 declarations)</summary>

#### Constructor

```java
public MouseMoveEvent(int button, int modifiers, int fromX, int fromY, int toX, int toY)
```

Creates a normal mouse-move event.

- **`button`** — mouse button state/code associated with the move
- **`modifiers`** — modifier bit mask
- **`fromX`** — starting X coordinate
- **`fromY`** — starting Y coordinate
- **`toX`** — ending X coordinate
- **`toY`** — ending Y coordinate

#### Constructor

```java
protected MouseMoveEvent(EventType<?> type, int button, int modifiers, int fromX, int fromY, int toX, int toY)
```

Stores the start point in inherited integer fields and the end point in signed
shorts while selecting the subclass's numeric route. No range checks occur.

- **`type`** — concrete event route
- **`button`** — mouse button code
- **`modifiers`** — modifier mask
- **`fromX`** — starting cursor X
- **`fromY`** — starting cursor Y
- **`toX`** — ending cursor X, narrowed to a short
- **`toY`** — ending cursor Y, narrowed to a short

#### set

```java
public MouseMoveEvent set(int button, int modifiers, int fromX, int fromY, int toX, int toY)
```

Replaces the complete movement payload without changing route or consumption.
End coordinates are narrowed to signed shorts; start coordinates remain integers.

- **`button`** — mouse button code
- **`modifiers`** — modifier mask
- **`fromX`** — starting cursor X
- **`fromY`** — starting cursor Y
- **`toX`** — ending cursor X, narrowed to a short
- **`toY`** — ending cursor Y, narrowed to a short

**Returns:** this event

#### getToX

```java
public int getToX()
```

Returns the current stored payload component without querying native input or
window state. Copy the value if it is needed after this reusable event is updated.

**Returns:** ending X coordinate

#### setToX

```java
public void setToX(short toX)
```

Replaces this payload component without changing other values, the numeric route,
or consumption state. Storage uses the declared field type without range validation.

- **`toX`** — ending X coordinate

#### getToY

```java
public int getToY()
```

Returns the current stored payload component without querying native input or
window state. Copy the value if it is needed after this reusable event is updated.

**Returns:** ending Y coordinate

#### setToY

```java
public void setToY(short toY)
```

Replaces this payload component without changing other values, the numeric route,
or consumption state. Storage uses the declared field type without range validation.

- **`toY`** — ending Y coordinate

</details>

<a id="type-mousepressevent"></a>

### MousePressEvent

[Source](../../src/main/java/valthorne/event/events/MousePressEvent.java#L13)

Mouse-button press notification carrying button, modifier, and cursor values.
Uses the dedicated MOUSE_PRESS route and inherited event-consumption state.
Coordinates are captured by the producer; construction does not convert them
to a UI node's local coordinates or query the current cursor.

<details>
<summary>MousePressEvent operation reference (1 declarations)</summary>

#### Constructor

```java
public MousePressEvent(int button, int modifiers, int x, int y)
```

Creates a button press notification from the supplied cursor snapshot.
The values are retained without validation or coordinate conversion.

- **`button`** — GLFW mouse-button code
- **`modifiers`** — modifier bit mask
- **`x`** — cursor horizontal coordinate supplied by the producer
- **`y`** — cursor vertical coordinate supplied by the producer

</details>

<a id="type-mousereleaseevent"></a>

### MouseReleaseEvent

[Source](../../src/main/java/valthorne/event/events/MouseReleaseEvent.java#L13)

Mouse-button release notification carrying button, modifier, and cursor values.
Uses the dedicated MOUSE_RELEASE route and inherited event-consumption state.
Coordinates are captured by the producer; construction does not convert them
to a UI node's local coordinates or query the current cursor.

<details>
<summary>MouseReleaseEvent operation reference (1 declarations)</summary>

#### Constructor

```java
public MouseReleaseEvent(int button, int modifiers, int x, int y)
```

Creates a button release notification from the supplied cursor snapshot.
The values are retained without validation or coordinate conversion.

- **`button`** — GLFW mouse-button code
- **`modifiers`** — modifier bit mask
- **`x`** — cursor horizontal coordinate supplied by the producer
- **`y`** — cursor vertical coordinate supplied by the producer

</details>

<a id="type-mousescrollevent"></a>

### MouseScrollEvent

[Source](../../src/main/java/valthorne/event/events/MouseScrollEvent.java#L30)

Mouse scroll notification containing horizontal and vertical input deltas.
Offsets describe one scroll callback, not cursor coordinates, accumulated
displacement or a prescribed pixel distance. Consumers choose how to map
them to scrolling or zooming. The mouse producer supplies floating-point
offsets so fractional trackpad input remains available to precise accessors.

The event is routed directly through `EventTypes#MOUSE_SCROLL`. Payload fields remain
mutable so one exclusively-owned object may be reused to reduce allocation pressure.
The mouse producer reuses this event between publications; listeners that need
a lasting value should copy the numeric offsets instead of retaining the event.
Do not mutate or publish the same instance concurrently. Payload setters do
not reset consumption; the event publisher handles that before dispatch.

Integer accessors truncate fractional offsets toward zero. The legacy
integer setters first narrow to signed 16-bit values, whereas construction
converts its integers directly to floats. Use `setPreciseOffsets(float, float)`
to preserve fractional values and avoid that legacy narrowing.

<details>
<summary>MouseScrollEvent operation reference (9 declarations)</summary>

#### Constructor

```java
public MouseScrollEvent(int xOffset, int yOffset)
```

Creates an event on the mouse-scroll route with the supplied integer
deltas converted directly to floats. Large integers can lose float
precision; unlike legacy setters, construction does not narrow to short.

- **`xOffset`** — the initial horizontal scroll delta
- **`yOffset`** — the initial vertical scroll delta

#### set

```java
public MouseScrollEvent set(int xOffset, int yOffset)
```

Replaces both offsets using the legacy integer conversion. Each input is
narrowed to a signed short before storage, so values outside that range
wrap rather than clamp. Routing and consumption state are unchanged.

- **`xOffset`** — the horizontal delta to narrow and store
- **`yOffset`** — the vertical delta to narrow and store

**Returns:** this event for reuse or chaining

#### setXOffset

```java
public void setXOffset(int xOffset)
```

Replaces only the horizontal delta after narrowing it to a signed short.
Out-of-range values wrap; the vertical delta and event state are retained.

- **`xOffset`** — the horizontal scroll delta to narrow and store

#### setYOffset

```java
public void setYOffset(int yOffset)
```

Replaces only the vertical delta after narrowing it to a signed short.
Out-of-range values wrap; the horizontal delta and event state are retained.

- **`yOffset`** — the vertical scroll delta to narrow and store

#### setPreciseOffsets

```java
public MouseScrollEvent setPreciseOffsets(float x, float y)
```

Stores both finite deltas directly, preserving fractional trackpad input
without short narrowing. Both values are validated before either field
changes; a rejected update leaves the previous payload intact. This method
does not publish the event or reset its consumed state.

- **`x`** — the finite horizontal scroll delta
- **`y`** — the finite vertical scroll delta

**Returns:** this event for reuse or chaining

**Throws `IllegalArgumentException`:** if either delta is NaN or infinite

#### preciseXOffset

```java
public float preciseXOffset()
```

Reads the stored horizontal delta without integer conversion or mutation.
Fractional precision depends on the producer and the setter it used.

**Returns:** the horizontal scroll delta as a float

#### preciseYOffset

```java
public float preciseYOffset()
```

Reads the stored vertical delta without integer conversion or mutation.
Prefer this accessor when fractional input should affect scroll or zoom.

**Returns:** the vertical scroll delta as a float

#### xOffset

```java
public int xOffset()
```

Provides the integer compatibility view of the horizontal delta. Java's
float-to-int conversion truncates toward zero and saturates values outside
the integer range; reading does not discard the stored fractional value.

**Returns:** the horizontal scroll delta converted to an integer

#### yOffset

```java
public int yOffset()
```

Provides the integer compatibility view of the vertical delta. Java's
float-to-int conversion truncates toward zero and saturates values outside
the integer range; use the precise accessor for deltas smaller than one.

**Returns:** the vertical scroll delta converted to an integer

</details>

<a id="type-textinputevent"></a>

### TextInputEvent

[Source](../../src/main/java/valthorne/event/events/TextInputEvent.java#L19)

Carries committed text on the `EventTypes#TEXT_INPUT` route.
The keyboard's native character callback creates a string from each received
Unicode code point; callers may also supply a multi-character string. Text
insertion is separate from physical key commands such as navigation or shortcuts.

The payload is retained unchanged: empty strings are allowed, and this
class performs no normalization or Unicode validation. The string is immutable,
while inherited event-consumption state remains mutable and dispatch-local.
Do not publish the same event instance concurrently.

<details>
<summary>TextInputEvent operation reference (2 declarations)</summary>

#### Constructor

```java
public TextInputEvent(String text)
```

Creates a text-input event without dispatching it. The supplied immutable
string is retained by reference and may contain multiple code points.

- **`text`** — the committed text to deliver

**Throws `NullPointerException`:** if text is null

#### getText

```java
public String getText()
```

Returns the original text payload without trimming or normalization.
Its length counts UTF-16 code units and may differ from its code-point count.

**Returns:** the nonnull committed string supplied at construction

</details>

<a id="type-windowfocusevent"></a>

### WindowFocusEvent

[Source](../../src/main/java/valthorne/event/events/WindowFocusEvent.java#L18)

Reports a native window focus transition on `EventTypes#WINDOW_FOCUS`.
The payload describes whether the window gained or lost focus, rather than
which UI node owns keyboard focus. UI roots use focus loss to cancel input
state that could otherwise remain captured after switching applications.

Constructing this event does not activate a window or change UI focus.
The boolean payload is fixed, while inherited consumption state belongs to
the current dispatch. A single instance must not be published concurrently.

<details>
<summary>WindowFocusEvent operation reference (2 declarations)</summary>

#### Constructor

```java
public WindowFocusEvent(boolean focused)
```

Creates a focus notification without publishing it or modifying window
state. The value is a snapshot of the reported native transition.

- **`focused`** — true if the window gained focus, false if it lost focus

#### isFocused

```java
public boolean isFocused()
```

Returns the focus state captured by this notification. This does not
query the window's current state, which may have changed since publication.

**Returns:** whether this notification reports native window focus gain

</details>

<a id="type-windowresizeevent"></a>

### WindowResizeEvent

[Source](../../src/main/java/valthorne/event/events/WindowResizeEvent.java#L22)

Event emitted when a window changes dimensions.

The four integer dimensions are intentionally stored directly in the reusable event object.
The event routes through `EventTypes#WINDOW_RESIZE` using the same constant-time numeric
dispatch mechanism as all other events.

Dimensions are retained without validation, including zero or negative values
supplied by a producer. Payload changes do not resize a native window or publish
a notification; publication and exclusive ownership belong to the producer.

<details>
<summary>WindowResizeEvent operation reference (10 declarations)</summary>

#### Constructor

```java
public WindowResizeEvent(int oldWidth, int oldHeight, int newWidth, int newHeight)
```

Creates a reusable notification on its concrete numeric route. Supplied payload
values are stored using the field types without additional range validation.

- **`oldWidth`** — previous window width
- **`oldHeight`** — previous window height
- **`newWidth`** — new window width
- **`newHeight`** — new window height

#### set

```java
public WindowResizeEvent set(int oldWidth, int oldHeight, int newWidth, int newHeight)
```

Replaces all four dimensions without changing route or consumption state.
Does not change the native window or dispatch an event.

- **`oldWidth`** — previous width
- **`oldHeight`** — previous height
- **`newWidth`** — replacement width
- **`newHeight`** — replacement height

**Returns:** this event

#### getOldWidth

```java
public int getOldWidth()
```

Returns the current stored payload component without querying native input or
window state. Copy the value if it is needed after this reusable event is updated.

**Returns:** width before resize

#### setOldWidth

```java
public void setOldWidth(int oldWidth)
```

Replaces this payload component without changing other values, the numeric route,
or consumption state. Storage uses the declared field type without range validation.

- **`oldWidth`** — width before resize

#### getOldHeight

```java
public int getOldHeight()
```

Returns the current stored payload component without querying native input or
window state. Copy the value if it is needed after this reusable event is updated.

**Returns:** height before resize

#### setOldHeight

```java
public void setOldHeight(int oldHeight)
```

Replaces this payload component without changing other values, the numeric route,
or consumption state. Storage uses the declared field type without range validation.

- **`oldHeight`** — height before resize

#### getNewWidth

```java
public int getNewWidth()
```

Returns the current stored payload component without querying native input or
window state. Copy the value if it is needed after this reusable event is updated.

**Returns:** width after resize

#### setNewWidth

```java
public void setNewWidth(int newWidth)
```

Replaces this payload component without changing other values, the numeric route,
or consumption state. Storage uses the declared field type without range validation.

- **`newWidth`** — width after resize

#### getNewHeight

```java
public int getNewHeight()
```

Returns the current stored payload component without querying native input or
window state. Copy the value if it is needed after this reusable event is updated.

**Returns:** height after resize

#### setNewHeight

```java
public void setNewHeight(int newHeight)
```

Replaces this payload component without changing other values, the numeric route,
or consumption state. Storage uses the declared field type without range validation.

- **`newHeight`** — height after resize

</details>

<a id="type-eventtype"></a>

### EventType

[Source](../../src/main/java/valthorne/event/EventType.java#L32)

Immutable, strongly typed descriptor for one numeric event route.

The old event system used `Class<? extends Event>` keys. This replacement moves routing
information into a dense integer ID so publication can use direct array indexing. The generic
parameter preserves compile-time type safety when registering `EventHandler`s.

##### ID rules

- IDs must be non-negative.

- IDs should be dense and zero-based for cache- and memory-efficient routing tables.

- An ID used with a publisher must be lower than the publisher's configured type count.

- IDs should remain stable if other code persists or otherwise depends on them.

This class intentionally does not maintain a global registry or allocate IDs dynamically.
`EventTypes` is the explicit source of truth for this package.

- **`<E>`** — concrete event type represented by this descriptor

<details>
<summary>EventType operation reference (4 declarations)</summary>

#### Constructor

```java
public EventType(int id, String name)
```

Creates an event type descriptor.

- **`id`** — zero-based numeric routing ID
- **`name`** — stable human-readable diagnostic name

**Throws `IllegalArgumentException`:** if `id` is negative

**Throws `NullPointerException`:** if `name` is `null`

#### id

```java
    public int id()
```

Returns the numeric route ID.

**Returns:** event route index

#### name

```java
    public String name()
```

Returns the diagnostic name.

**Returns:** stable event type name

#### toString

```java
    public String toString()
```

Returns a compact diagnostic representation such as `key-press[1]`.

**Returns:** type name and ID

</details>

<a id="type-eventtypes"></a>

### EventTypes

[Source](../../src/main/java/valthorne/event/EventTypes.java#L43)

Central registry of every built-in event route used by the Valthorne event system.

Numeric IDs replace class-keyed routing. IDs are intentionally explicit, unique, dense, and
stable. Do not derive these values from enum ordinals, class hash codes, class names, or runtime
registration order.

##### Inheritance is not routing

Java inheritance remains useful for sharing payload fields and APIs, but the publisher no longer
walks superclasses. For example, `KeyPressEvent` extends `KeyEvent`, yet a key-press
publication uses only `KEY_PRESS`. A listener that wants both key press and key release
explicitly registers for both routes. The specialized listener interfaces in
`valthorne.event.listeners` provide convenience methods for exactly that purpose.

##### Adding a new event type

- Assign the next unused integer ID.

- Add an `EventType` constant here.

- Increment `COUNT`.

- Pass that constant to the concrete event's `Event` constructor chain.

```java
EventHandler<KeyPressEvent> listener = event -> {
    System.out.println(event.getKey());
};
valthorne.JGL.subscribe(EventTypes.KEY_PRESS, listener);
// Remove the same listener when its owner no longer needs input.
valthorne.JGL.unsubscribe(EventTypes.KEY_PRESS, listener);
```

<details>
<summary>EventTypes operation reference (14 declarations)</summary>

#### KEY

```java
public static final  EventType<KeyEvent> KEY
```

Route for explicit base KeyEvent publications. Key press and release events
do not also dispatch through this route merely because they inherit KeyEvent.

#### KEY_PRESS

```java
public static final  EventType<KeyPressEvent> KEY_PRESS
```

Key-press route used for physical key commands. Text entry is delivered
separately through TEXT_INPUT rather than inferred from key codes.

#### KEY_RELEASE

```java
public static final  EventType<KeyReleaseEvent> KEY_RELEASE
```

Key-release route, independent of both the raw key and key-press routes.

#### MOUSE

```java
public static final  EventType<MouseEvent> MOUSE
```

Route for explicit base MouseEvent publications. Specialized mouse events
use their own routes and do not automatically notify this one.

#### MOUSE_MOVE

```java
public static final  EventType<MouseMoveEvent> MOUSE_MOVE
```

Mouse-position movement route carrying the producer's movement payload.
Drag notifications have a separate route.

#### MOUSE_DRAG

```java
public static final  EventType<MouseDragEvent> MOUSE_DRAG
```

Mouse-drag route, allowing drag listeners to subscribe independently of
ordinary motion or button events.

#### MOUSE_PRESS

```java
public static final  EventType<MousePressEvent> MOUSE_PRESS
```

Mouse-button press route used by input handlers and UI press routing.

#### MOUSE_RELEASE

```java
public static final  EventType<MouseReleaseEvent> MOUSE_RELEASE
```

Mouse-button release route used by input handlers and UI release routing.

#### MOUSE_SCROLL

```java
public static final  EventType<MouseScrollEvent> MOUSE_SCROLL
```

Wheel and trackpad scroll route. MouseScrollEvent exposes precise fractional
deltas as well as integer compatibility accessors.

#### WINDOW_RESIZE

```java
public static final  EventType<WindowResizeEvent> WINDOW_RESIZE
```

Window dimension-change route used to notify viewport and UI sizing logic.

#### THEME_DATA_CHANGE

```java
public static final  EventType<ThemeDataChangeEvent> THEME_DATA_CHANGE
```

Global theme mutation route for token, resource and registered rule-map
notifications. Listeners inspect the event payload to identify the theme.

#### COUNT

```java
public static final  int COUNT
```

Number of slots needed for built-in route IDs zero through twelve. This
exclusive upper bound must be updated when a new built-in ID is added.

#### WINDOW_FOCUS

```java
public static final  EventType<WindowFocusEvent> WINDOW_FOCUS
```

Window focus-change route used to distinguish focus gain and loss, including
input cleanup when the window loses focus.

#### TEXT_INPUT

```java
public static final  EventType<TextInputEvent> TEXT_INPUT
```

Committed-text route carrying a string. The native character callback
supplies one Unicode code point per event, while other producers may supply
longer strings. This is separate from physical key commands.

</details>

<a id="type-keyadapter"></a>

### KeyAdapter

[Source](../../src/main/java/valthorne/event/listeners/KeyAdapter.java#L18)

Adapter with no-op key callbacks so subclasses override only the events they need.

Register an adapter instance through `KeyListener#register(valthorne.event.EventPublisher)`
or its priority overload. That helper explicitly attaches the instance to the key-press and
key-release numeric routes.

<details>
<summary>KeyAdapter operation reference (2 declarations)</summary>

#### keyPressed

```java
    public void keyPressed(KeyPressEvent event)
```

Default no-op key-press callback.
This implementation does not inspect, retain, or consume the event. Override
this callback to handle the notification while leaving other callbacks inactive.

- **`event`** — notification ignored by this default implementation

#### keyReleased

```java
    public void keyReleased(KeyReleaseEvent event)
```

Default no-op key-release callback.
This implementation does not inspect, retain, or consume the event. Override
this callback to handle the notification while leaving other callbacks inactive.

- **`event`** — notification ignored by this default implementation

</details>

<a id="type-keylistener"></a>

### KeyListener

[Source](../../src/main/java/valthorne/event/listeners/KeyListener.java#L30)

Convenience handler for both key-press and key-release routes.

The old publisher delivered subclass events to a listener registered for `KeyEvent.class`
by walking the event's superclass chain. The high-throughput publisher intentionally does not do
that. This interface therefore provides `register(EventPublisher, int)` and
`unregister(EventPublisher)` helpers that explicitly attach the same handler to the two
concrete numeric routes.

Once registered, the publisher still performs only one route-array lookup per publication.
The small subtype switch below is executed only by this convenience handler, not by every event
in the global dispatcher.

<details>
<summary>KeyListener operation reference (6 declarations)</summary>

#### handle

```java
    default void handle(KeyEvent event)
```

Dispatches the concrete key event to its specialized callback.
The callback executes immediately and receives the original event reference.
This entry point neither copies the event nor consumes it automatically.

- **`event`** — concrete event supported by this listener

**Throws `IllegalStateException`:** if the event has an unsupported subtype

**Throws `NullPointerException`:** if event is null

#### register

```java
default void register(EventPublisher publisher)
```

Registers this listener for key press and key release at normal priority `0`.

- **`publisher`** — target publisher

#### register

```java
default void register(EventPublisher publisher, int priority)
```

Registers this exact listener instance for both concrete key routes.

- **`publisher`** — target publisher
- **`priority`** — execution priority; larger values run first

#### unregister

```java
default boolean unregister(EventPublisher publisher)
```

Removes this listener from both concrete key routes.

- **`publisher`** — target publisher

**Returns:** `true` if at least one route registration was removed

#### keyPressed

```java
void keyPressed(KeyPressEvent event)
```

Receives the key press event. The event is shared with the publisher's
dispatch; consume it explicitly if later listeners should not receive it.
Implementations run synchronously and should copy needed values before retaining
information from reusable events.

- **`event`** — key press event

#### keyReleased

```java
void keyReleased(KeyReleaseEvent event)
```

Receives the key release event. The event is shared with the publisher's
dispatch; consume it explicitly if later listeners should not receive it.
Implementations run synchronously and should copy needed values before retaining
information from reusable events.

- **`event`** — key release event

</details>

<a id="type-mouseadapter"></a>

### MouseAdapter

[Source](../../src/main/java/valthorne/event/listeners/MouseAdapter.java#L19)

Adapter with no-op implementations for the four callbacks in `MouseListener`.

Subclasses may override only the actions they need, then register the adapter through
`MouseListener#register(valthorne.event.EventPublisher)` or the priority overload.

<details>
<summary>MouseAdapter operation reference (4 declarations)</summary>

#### mousePressed

```java
    public void mousePressed(MousePressEvent event)
```

Default no-op press callback.
This implementation does not inspect, retain, or consume the event. Override
this callback to handle the notification while leaving other callbacks inactive.

- **`event`** — notification ignored by this default implementation

#### mouseReleased

```java
    public void mouseReleased(MouseReleaseEvent event)
```

Default no-op release callback.
This implementation does not inspect, retain, or consume the event. Override
this callback to handle the notification while leaving other callbacks inactive.

- **`event`** — notification ignored by this default implementation

#### mouseDragged

```java
    public void mouseDragged(MouseDragEvent event)
```

Default no-op drag callback.
This implementation does not inspect, retain, or consume the event. Override
this callback to handle the notification while leaving other callbacks inactive.

- **`event`** — notification ignored by this default implementation

#### mouseMoved

```java
    public void mouseMoved(MouseMoveEvent event)
```

Default no-op move callback.
This implementation does not inspect, retain, or consume the event. Override
this callback to handle the notification while leaving other callbacks inactive.

- **`event`** — notification ignored by this default implementation

</details>

<a id="type-mouselistener"></a>

### MouseListener

[Source](../../src/main/java/valthorne/event/listeners/MouseListener.java#L26)

Convenience handler spanning the four primary mouse action routes.

Superclass-based routing has been removed from `EventPublisher`. Calling
`register(EventPublisher, int)` explicitly registers this same handler instance for
`EventTypes#MOUSE_MOVE`, `EventTypes#MOUSE_DRAG`, `EventTypes#MOUSE_PRESS`,
and `EventTypes#MOUSE_RELEASE`. Publication itself remains a single numeric array lookup.

Scroll events intentionally remain separate and are handled by `MouseScrollListener`,
matching the structure of the original API.

<details>
<summary>MouseListener operation reference (8 declarations)</summary>

#### handle

```java
    default void handle(MouseEvent event)
```

Dispatches a routed concrete mouse event to its specialized callback.
The callback executes immediately and receives the original event reference.
This entry point neither copies the event nor consumes it automatically.

- **`event`** — concrete event supported by this listener

**Throws `IllegalStateException`:** if the event has an unsupported subtype

**Throws `NullPointerException`:** if event is null

#### register

```java
default void register(EventPublisher publisher)
```

Registers all four supported mouse routes at priority `0`.
Registration retains this listener instance on the corresponding numeric route
or routes; call unregister when the listener's owner no longer needs delivery.

- **`publisher`** — publisher receiving the registration

**Throws `NullPointerException`:** if publisher is null

#### register

```java
default void register(EventPublisher publisher, int priority)
```

Registers all four concrete mouse routes with one explicit priority.

- **`publisher`** — target publisher
- **`priority`** — execution priority; larger values run first

#### unregister

```java
default boolean unregister(EventPublisher publisher)
```

Removes this listener from all four routes. Each route is checked even if
an earlier removal succeeds; registrations on other routes are preserved.

- **`publisher`** — publisher from which to detach

**Returns:** `true` if at least one registration was removed

#### mousePressed

```java
void mousePressed(MousePressEvent event)
```

Receives the mouse-button press event. The event is shared with the publisher's
dispatch; consume it explicitly if later listeners should not receive it.
Implementations run synchronously and should copy needed values before retaining
information from reusable events.

- **`event`** — mouse-button press event

#### mouseReleased

```java
void mouseReleased(MouseReleaseEvent event)
```

Receives the mouse-button release event. The event is shared with the publisher's
dispatch; consume it explicitly if later listeners should not receive it.
Implementations run synchronously and should copy needed values before retaining
information from reusable events.

- **`event`** — mouse-button release event

#### mouseDragged

```java
void mouseDragged(MouseDragEvent event)
```

Receives the mouse drag event. The event is shared with the publisher's
dispatch; consume it explicitly if later listeners should not receive it.
Implementations run synchronously and should copy needed values before retaining
information from reusable events.

- **`event`** — mouse drag event

#### mouseMoved

```java
void mouseMoved(MouseMoveEvent event)
```

Receives the mouse movement event. The event is shared with the publisher's
dispatch; consume it explicitly if later listeners should not receive it.
Implementations run synchronously and should copy needed values before retaining
information from reusable events.

- **`event`** — mouse movement event

</details>

<a id="type-mousescrolllistener"></a>

### MouseScrollListener

[Source](../../src/main/java/valthorne/event/listeners/MouseScrollListener.java#L20)

Specialized high-throughput handler for `MouseScrollEvent`.

This interface is itself an `EventHandler`, so it may be registered directly with
`EventTypes#MOUSE_SCROLL`. Convenience registration methods are supplied for symmetry with
the multi-route listener interfaces.

<details>
<summary>MouseScrollListener operation reference (5 declarations)</summary>

#### handle

```java
    default void handle(MouseScrollEvent event)
```

Delegates the generic handler entry point to `mouseScrolled(MouseScrollEvent)`.
Passes the original reference synchronously without validation or automatic
consumption. Callback exceptions propagate to the caller.

- **`event`** — notification passed to the specialized callback

#### register

```java
default void register(EventPublisher publisher)
```

Registers this listener at priority `0`.
Registration retains this listener instance on the corresponding numeric route
or routes; call unregister when the listener's owner no longer needs delivery.

- **`publisher`** — publisher receiving the registration

**Throws `NullPointerException`:** if publisher is null

#### register

```java
default void register(EventPublisher publisher, int priority)
```

Registers this listener at an explicit priority.
Registration retains this listener instance on the corresponding numeric route
or routes; call unregister when the listener's owner no longer needs delivery.

- **`publisher`** — publisher receiving the registration
- **`priority`** — execution priority; larger values run first

**Throws `NullPointerException`:** if publisher is null

#### unregister

```java
default boolean unregister(EventPublisher publisher)
```

Removes this listener from the mouse-scroll route.
Only registrations for this listener instance on the supported route are affected.

- **`publisher`** — publisher from which to detach

**Returns:** true if a registration was removed; false if none existed

**Throws `NullPointerException`:** if publisher is null

#### mouseScrolled

```java
void mouseScrolled(MouseScrollEvent event)
```

Receives the mouse scroll event. The event is shared with the publisher's
dispatch; consume it explicitly if later listeners should not receive it.
Implementations run synchronously and should copy needed values before retaining
information from reusable events.

- **`event`** — mouse scroll event

</details>

<a id="type-windowresizelistener"></a>

### WindowResizeListener

[Source](../../src/main/java/valthorne/event/listeners/WindowResizeListener.java#L19)

Specialized high-throughput handler for `WindowResizeEvent`.

The listener registers directly against `EventTypes#WINDOW_RESIZE`; no reflective method
inspection or class-based listener lookup is involved.

<details>
<summary>WindowResizeListener operation reference (5 declarations)</summary>

#### handle

```java
    default void handle(WindowResizeEvent event)
```

Delegates the handler entry point to `windowResized(WindowResizeEvent)`.
Passes the original reference synchronously without validation or automatic
consumption. Callback exceptions propagate to the caller.

- **`event`** — notification passed to the specialized callback

#### register

```java
default void register(EventPublisher publisher)
```

Registers this listener at normal priority `0`.
Registration retains this listener instance on the corresponding numeric route
or routes; call unregister when the listener's owner no longer needs delivery.

- **`publisher`** — publisher receiving the registration

**Throws `NullPointerException`:** if publisher is null

#### register

```java
default void register(EventPublisher publisher, int priority)
```

Registers this listener at an explicit priority.
Registration retains this listener instance on the corresponding numeric route
or routes; call unregister when the listener's owner no longer needs delivery.

- **`publisher`** — publisher receiving the registration
- **`priority`** — execution priority; larger values run first

**Throws `NullPointerException`:** if publisher is null

#### unregister

```java
default boolean unregister(EventPublisher publisher)
```

Removes this listener from the window-resize route.
Only registrations for this listener instance on the supported route are affected.

- **`publisher`** — publisher from which to detach

**Returns:** true if a registration was removed; false if none existed

**Throws `NullPointerException`:** if publisher is null

#### windowResized

```java
void windowResized(WindowResizeEvent event)
```

Receives the completed window-resize event. The event is shared with the publisher's
dispatch; consume it explicitly if later listeners should not receive it.
Implementations run synchronously and should copy needed values before retaining
information from reusable events.

- **`event`** — completed window-resize event

</details>

## Related guides

- [Keyboard, mouse, and cursor input](input.md)
- [Scenes and game screens](scenes.md)
- [UI roots, nodes, and input routing](ui-core.md)
