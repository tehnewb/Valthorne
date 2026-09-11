# State machines and transitions

Author: Albert Beaupre

[System manual](README.md)

## Purpose

Use a state machine for behavior that has named modes and controlled transitions: idle/run/jump, menu states, or a multi-step interaction. The machine carries shared context, invokes state lifecycle callbacks, and evaluates guarded transitions. It is separate from scene replacement and does not manage graphics resources for you.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| States and context | A state receives enter/update/exit callbacks and shared application data. |
| Guards and conditions | Predicates decide whether a transition is currently eligible. |
| Triggers | Named or retained triggers represent a discrete event such as a jump request. |
| Priority and dwell time | Transitions can compete by priority and require a minimum time in the current state. |
| Transition actions | An action performs side effects at the selected state change. |

## Getting started

1. Define the context data and state objects before constructing the machine.
2. Register global transitions and state-specific transitions with guards and priorities.
3. Fire triggers from input or gameplay events.
4. Call `update(deltaSeconds)` from one simulation owner and keep rendering outside transition selection.

## Ownership and lifecycle

State callbacks execute synchronously. Keep resource ownership in your application or state lifecycle design, and avoid uncontrolled reentrant machine changes from callbacks. Context mutation must follow the same owning thread as evaluation.

## Important behavior

- An always-true high-priority global transition can dominate local transitions.
- Use dwell time to suppress rapid state oscillation, not to replace correct guards.
- Consult the detailed selection contract for equal priorities, trigger consumption, and forced transitions.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`Condition`](#type-condition)
- [`Guard`](#type-guard)
- [`State`](#type-state)
- [`StateContext`](#type-statecontext)
- [`StateMachine`](#type-statemachine)
- [`Transition`](#type-transition)
- [`TransitionAction`](#type-transitionaction)
- [`Trigger`](#type-trigger)

<a id="type-condition"></a>

### Condition

[Source](../../src/main/java/valthorne/state/Condition.java#L18)

A boolean condition used to decide transitions.

##### Example

```java
Condition isDead = () -> health <= 0;
if (isDead.test()) { ... }
```

This interface is a general-purpose predicate and may be used outside the generic FSM
(this FSM implementation primarily uses `Guard` + `Trigger`).

<details>
<summary>Condition operation reference (1 declarations)</summary>

#### test

```java
boolean test()
```

Evaluates the condition.

**Returns:** true if the condition is met

</details>

<a id="type-guard"></a>

### Guard

[Source](../../src/main/java/valthorne/state/Guard.java#L23)

A guard blocks transitions.

##### Example

```java
Guard<PlayerCtx> isGrounded = ctx -> ctx.data().grounded();

fsm.addTransition(idle, jump, 10, new Trigger("jump"), isGrounded, 0f, "jump", null);
```

Guards are separate from triggers:

- Triggers represent queued events (like "jumpPressed").

- Guards represent boolean checks (like "isGrounded").

- **`<C>`** — user-defined context type

<details>
<summary>Guard operation reference (1 declarations)</summary>

#### allow

```java
boolean allow(StateContext<C> ctx)
```

Evaluates whether a transition is allowed right now.

Return true to allow the transition to be considered valid.

Return false to block the transition.

- **`ctx`** — state context

**Returns:** true if allowed

</details>

<a id="type-state"></a>

### State

[Source](../../src/main/java/valthorne/state/State.java#L33)

A state in a finite state machine (FSM).

##### Example

```java
public final class IdleState implements State<PlayerCtx> {
    @Override
    public void onEnter(StateContext<PlayerCtx> ctx) {
        // Reset state-local timers, play enter animation, etc.
    }

    @Override
    public void onUpdate(StateContext<PlayerCtx> ctx, float dtSec) {
        // Run logic for this state.
        // Transition rules are handled by the StateMachine, not here.
    }

    @Override
    public void onExit(StateContext<PlayerCtx> ctx) {
        // Cleanup: stop sounds, clear flags, etc.
    }
}
```

States should be lightweight and typically stateless, with shared data stored in `StateContext#data()`.

- **`<C>`** — user-defined context type

<details>
<summary>State operation reference (3 declarations)</summary>

#### onEnter

```java
void onEnter(StateContext<C> ctx)
```

Called when the state becomes active.

This is called after a transition is taken and `StateContext#timeInStateSec()` has been reset to 0.

If the transition had an action, the action is executed before this method.

- **`ctx`** — state context (shared, reused)

#### onUpdate

```java
void onUpdate(StateContext<C> ctx, float dtSec)
```

Called every update while the state is active.

This is called once per `StateMachine#update(float)` call (as long as this state remains active
during the update).

- **`ctx`** — state context (shared, reused)
- **`dtSec`** — delta time in seconds (clamped >= 0 by the machine)

#### onExit

```java
void onExit(StateContext<C> ctx)
```

Called when the state stops being active.

This is called before the transition action (if any) and before entering the next state.

- **`ctx`** — state context (shared, reused)

</details>

<a id="type-statecontext"></a>

### StateContext

[Source](../../src/main/java/valthorne/state/StateContext.java#L42)

Context passed into states, guards, and transition actions.

##### Example

```java
// Your data model.
public final class PlayerData {
    public boolean grounded;
    public float vx;
}

State<PlayerData> run = new State<>() {
    @Override public void onEnter(StateContext<PlayerData> ctx) {
        // ctx.data() gives you your object.
        // ctx.timeInStateSec() starts at 0 on enter.
    }

    @Override public void onUpdate(StateContext<PlayerData> ctx, float dt) {
        float time = ctx.timeInStateSec();
        Transition<PlayerData> last = ctx.lastTransition();
    }

    @Override public void onExit(StateContext<PlayerData> ctx) {}
};
```

This object is owned by the `StateMachine` and is reused each update.

It provides:

- Access to your user data via `data()`.

- Current state via `currentState()`.

- Time spent in the current state via `timeInStateSec()`.

- Last transition taken via `lastTransition()` (includes reason).

- **`<C>`** — user-defined context type

<details>
<summary>StateContext operation reference (5 declarations)</summary>

#### machine

```java
public StateMachine<C> machine()
```

Returns the owning FSM.

Useful if a state wants to query current state, force changes, or fire triggers.

**Returns:** the state machine instance

#### data

```java
public C data()
```

Returns your shared user data object.

This is the primary mechanism to share state between states and guards.

**Returns:** user context data (may be null depending on your design)

#### currentState

```java
public State<C> currentState()
```

Returns the currently active state.

**Returns:** current state (may be null if machine is idle)

#### timeInStateSec

```java
public float timeInStateSec()
```

Returns how many seconds have elapsed since the current state was entered.

This resets to 0 when a transition is taken or when the initial state is set.

**Returns:** time in current state, seconds

#### lastTransition

```java
public Transition<C> lastTransition()
```

Returns the last transition taken.

This can be used to inspect `Transition#reason()` or other transition metadata.

**Returns:** last transition, or null if none taken yet

</details>

<a id="type-statemachine"></a>

### StateMachine

[Source](../../src/main/java/valthorne/state/StateMachine.java#L120)

A condition-driven finite state machine (FSM).

##### Example

```java
// Your shared data for states/guards/actions.
public record PlayerCtx(boolean grounded, float vx, boolean dead) {}

// Example states.
State<PlayerCtx> idle = new State<>() {
    @Override public void onEnter(StateContext<PlayerCtx> ctx) {}
    @Override public void onUpdate(StateContext<PlayerCtx> ctx, float dt) {}
    @Override public void onExit(StateContext<PlayerCtx> ctx) {}
};

State<PlayerCtx> run = new State<>() {
    @Override public void onEnter(StateContext<PlayerCtx> ctx) {}
    @Override public void onUpdate(StateContext<PlayerCtx> ctx, float dt) {}
    @Override public void onExit(StateContext<PlayerCtx> ctx) {}
};

State<PlayerCtx> dead = new State<>() {
    @Override public void onEnter(StateContext<PlayerCtx> ctx) {}
    @Override public void onUpdate(StateContext<PlayerCtx> ctx, float dt) {}
    @Override public void onExit(StateContext<PlayerCtx> ctx) {}
};

// Create the FSM with initial state.
StateMachine<PlayerCtx> fsm = new StateMachine<>(new PlayerCtx(true, 0f, false), idle);

// Global "any state -> dead" transition.
fsm.addGlobalTransition(
    dead,
    1000, // priority
    null, // trigger
    ctx -> ctx.data().dead(), // guard
    0f,   // min time in state
    "player died",
    (ctx, tr) -> {
        // Transition action: reset something, play sound, fire event, etc.
    }
);

// State-specific transitions.
fsm.addTransition(
    idle, run,
    10,
    null,
    ctx -> Math.abs(ctx.data().vx()) > 0.1f,
    0.05f, // debounce: must be in IDLE for 50ms before leaving
    "start moving",
    null
);

fsm.addTransition(
    run, idle,
    10,
    null,
    ctx -> Math.abs(ctx.data().vx()) <= 0.1f,
    0.05f,
    "stop moving",
    null
);

// Trigger-based transition (example: jump).
Trigger jump = new Trigger("jump");
State<PlayerCtx> jumpState = ...;

fsm.addTransition(
    idle, jumpState,
    50,
    jump,
    ctx -> ctx.data().grounded(),
    0f,
    "jump pressed",
    (ctx, tr) -> {
        // e.g. set vertical velocity
    }
);

// In your input handling:
// if (jumpPressed) fsm.fireTrigger("jump");

// In your game loop:
fsm.update(deltaSeconds);
```

##### How transition selection works

- Global transitions are evaluated first and compete with state-specific transitions by priority.

- Lists are pre-sorted by priority (descending), then insertion order (ascending).

- A transition is valid only if:

- `timeInStateSec >= minTimeInStateSec`

- guard is null or `guard.allow(ctx)` returns true

- requiredTrigger is null or its name exists in the trigger queue

- If a transition requires a trigger, the trigger is consumed when the transition is taken.

- By default, multiple transitions may occur per `update(float)` call (capped by `maxTransitionsPerUpdate`).

##### Transition lifecycle

- oldState.onExit(ctx)

- transition.action.run(ctx, transition) (if present)

- current is switched, `timeInStateSec` resets to 0

- newState.onEnter(ctx)

- **`<C>`** — user-defined context type

<details>
<summary>StateMachine operation reference (12 declarations)</summary>

#### Constructor

```java
public StateMachine(C userContext, State<C> initial)
```

Creates a new FSM and immediately enters the initial state (if non-null).

The provided `userContext` becomes available through `StateContext#data()`.

The initial enter reason is stored on `StateContext#lastTransition()` as `"initial"` (or your override).

- **`userContext`** — your context data object (may be null, depending on your design)
- **`initial`** — initial state (may be null)

#### setAllowMultipleTransitionsPerUpdate

```java
public StateMachine<C> setAllowMultipleTransitionsPerUpdate(boolean allow)
```

Enables or disables taking multiple transitions in a single `update(float)` call.

If enabled, the FSM will keep evaluating transitions after each state change until:
it finds no valid transition, reaches `maxTransitionsPerUpdate`, or this option is disabled.

- **`allow`** — true to allow multiple transitions per update

**Returns:** this for chaining

#### setMaxTransitionsPerUpdate

```java
public StateMachine<C> setMaxTransitionsPerUpdate(int max)
```

Sets the maximum number of transitions allowed in one `update(float)`.

This is a safety valve against conditions that form a cycle, e.g. A->B and B->A both valid.

- **`max`** — maximum transitions per update (minimum 1)

**Returns:** this for chaining

#### fireTrigger

```java
public void fireTrigger(String triggerName)
```

Fires a trigger (queued event) by name.

Triggers are stored as unique names. Firing the same trigger multiple times before it is consumed
will still result in only one queued instance.

A transition that requires this trigger will consume it when taken.

- **`triggerName`** — trigger name (ignored if null/blank)

#### clearTriggers

```java
public void clearTriggers()
```

Clears all queued triggers.

Use this if you want to guarantee that triggers do not carry across frames.

#### addTransition

```java
public StateMachine<C> addTransition(State<C> from, State<C> to, int priority, Trigger requiredTrigger, Guard<C> guard, float minTimeInStateSec, String reason, TransitionAction<C> action)
```

Adds a transition that is only considered when `from` is the current state.

Transition selection is priority-based:
higher priority wins; ties are resolved by insertion order.

Use `requiredTrigger` to require a queued event (consumed when taken).

Use `guard` to implement boolean logic checks against `StateContext`.

Use `minTimeInStateSec` as a debounce/cooldown (must remain in state for this many seconds).

Use `reason` to store a human-readable reason on `Transition#reason()`.

Use `action` to run custom logic during the transition.

- **`from`** — source state (must be non-null)
- **`to`** — target state (must be non-null)
- **`priority`** — priority (higher wins)
- **`requiredTrigger`** — required trigger (nullable)
- **`guard`** — guard predicate (nullable)
- **`minTimeInStateSec`** — debounce/cooldown in seconds (clamped to >= 0)
- **`reason`** — human-readable reason (nullable/blank becomes default)
- **`action`** — transition action (nullable)

**Returns:** this for chaining

#### addGlobalTransition

```java
public StateMachine<C> addGlobalTransition(State<C> to, int priority, Trigger requiredTrigger, Guard<C> guard, float minTimeInStateSec, String reason, TransitionAction<C> action)
```

Adds a global transition (any-state rule).

Global transitions are evaluated alongside the current state's transitions and compete by priority.

A global transition uses `from=null` inside `Transition#from()`.

- **`to`** — target state (must be non-null)
- **`priority`** — priority (higher wins)
- **`requiredTrigger`** — required trigger (nullable)
- **`guard`** — guard predicate (nullable)
- **`minTimeInStateSec`** — debounce/cooldown in seconds (clamped to >= 0)
- **`reason`** — human-readable reason (nullable/blank becomes default)
- **`action`** — transition action (nullable)

**Returns:** this for chaining

#### update

```java
public void update(float dtSec)
```

Updates the FSM by:

- Accumulating time in the current state

- Calling `current.onUpdate(ctx, dt)`

- Evaluating transitions and taking the best valid one

- Optionally repeating transition evaluation (multi-transition mode)

Trigger handling:

- If a transition requires a trigger, it must exist in the trigger queue.

- On take, that trigger name is removed from the queue.

- If the best transition requires a trigger but consumption fails, that transition is skipped
for this update and evaluation continues.

Safety:

- `dtSec` is clamped to `>= 0`.

- Transition chaining is capped by `maxTransitionsPerUpdate`.

- **`dtSec`** — delta time in seconds

#### getCurrentState

```java
public State<C> getCurrentState()
```

Returns the current active state.

**Returns:** current state (may be null)

#### getContext

```java
public StateContext<C> getContext()
```

Returns the live `StateContext` used for updates and transitions.

This context object is reused; do not store it as if it were immutable snapshot state.

**Returns:** the state context instance

#### changeState

```java
public void changeState(State<C> next, String reason)
```

Forces an immediate state change (bypasses guards, triggers, and cooldown checks).

This still performs the normal transition lifecycle:
oldState.onExit, optional action, switch current, reset timeInState, newState.onEnter.

The forced transition uses a very high priority and sets `Transition#reason()` to your provided reason
(or `"forced"` if null).

- **`next`** — next state (may be null)
- **`reason`** — reason string (nullable)

#### setInitialState

```java
public void setInitialState(State<C> initial, String reason)
```

Sets the initial state and calls `State#onEnter(StateContext)` immediately.

This clears:

- current state

- time in state

- last transition

If `initial` is null, the machine becomes idle (no current state).

- **`initial`** — initial state (nullable)
- **`reason`** — reason string used on the synthetic "initial" transition (nullable)

</details>

<a id="type-transition"></a>

### Transition

[Source](../../src/main/java/valthorne/state/Transition.java#L40)

A transition rule.

##### Example

```java
Trigger jump = new Trigger("jump");

Transition<PlayerCtx> t = new Transition<>(
    idle, jumpState,
    jump,
    ctx -> ctx.data().grounded(),
    0.05f,
    10,
    0L,
    "jump pressed",
    (ctx, tr) -> {  reset timers, play sound, etc.  }
);
```

Transitions can be:

- **State-specific**: `from()` is non-null and must match the current state.

- **Global**: `from()` is null and can apply from any state.

Ordering:

- Higher `priority()` wins.

- If priorities tie, lower `order()` (earlier insertion) wins.

- **`<C>`** — user-defined context type

<details>
<summary>Transition operation reference (9 declarations)</summary>

#### from

```java
public State<C> from()
```

Returns the source state.

If this is a global transition, this will be null.

**Returns:** source state, or null

#### to

```java
public State<C> to()
```

Returns the target state.

**Returns:** target state (never null)

#### requiredTrigger

```java
public Trigger requiredTrigger()
```

Returns the required trigger for this transition, if any.

If non-null, the trigger must be present in the state machine's trigger queue to be valid,
and will be consumed (removed) when the transition is taken.

**Returns:** required trigger, or null

#### guard

```java
public Guard<C> guard()
```

Returns the guard predicate, if any.

If non-null, it must return true for this transition to be valid.

**Returns:** guard, or null

#### minTimeInStateSec

```java
public float minTimeInStateSec()
```

Returns the minimum time required in the current state before this transition becomes eligible.

This is a debounce/cooldown mechanism to prevent rapid flipping between states.

**Returns:** minimum seconds in state (>= 0)

#### priority

```java
public int priority()
```

Returns the priority for this transition.

Higher values win when multiple transitions are valid.

**Returns:** priority

#### order

```java
public long order()
```

Returns the insertion order for tie-breaking.

Lower values indicate earlier insertion.

**Returns:** insertion order

#### reason

```java
public String reason()
```

Returns a human-readable reason associated with this transition.

This is useful for debugging, logs, or telemetry.

**Returns:** reason string (never blank)

#### action

```java
public TransitionAction<C> action()
```

Returns the transition action, if any.

Actions run after oldState.onExit and before newState.onEnter.

**Returns:** action or null

</details>

<a id="type-transitionaction"></a>

### TransitionAction

[Source](../../src/main/java/valthorne/state/TransitionAction.java#L29)

Runs when a transition is taken.

##### Example

```java
TransitionAction<PlayerCtx> resetJumpTimer = (ctx, tr) -> {
    ctx.data().lastJumpTime = 0f;
};

fsm.addTransition(idle, jump, 10, new Trigger("jump"), ctx -> ctx.data().grounded(), 0f, "jump", resetJumpTimer);
```

This is where you put side effects that must happen exactly when the transition occurs:
reset timers, play sounds, fire events, set animation, etc.

Execution order:

- oldState.onExit(ctx)

- action.run(ctx, transition)

- newState.onEnter(ctx)

- **`<C>`** — user-defined context type

<details>
<summary>TransitionAction operation reference (1 declarations)</summary>

#### run

```java
void run(StateContext<C> ctx, Transition<C> transition)
```

Called when the FSM takes a transition.

This runs after the old state exits and before the new state enters.

- **`ctx`** — state context
- **`transition`** — the transition being taken

</details>

<a id="type-trigger"></a>

### Trigger

[Source](../../src/main/java/valthorne/state/Trigger.java#L28)

A queued FSM event (trigger).

##### Example

```java
Trigger jump = new Trigger("jump");

// Transition requires "jump".
fsm.addTransition(idle, jumpState, 10, jump, ctx -> ctx.data().grounded(), 0f, "jump", null);

// Fire it from input:
if (jumpPressed) {
    fsm.fireTrigger("jump");
}
```

Triggers are stored on the machine as string names.

A transition can require a trigger, and the trigger is consumed when that transition is taken.

- **`name`** — trigger name

<details>
<summary>Trigger operation reference (1 declarations)</summary>

#### Constructor

```java
public Trigger
```

Creates a trigger.

Names must be non-null and non-blank.

- **`name`** — trigger name

**Throws `NullPointerException`:** if name is null

**Throws `IllegalArgumentException`:** if name is blank

</details>

## Related guides

- [Events and listeners](events.md)
- [Ticks and frame timing](timing.md)
- [Scenes and game screens](scenes.md)
