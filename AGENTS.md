# AGENTS.md

This document defines mandatory development, documentation, architecture, performance, formatting, and code-organization rules for agents working on this project.

These rules apply across the entire codebase unless a rule is genuinely impossible to follow because of a language, platform, framework, JVM, generated-code requirement, or third-party API requirement.

Code quality, simplicity, organization, maintainability, documentation, formatting consistency, memory efficiency, CPU efficiency, allocation behavior, API clarity, and runtime performance are mandatory requirements rather than optional preferences.

The project should favor highly optimized, low-overhead Java while maintaining correctness, consistency, and understandable architecture.

---

# Canonical Style Reference

When a formatting or documentation rule in this file is ambiguous, generated Java should follow the established style represented by the project's well-structured classes.

The preferred style is:

- Detailed class-level Javadoc for substantial systems
- Practical usage examples for large APIs
- Thorough Javadocs for non-overriding methods
- No Javadoc on fields
- Concise right-side comments on non-static fields
- Multi-line block comments above static fields
- Compact early-return conditionals when appropriate
- Braces for multi-statement blocks
- Vertically formatted fluent method chains when they are not inside parentheses
- Compact chained expressions inside parentheses
- Parameters kept on one line
- Annotations stacked vertically
- Focused methods that are not fragmented merely to reduce line count
- Clear source architecture without unnecessary runtime abstraction

When another formatting rule appears to conflict with this established style, prefer the established project style unless the user explicitly provides a newer rule.

---

# Core Development Philosophy

Every implementation should strive to be:

- Correct
- Simple
- Fast
- Memory-efficient
- Allocation-conscious
- Cache-conscious
- Predictable
- Easy to use
- Easy to understand
- Easy to maintain
- Consistently formatted
- Thoroughly documented where documentation is required
- Architecturally focused

Do not introduce complexity unless that complexity provides a measurable or structurally important benefit.

Do not sacrifice runtime efficiency merely for stylistic convenience.

Do not introduce complicated micro-optimizations that provide no realistic benefit.

Optimization decisions should account for:

- CPU instructions
- Allocation count
- Object size
- Memory footprint
- Memory bandwidth
- Cache locality
- Branch behavior
- Boxing and unboxing
- Garbage collection pressure
- Method-call overhead
- Synchronization overhead
- Data structure complexity
- Algorithmic complexity
- Frequency of execution
- JVM/JIT behavior
- Inlining potential
- Devirtualization potential
- Escape analysis
- Data locality

The overall objective is:

**Use the simplest architecture that provides the required behavior while producing the least reasonable CPU, memory, allocation, and runtime overhead.**

---

# Java Type Usage

## Fully Qualified Type Names Are Prohibited Unless Absolutely Necessary

Do not use fully qualified class or type names anywhere in normal Java source code.

Classes must be imported and referenced using their simple names.

This is a hard rule.

The prohibition applies everywhere, including:

- Local variables
- Fields
- Parameters
- Constructor parameters
- Return types
- Generic parameters
- Generic bounds
- Wildcard bounds
- Object creation
- Casts
- `instanceof`
- Pattern matching
- Method references
- Static references
- Class literals
- Annotation values
- Annotation types
- `extends`
- `implements`
- `throws`
- Record components
- Collections
- Nested generic expressions
- Reflection-related type references
- Any other place a Java type appears

### Wrong

```java
java.util.ArrayList<String> list = new java.util.ArrayList<>();
```

```java
Class<? extends valthorne.scene.Scene> sceneType;
```

```java
public final class GameScene extends valthorne.scene.Scene {
}
```

### Correct

```java
import java.util.ArrayList;
import valthorne.scene.Scene;

ArrayList<String> list = new ArrayList<>();
Class<? extends Scene> sceneType;
```

```java
public final class GameScene extends Scene {
}
```

## Necessary Exception

A fully qualified class name may only be used when genuinely unavoidable.

The primary valid case is an unavoidable simple-name collision.

If two required types share the exact same simple name:

- Import one normally.
- Fully qualify only the conflicting usages.
- Do not spread fully qualified names elsewhere.
- Do not qualify types merely to avoid imports.
- Do not qualify types because generated code or an IDE did so.
- Do not qualify generic arguments unless the collision makes it unavoidable.

If both conflicting classes belong to this project, prefer renaming one of them when doing so improves clarity and is practical.

The default rule is:

**If a type can legally be imported and referenced by its simple name, it must be imported and referenced by its simple name.**

---

# Class and Type Organization

## Never Nest Named Types

Do not declare named types inside other types.

This includes:

- Classes
- Interfaces
- Enums
- Records
- Annotations

Avoid:

- Static nested classes
- Non-static inner classes
- Local classes
- Nested interfaces
- Nested enums
- Nested records
- Nested annotations

Every named type should have its own source file.

## Use Packages for Related Types

When a system requires several related types, place them in an appropriate package rather than nesting them.

Prefer:

```text
tick/
    Tick.java
    TickSchedule.java
    TickConditions.java
    ActionKind.java
    After.java
```

over placing all of those types inside `Tick`.

The default rule is:

**One named type per source file. Related types belong in packages, not inside other types.**

## Necessary Exceptions

Anonymous implementations may be used when required by Java or a third-party API and when creating a standalone type would provide no meaningful benefit.

---

# Class Responsibility and System Decomposition

## One Class Handles One Primary Responsibility

Every class should control, represent, manage, or process one primary concept.

A class should have:

- One clear purpose
- One coherent responsibility
- One primary reason to change

Do not create classes that become dumping grounds for unrelated behavior.

## Break Complex Systems Into Focused Pieces

If a system begins accumulating:

- Unrelated state
- Independent algorithms
- Multiple separate lifecycles
- Too many unrelated dependencies
- Several distinct reasons to change

split it into smaller focused systems.

## Separate Coordination From Implementation

A high-level class may coordinate multiple systems.

It should not internally contain complete implementations of every system it coordinates.

## Do Not Split Arbitrarily

Do not create tiny wrapper classes simply to reduce line count.

A class boundary should represent a meaningful:

- Responsibility
- Concept
- Resource
- Lifecycle
- Algorithm
- Behavior
- Architectural boundary

The goal is:

**High cohesion within classes and low coupling between unrelated classes.**

---

# Class Member Organization

## Fields Stay Together at the Top

All fields should be declared together near the top of the class before constructors and methods.

Do not interleave fields with behavior.

## One Field Per Declaration

Each field must have its own declaration.

### Wrong

```java
private float x, y;
```

### Correct

```java
private float x; // Horizontal position in world units.
private float y; // Vertical position in world units.
```

## Field Ordering

Fields should be organized logically.

Prefer grouping by:

- Related state
- Lifecycle
- Timing
- Configuration
- Callbacks
- Supporting systems

Do not force arbitrary alphabetical or name-length ordering when logical grouping produces clearer code.

Keep related fields together.

## Constructors

Constructors appear after fields.

Multiple constructors stay grouped together.

## Method Ordering

Prefer the following broad order when it does not disrupt logical API readability:

1. Static methods
2. Final instance methods
3. Non-final instance methods

However, coherent public API grouping may take precedence when that makes a large class significantly easier to understand.

Related methods should remain near each other.

Do not scatter related lifecycle or configuration methods unnecessarily.

---

# Class Declaration Formatting

Class, interface, enum, record, and annotation declarations should remain on one line.

### Wrong

```java
public final class PlayerSystem
        extends BaseSystem
        implements Disposable {
}
```

### Correct

```java
public final class PlayerSystem extends BaseSystem implements Disposable {
}
```

Avoid wrapping:

- `extends`
- `implements`
- Modifiers
- Generic declarations

onto separate lines.

If the declaration becomes excessively complex, simplify the architecture.

---

# Method and Constructor Parameters

Method and constructor parameter lists should remain on one line.

### Wrong

```java
public void move(
        float x,
        float y,
        float speed
) {
}
```

### Correct

```java
public void move(float x, float y, float speed) {
}
```

If a constructor requires too many parameters, strongly consider a fluent or builder API instead.

---

# Annotation Formatting

Each annotation belongs on its own line.

### Wrong

```java
@Override @Deprecated public void update() {
}
```

### Correct

```java
@Override
@Deprecated
public void update() {
}
```

Annotations must not share a line with the declaration they annotate.

---

# Enum Formatting

Every enum constant appears on its own line.

### Wrong

```java
public enum Direction {
    NORTH, SOUTH, EAST, WEST
}
```

### Correct

```java
public enum Direction {
    NORTH,
    SOUTH,
    EAST,
    WEST
}
```

---

# Chained Method Formatting

## General Rule

Fluent and chained method calls should normally be formatted vertically when the chain exists as a standalone expression, assignment, return value, or declaration initializer.

### Wrong

```java
Tick tick = new Tick().delay(1).repeat(5).callback(this::run);
```

### Correct

```java
Tick tick = new Tick()
        .delay(1)
        .repeat(5)
        .callback(this::run);
```

Every additional chained invocation begins on a new line when the chain itself is being formatted as a standalone chained expression.

This applies to:

- Fluent APIs
- Builders
- Configuration APIs
- Query APIs
- Layout APIs
- Framework APIs
- Similar standalone chained expressions

---

# Chained Calls Inside Parentheses

## The Chained-Newline Rule Does Not Apply Inside Parentheses

When a chained expression appears inside `(...)`, do not vertically split the chain merely because it contains multiple method calls.

Inside parentheses, chained expressions should remain compact and inline whenever reasonably possible.

This rule overrides the general chained-method formatting rule.

Parenthesized contexts include:

- `if (...)`
- `else if (...)`
- `while (...)`
- `switch (...)`
- `synchronized (...)`
- Method arguments
- Constructor arguments
- Annotation arguments
- `assert`
- Ternary subexpressions contained inside parentheses
- Nested method-call arguments
- Any other expression enclosed by parentheses

### Wrong

```java
if (
    component.getClass()
            .getName()
            .equals(type.getName())
)
    return;
```

### Wrong

```java
if (component.getClass()
        .getName()
        .equals(type.getName()))
    return;
```

### Correct

```java
if (component.getClass().getName().equals(type.getName()))
    return;
```

### Wrong

```java
register(
        component.getClass()
                .getName()
                .toLowerCase()
);
```

### Correct

```java
register(component.getClass().getName().toLowerCase());
```

### Wrong

```java
Objects.requireNonNull(
        configuration.getWindow()
                .getTitle()
);
```

### Correct

```java
Objects.requireNonNull(configuration.getWindow().getTitle());
```

### Wrong

```java
new Resource(
        path.toAbsolutePath()
                .normalize()
);
```

### Correct

```java
new Resource(path.toAbsolutePath().normalize());
```

## Parentheses Take Precedence Over Chain Formatting

The formatting priority is:

```text
CHAIN OUTSIDE PARENTHESES
    → vertical chaining

CHAIN INSIDE PARENTHESES
    → keep the chain inline
```

Do not mechanically put each `.` on a new line when the chain is part of a parenthesized expression.

The existence of chained calls does not justify vertically expanding a method argument, constructor argument, or condition.

## If the Parenthesized Expression Becomes Excessively Complex

Do not split every chained call vertically inside the parentheses.

Instead, prefer one of these approaches:

1. Keep the expression on one line when still reasonably understandable.
2. Extract a meaningful intermediate value before the statement.
3. Simplify the surrounding logic.

### Prefer Extraction

```java
String componentName = component.getClass().getName();

if (componentName.equals(type.getName()))
    return;
```

rather than:

```java
if (component.getClass()
        .getName()
        .equals(type.getName()))
    return;
```

The objective is:

**Parentheses should not trigger vertically exploded method chains.**

---

# Field Declaration Formatting

Normal field declarations remain on one line.

### Wrong

```java
private final Map<String,
        Entity> entities;
```

### Correct

```java
private final Map<String, Entity> entities; // Entities indexed by their unique names.
```

A chained field initializer may continue vertically according to the chaining rules because the chain exists outside a parenthesized argument context.

### Correct

```java
private final ServerConfig config = ServerConfig.builder()
        .port(43594)
        .workerThreads(8)
        .keepAlive(true)
        .build(); // Immutable server configuration.
```

---

# Conditional Formatting

## Parentheses Remain Compact

The expression contained by `if (...)` should begin immediately after `(` and end immediately before `)`.

Do not place the opening condition on the following line.

Do not place the closing `)` on its own line solely for formatting.

### Wrong

```java
if (
    component != null
)
    update();
```

### Correct

```java
if (component != null)
    update();
```

## Single-Statement Conditionals

For a single statement, braces are optional and generally unnecessary.

Both of these compact forms are acceptable when clear:

```java
if (state == State.STOPPED) return;
```

```java
if (maximum < 1)
    throw new IllegalArgumentException("maximum must be at least 1.");
```

Prefer the one-line form for very short early returns and similarly obvious guards.

Prefer the two-line form when the condition or controlled statement is long.

Do not force braces around a single obvious statement.

## Multiple Statements

Multiple statements require braces.

```java
if (active) {
    updatePosition();
    updateAnimation();
}
```

## Nested or Ambiguous Conditionals

Use braces when omitting them would reduce clarity or make control flow visually ambiguous.

Correctness and readability take precedence over mechanically removing braces.

---

# General Control-Flow Formatting

Do not unnecessarily compress complex control flow.

Short guard statements may remain compact.

Larger control-flow constructs should use conventional multi-line formatting.

### Prefer

```java
try {
    save();
} catch (IOException failure) {
    report(failure);
}
```

rather than compressing `try` and `catch` bodies onto one line.

Loops with trivial single statements may omit braces when clear.

Use braces when:

- Multiple statements exist
- Nesting becomes ambiguous
- Additional behavior is likely to be added
- Clarity improves substantially

---

# Fluent APIs and Builder Patterns

## Prefer Fluent Construction for Large Configurable Systems

When a system has substantial configuration or many optional values, prefer:

- Method chaining
- Fluent configuration
- Builder patterns

over:

- Huge constructors
- Long positional argument lists
- Constructor overload explosions
- Unclear setter sequences

### Prefer

```java
ServerConfig config = ServerConfig.builder()
        .port(43594)
        .workerThreads(8)
        .tcpNoDelay(true)
        .keepAlive(true)
        .build();
```

## Do Not Use Builders for Trivial Objects

Prefer:

```java
Position position = new Position(x, y);
```

instead of a builder when construction is simple.

## Fluent APIs Must Remain Efficient

Do not let fluent syntax introduce unnecessary allocation.

Prefer:

- One mutable temporary builder
- Few intermediate objects
- Minimal validation duplication
- Small chain methods
- JIT-friendly implementation

---

# Performance and Micro-Optimization

## Performance Is a First-Class Requirement

All code should be written with runtime efficiency in mind.

When several correct implementations exist, prefer the one expected to use:

- Fewer CPU cycles
- Fewer allocations
- Less memory
- Less GC
- Fewer indirections
- Less synchronization
- Less boxing
- Less copying
- Fewer repeated calculations
- Better cache locality
- Better sequential memory access

Performance should be considered during design, not only after implementation.

## Minimize CPU Work

Avoid unnecessary:

- Repeated calculations
- Repeated lookups
- Repeated conversions
- Redundant checks
- Expensive abstraction layers
- Excessive branching
- Synchronization
- Copying

Cache stable results when doing so provides a meaningful advantage.

## Minimize Allocations

Be especially strict in:

- Render loops
- Update loops
- Tick systems
- ECS systems
- Networking
- Physics
- Audio
- Serialization
- Deserialization
- High-frequency UI code

Avoid unnecessary:

- Temporary objects
- Arrays
- Wrappers
- Streams
- Iterators
- Lambdas
- Temporary collections
- Strings
- `Optional`
- Boxing

## Prefer Primitive Data

Use primitives instead of wrappers when object semantics are unnecessary.

## Avoid Streams in Hot Code

Prefer explicit loops when they are cheaper and clearer.

## Prefer Arrays When Appropriate

Use arrays when:

- Indexed traversal dominates
- Storage size is predictable
- Memory locality matters
- Collection features are unnecessary

## Choose Data Structures Deliberately

Do not automatically use general-purpose collections without considering the actual access pattern.

## Optimize for Cache Locality

Prefer compact and sequential data representation in performance-sensitive systems.

Avoid excessive pointer chasing.

## Avoid Unnecessary Synchronization

Do not make code thread-safe by default.

Use:

- Locks
- Atomics
- `volatile`
- Concurrent collections
- `synchronized`

only when genuine concurrent access requires them.

Prefer thread confinement.

## Avoid Reflection in Hot Paths

Prefer direct calls and cached/precomputed metadata.

## Avoid Exceptions for Normal Control Flow

Exceptions should represent exceptional conditions.

## Avoid Repeated String Work

Avoid high-frequency:

- Formatting
- Concatenation
- Parsing
- Regex
- Case conversion
- Temporary strings

## Avoid Regex for Simple Parsing

Prefer direct parsing for simple structured input when substantially cheaper.

---

# JVM and JIT Optimization

Clean source architecture does not imply expensive runtime behavior.

The JVM can optimize across class and method boundaries using:

- Method inlining
- Devirtualization
- Escape analysis
- Scalar replacement
- Dead-code elimination
- Constant folding
- Loop optimization
- Class hierarchy analysis

Do not combine unrelated classes merely to avoid method calls.

Help the JVM optimize by:

- Using `final` where inheritance is unnecessary
- Avoiding unnecessary polymorphism
- Avoiding interfaces that serve no useful purpose
- Keeping hot call sites predictable
- Keeping frequently inlined methods reasonably small
- Avoiding reflection in hot paths
- Preferring concrete types where abstraction provides no useful boundary

The goal is to minimize actual runtime overhead, not source-file count.

---

# Benchmark Important Optimization Decisions

When an optimization significantly complicates code or depends on JVM behavior, validate it where practical using:

- JMH
- CPU profiling
- Allocation profiling
- GC profiling
- Real workload benchmarks
- JVM profiling tools

Measured behavior is more reliable than assumptions.

---

# Dependency Rules

Do not add dependencies without a concrete reason.

Avoid:

- Large libraries for tiny functionality
- Duplicate dependencies
- Overlapping libraries
- Dependencies that create unnecessary runtime cost
- Dependencies when existing project functionality already solves the problem

---

# Utility Class Rules

Do not create dumping-ground classes such as:

```text
Utils
Helpers
Common
Misc
GeneralUtil
```

Utility classes must have a focused responsibility.

Examples:

```text
MathUtil
BufferUtil
PathUtil
StringUtil
ColorUtil
```

Split them when responsibilities diverge.

---

# Visibility Rules

Use the narrowest visibility practical.

Preference:

1. `private`
2. package-private
3. `protected`
4. `public`

Do not expose implementation details unnecessarily.

---

# Composition Over Inheritance

Prefer composition unless inheritance represents a real subtype relationship.

Do not use inheritance merely for code reuse.

Inheritance should represent:

**is-a**

rather than:

**reuses-implementation-from**

---

# Null Handling

Avoid nullable state when a clearer representation exists.

Prefer:

- Empty collections
- Explicit enums
- Explicit flags
- Dedicated state representations
- Meaningful sentinel values

If `null` has semantic meaning, document that meaning.

Avoid `Optional` fields and avoid unnecessary `Optional` use in internal hot code.

---

# Boundary Validation

Validate data at system boundaries:

- Public APIs
- Files
- Network packets
- External resources
- User-controlled input
- Configuration
- Databases
- Plugins

Do not repeatedly revalidate trusted internal state unless correctness requires it.

---

# Exception Handling

Never swallow exceptions.

Empty catch blocks are prohibited.

Exceptions should be:

- Handled
- Propagated
- Wrapped appropriately
- Reported when necessary

Preserve the original cause when wrapping exceptions.

---

# Constants and Magic Values

Use meaningful constants for important:

- Limits
- Buffer capacities
- Timing values
- Protocol IDs
- Bit masks
- Defaults
- Conversion factors
- Tuning values

Do not create constants for trivial literals when doing so hurts readability.

---

# Method Responsibility

Methods should perform one coherent operation.

Do not fragment coherent algorithms merely to reduce method length.

Split methods when they begin mixing independent:

- Algorithms
- State responsibilities
- Error-handling responsibilities
- Processing phases

A longer cohesive method is preferable to many meaningless forwarding methods.

---

# API Simplicity

Public APIs should be obvious to use.

Avoid unnecessary:

- Factories
- Builders
- Wrappers
- Interfaces
- Adapters
- Generic abstraction layers
- Indirection

unless they provide concrete value.

---

# Avoid Premature Abstraction

Do not create abstractions solely for hypothetical future needs.

Avoid interfaces when:

- Only one implementation exists
- No meaningful boundary is created
- No alternate implementation is expected
- Testing does not require substitution

---

# Preserve Project Conventions

Inspect surrounding project code before introducing a new architectural pattern.

When an existing project convention conflicts with this file, this `AGENTS.md` takes precedence unless the user explicitly establishes a newer preferred style.

The canonical project style demonstrated by high-quality existing classes should be used to resolve ambiguities.

---

# Do Not Modify Unrelated Code

Do not unnecessarily:

- Rename unrelated types
- Reformat unrelated files
- Move unrelated packages
- Refactor unrelated systems
- Change unrelated APIs
- Replace unrelated architecture

Changes should remain connected to the requested work.

---

# Naming Rules

Use precise names.

Avoid vague names such as:

```text
thing
stuff
data
helper
doThing
doWork
```

when a more descriptive name exists.

Names such as:

- `Manager`
- `Handler`
- `Controller`
- `System`

are acceptable when they accurately describe one focused responsibility.

---

# Package Structure

Keep package structures shallow until additional hierarchy is justified.

Use subpackages for meaningful architectural subdivisions, not arbitrary organization.

---

# Documentation Terminology

The documentation forms below are different and are not interchangeable.

## Javadoc

Javadoc uses:

```java
/**
 * Documentation text.
 */
```

Single-line Javadoc is prohibited.

### Wrong

```java
/** Updates the player. */
```

### Correct

```java
/**
 * Updates the player's current state.
 */
```

Javadoc is used where required for:

- Classes
- Interfaces
- Enums
- Records
- Constructors
- Non-overriding methods

**Fields never use Javadoc.**

## Block Comments

Block comments use:

```java
/*
 * Documentation or implementation text.
 */
```

Block comments are specifically required for:

- Static fields, directly above the field
- Static methods, inside the method body

Block comments may also be used elsewhere for genuinely useful implementation explanations.

One-line block comments are prohibited.

## Line Comments

Line comments use:

```java
// Comment text.
```

Line comments are required directly to the right of non-static fields.

They are also appropriate for short implementation notes.

---

# Documentation Requirements

Documentation is mandatory where required by these rules.

Required documentation includes:

- Classes
- Interfaces
- Enums
- Records
- Constructors
- Non-overriding methods
- Static fields
- Non-static fields
- Significant implementation behavior

Exceptions:

- `@Override` methods do not require method documentation.
- Fields follow their exclusive field-comment rules.
- Fields never use Javadoc.

---

# Override Method Documentation

Methods annotated with `@Override` do not require Javadoc merely because they are methods.

### Correct

```java
@Override
public void update(float delta) {
    elapsed += delta;
}
```

### Wrong

```java
/**
 * Updates this object.
 */
@Override
public void update(float delta) {
    elapsed += delta;
}
```

Implementation comments may still be used inside an override when something genuinely non-obvious needs explanation.

---

# Class Documentation

Every class requires detailed multi-line Javadoc directly above its declaration.

For simple data classes, documentation may be concise but should still explain the purpose of the type.

For substantial systems, class-level Javadoc should explain:

- Purpose
- Responsibilities
- Lifecycle
- Usage
- Configuration
- Timing behavior
- State behavior
- Threading
- Resource ownership
- Performance
- Allocation characteristics
- Failure behavior
- Important invariants
- Interactions with related systems
- Edge cases

When the class exposes a substantial system, include realistic examples.

The larger and more feature-rich the class, the more complete the class-level documentation should be.

---

# Large System Documentation

If a class represents a large or feature-rich system, class-level Javadoc should function as practical technical documentation.

It should include applicable sections such as:

- Overview
- Conditions
- Timing
- State behavior
- Scheduling
- Threading
- Allocation behavior
- Safety
- Mutation behavior
- Failure semantics
- Examples

A large example should demonstrate major features together rather than showing only object construction.

---

# Method Documentation

Every non-overriding method requires multi-line Javadoc.

This includes:

- Public methods
- Protected methods
- Package-private methods
- Private methods
- Static methods
- Constructors

Documentation depth should match method complexity.

Simple getters may use concise documentation.

Complex methods should explain:

- Purpose
- Important algorithm behavior
- Parameters
- Units
- Return values
- State changes
- Side effects
- Exceptions
- Nullability
- Timing behavior
- Threading concerns
- Performance considerations
- Preconditions
- Postconditions

Do not write empty documentation that merely repeats the method name.

---

# Static Method Documentation

Every static method requires:

1. Multi-line Javadoc above the method.
2. A meaningful multi-line block comment inside the method body.

### Correct

```java
/**
 * Converts seconds into nanoseconds.
 *
 * @param seconds number of seconds to convert
 * @return equivalent duration in nanoseconds
 */
public static long secondsToNanos(long seconds) {
    /*
     * Use direct integer multiplication to avoid temporary duration objects
     * and unnecessary floating-point conversion.
     */
    return seconds * 1_000_000_000L;
}
```

The internal block comment should explain something useful, such as:

- Algorithm choice
- Optimization
- Invariants
- Memory behavior
- Edge handling
- Implementation reasoning

Do not add meaningless filler.

---

# Field Documentation

## Field Documentation Overrides General Documentation Rules

Fields have exclusive documentation rules.

**Fields never use Javadoc.**

There are exactly two field-documentation styles:

```text
STATIC FIELD
    → multi-line /* ... */ comment directly above

NON-STATIC FIELD
    → // comment directly to the right
```

A field must not use both forms.

---

# Static Field Documentation

Every static field requires a meaningful multi-line block comment directly above it.

This includes:

- Public static fields
- Private static fields
- Static constants
- Static caches
- Static collections
- Static patterns
- Static lookup tables
- Mutable static fields
- `static final` fields

### Correct

```java
/*
 * Maximum number of scheduled actions that may execute in one update.
 */
private static final int MAX_ACTIONS = 64;
```

### Wrong

```java
/**
 * Maximum number of scheduled actions.
 */
private static final int MAX_ACTIONS = 64;
```

### Wrong

```java
private static final int MAX_ACTIONS = 64; // Maximum actions.
```

### Wrong

```java
/* Maximum actions. */
private static final int MAX_ACTIONS = 64;
```

The block comment is the static field's complete documentation.

Do not add field Javadoc or a duplicate right-side comment.

---

# Non-Static Field Documentation

Every non-static field requires a concise line comment directly to the right.

That right-side comment is the field's complete documentation.

### Correct

```java
private float delay; // Seconds between executions; zero means once per eligible update.
private double elapsed; // Unconsumed running time, including retained catch-up backlog.
private State state = State.STOPPED; // Current lifecycle state.
private TickSchedule schedule; // Lazily allocated ordered action schedule.
```

### Wrong

```java
/**
 * Seconds between executions.
 */
private float delay; // Seconds between executions.
```

### Wrong

```java
/*
 * Seconds between executions.
 */
private float delay;
```

### Wrong

```java
// Seconds between executions.
private float delay;
```

### Wrong

```java
private float delay;
```

For non-static fields:

**No Javadoc. No comment above. Exactly one meaningful right-side line comment.**

---

# Field Documentation Decision Rule

```text
IS STATIC?

YES
    → NO JAVADOC
    → MULTI-LINE BLOCK COMMENT DIRECTLY ABOVE
    → NO DUPLICATE RIGHT-SIDE DOCUMENTATION

NO
    → NO JAVADOC
    → NO BLOCK COMMENT ABOVE
    → RIGHT-SIDE // COMMENT
```

Visibility and `final` do not change this rule.

---

# Block Comment Formatting

One-line block comments are prohibited.

### Wrong

```java
/* Fast path. */
```

### Correct

```java
/*
 * Use the dedicated unconditional timing path to avoid predicate traversal
 * when no configurable conditions are installed.
 */
```

Static field block comments must remain multi-line even if the description is short.

---

# Implementation Comments

Use `//` for short implementation notes.

Example:

```java
// Commit the interval before user code so exceptions cannot replay it.
```

Use a multi-line block comment when a longer explanation is needed.

Do not add implementation comments merely to satisfy a quota.

Comments should explain useful intent or non-obvious behavior.

---

# Documentation Quality

Documentation must communicate useful information.

### Poor

```java
private boolean running; // Running.
```

### Better

```java
private boolean running; // Whether the tick may currently advance and execute callbacks.
```

Simple APIs may have concise documentation.

Complex systems should have detailed documentation.

Documentation should be proportional to complexity rather than mechanically verbose.

---

# Documentation Synchronization

Whenever behavior changes, update the corresponding documentation.

This includes:

- Class Javadocs
- Usage examples
- Method Javadocs
- Static-field block comments
- Non-static field comments
- Static-method block comments
- Parameter descriptions
- Return descriptions
- Exception documentation

Outdated documentation is considered a defect.

---

# Hot-Path Rules

For high-frequency code:

- Avoid allocations
- Avoid boxing
- Avoid streams
- Avoid temporary strings
- Avoid temporary collections
- Avoid redundant validation
- Avoid unnecessary synchronization
- Avoid unnecessary polymorphism
- Reuse buffers
- Preallocate predictable storage
- Prefer primitive data
- Prefer sequential memory access
- Cache stable values
- Move invariant work outside loops
- Keep control flow simple

Examples include:

- Frame updates
- Tick updates
- ECS systems
- Rendering
- Physics
- Networking
- Audio
- Serialization
- Input polling

---

# Optimization Review Checklist

Before considering implementation complete, verify:

1. Are there unnecessary allocations?
2. Is anything boxed unnecessarily?
3. Can temporary objects be removed?
4. Are collections appropriately sized?
5. Would arrays be more efficient?
6. Are repeated calculations cached appropriately?
7. Is invariant work happening inside loops?
8. Are streams or lambdas adding overhead?
9. Is synchronization actually required?
10. Is reflection used in hot code?
11. Are strings repeatedly created?
12. Is the chosen data structure appropriate?
13. Is data arranged efficiently?
14. Are abstraction layers unnecessary?
15. Can branches or lookups be simplified?
16. Are builders creating unnecessary temporary objects?
17. Can buffers be reused?
18. Can object lifetimes be shortened?
19. Is duplicate state wasting memory?
20. Has a non-obvious optimization been benchmarked where practical?
21. Can the JIT inline hot methods easily?
22. Is unnecessary polymorphism blocking devirtualization?
23. Are fully qualified class names used unnecessarily?
24. Are fields grouped at the top?
25. Is there one field per declaration?
26. Are field comments using the correct static/non-static style?
27. Does any field incorrectly use Javadoc?
28. Are annotations stacked vertically?
29. Are standalone chained calls vertically formatted?
30. Are chained calls inside parentheses kept inline?
31. Are enum constants each on their own line?
32. Are class declarations kept on one line?
33. Are parameters kept on one line?
34. Are multi-statement control-flow bodies properly braced?
35. Does the resulting source match established project style?

---

# Final Formatting Standard

All Java code must follow these rules:

**Fields remain together at the top of the class.**

**Use one field per declaration.**

**Fields are grouped logically.**

**Fields never use Javadoc.**

**Static fields use a multi-line block comment directly above them.**

**Non-static fields use a meaningful right-side line comment.**

**Class declarations remain on one line.**

**Method and constructor parameters remain on one line.**

**Annotations stack vertically.**

**Enum constants each appear on their own line.**

**Standalone fluent method chains are vertically formatted.**

**Chained expressions inside parentheses remain inline and are not vertically split merely because they are chains.**

**The chained-call newline rule never overrides parenthesized-expression formatting.**

**Normal field declarations remain on one line.**

**Single-statement conditionals may omit braces.**

**Very short guard statements may remain on one line when clear.**

**Multi-statement control-flow bodies require braces.**

**Do not compress complex control flow merely to reduce line count.**

**Fully qualified type names are prohibited unless genuinely unavoidable.**

---

# Final Architectural Standard

All code should follow these principles:

**One primary responsibility per class.**

**Break complex mixed systems into focused components.**

**Keep named types in separate source files.**

**Use packages to group related types.**

**Prefer composition over unnecessary inheritance.**

**Prefer fluent APIs or builders for large configurable systems.**

**Use direct construction for simple objects.**

**Keep APIs simple and precise.**

**Avoid unnecessary abstractions and dependencies.**

**Use restrictive visibility.**

**Avoid unnecessary nullable state.**

**Validate at system boundaries.**

**Never swallow exceptions.**

**Use meaningful constants where appropriate.**

**Keep methods conceptually focused.**

**Do not modify unrelated code without reason.**

**Document substantial systems thoroughly.**

**Document all non-overriding methods appropriately.**

**Do not add method Javadoc to `@Override` methods merely to satisfy documentation rules.**

**Fields never use Javadoc.**

**Static fields use multi-line block comments above them.**

**Non-static fields use right-side line comments.**

**Static methods require Javadoc plus a meaningful internal multi-line block comment.**

**Never use single-line Javadoc.**

**Never use one-line block comments.**

**Keep documentation synchronized with behavior.**

**Never use a fully qualified type where a normal import can be used.**

**Format standalone chains vertically, but never explode chained calls merely because they appear inside parentheses.**

**Minimize allocations, CPU work, memory usage, GC pressure, synchronization, boxing, copying, indirection, and repeated computation.**

**Treat performance as a design requirement from the beginning.**

**Do not sacrifice clean source architecture merely to reduce source-level method calls.**

**Design hot code so the JVM can inline, devirtualize, and optimize it effectively.**

**Benchmark significant performance decisions when practical.**

The overall project standard is:

**Write the smallest, cleanest, most focused, consistently styled, appropriately documented, and most runtime-efficient implementation that correctly solves the problem without introducing unnecessary architecture or overhead.**