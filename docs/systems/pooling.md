# Object pooling

Author: Albert Beaupre

[System manual](README.md)

## Purpose

Pool reuses objects produced by a factory, while Poolable defines reset behavior where applicable. This reduces repeated allocation for temporary objects with clear acquire/release boundaries. Pool capacity describes retained free objects, not necessarily all objects ever created.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Factory | Creates an object when the pool cannot supply an existing one. |
| Warm-up | Initialization prepopulates reusable objects. |
| Obtain/free | Acquire temporary ownership and return the object after use. |
| Reset and retention | Reset behavior prepares reuse; capacity limits how much free storage is retained. |

## Getting started

1. Provide a factory and choose the maximum retained size.
2. Warm the pool if predictable initial allocations matter.
3. Obtain an object, initialize the state needed for the operation, and use it temporarily.
4. Return it exactly once after every consumer has finished.

## Ownership and lifecycle

Once an object is returned, another caller can obtain and mutate it. Do not retain a returned reference as persistent game state. Pooling does not automatically release arbitrary native resources held by an object.

## Important behavior

- Double-free can make one object appear available more than once unless the implementation explicitly detects it.
- Reset must clear the state that would leak from one use to another.
- Use one owning thread or the synchronization policy explicitly supported by the pool.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`Pool`](#type-pool)
- [`Poolable`](#type-poolable)

<a id="type-pool"></a>

### Pool

[Source](../../src/main/java/valthorne/io/pool/Pool.java#L77)

`Pool` is a generic reusable object pool designed to reduce repeated
allocations by recycling objects instead of constantly creating and discarding them.
This is especially useful in performance-sensitive systems such as rendering,
particle effects, physics, UI events, temporary math objects, and other high-frequency
runtime workflows where many short-lived objects may otherwise create avoidable
garbage collection pressure.

The pool stores instances in an internal `FastStack`. When an object is
requested through `obtain()`, the pool attempts to return an already existing
object from the stack. If none are available, a new instance is created through the
provided `Supplier` factory.

When an object is returned through `free(Object)`, the pool optionally resets
it if it implements `Poolable`, then places it back into the stack as long as
the configured maximum pool size has not been reached.

This class does not require pooled objects to implement `Poolable`, but if they
do, the reset step allows them to be safely reused in a clean state. That makes this
pool flexible enough to manage both resettable and non-resettable object types.

Typical usage involves:

- creating the pool with a factory and a max size

- optionally preloading objects with `initialize(int)`

- getting objects with `obtain()`

- returning objects with `free(Object)`

##### Example Usage

```java
Pool<MyParticle> particlePool = new Pool<>(MyParticle::new, 256);
particlePool.initialize(32);

MyParticle particle = particlePool.obtain();
particle.setPosition(100, 200);
particle.setVelocity(1.5f, -0.5f);

// Use the particle...

particlePool.free(particle);

int pooledCount = particlePool.size();

particlePool.clear();
```

This example demonstrates the full intended use of the class: construction,
pre-initialization, obtaining an object, using it, returning it, inspecting
the current pool size, and clearing the pool.

- **`<T>`** — the type of objects managed by this pool

<details>
<summary>Pool operation reference (6 declarations)</summary>

#### Constructor

```java
public Pool(Supplier<T> factory, int maxSize)
```

Creates a new object pool with the provided factory and maximum retention size.

The factory is used whenever `obtain()` is called and the pool has no
currently available objects. The max size controls how many returned objects
may be stored for future reuse. If more objects are freed after the pool has
reached its maximum size, those extra objects are simply not stored.

- **`factory`** — supplier used to create new objects when needed
- **`maxSize`** — maximum number of objects the pool may retain

#### initialize

```java
public void initialize(int count)
```

Pre-populates the pool by creating and storing a specified number of objects.

This is useful when you want to front-load object creation during initialization
rather than allowing the pool to grow lazily during runtime. Each created object
is produced through the configured factory and pushed into the internal stack.

This method does not enforce `maxSize` directly, so callers should pass
values that make sense for their intended pool configuration.

- **`count`** — the number of objects to create and add to the pool

#### obtain

```java
public T obtain()
```

Obtains an object from the pool.

If the pool contains an available object, that object is popped from the internal
stack and returned. If the pool is empty, a new object is created through the
configured factory and returned instead.

This method never resets the object on obtain. Resetting happens when an object
is returned through `free(Object)` if it implements `Poolable`.

**Returns:** a pooled object, either reused or newly created

#### free

```java
public void free(T obj)
```

Returns an object to the pool for future reuse.

If the provided object is `null`, the method returns immediately.
If the object implements `Poolable`, its `Poolable#reset()` method
is called before the object is stored. The object is only pushed back into the
pool if the current pool size is still below the configured maximum size.

This behavior prevents the pool from growing without limit while still giving
resettable objects a clean state before reuse.

- **`obj`** — the object to return to the pool

#### clear

```java
public void clear()
```

Removes all currently stored objects from the pool.

This only clears the pool's internal storage. It does not dispose objects or
perform any other cleanup beyond removing all references from the stack.

#### size

```java
public int size()
```

Returns the current number of available objects stored in the pool.

This value reflects how many objects can currently be reused without creating
new ones through the factory.

**Returns:** the number of objects currently stored in the pool

</details>

<a id="type-poolable"></a>

### Poolable

[Source](../../src/main/java/valthorne/io/pool/Poolable.java#L21)

Represents an interface for objects that can be pooled and reused.
Implementations of this interface must provide a mechanism to reset
their internal state to ensure they are ready for reuse when returned
to a pool.

Objects that implement this interface are typically used in conjunction
with object pooling mechanisms, such as the `Pool` class, to
reduce the overhead of object creation and garbage collection in
performance-critical applications.

The `reset()` method is invoked when an object is returned
to a pool to reset its internal state and prepare it for subsequent use.

<details>
<summary>Poolable operation reference (1 declarations)</summary>

#### reset

```java
void reset()
```

Reset the object to its initial state for reuse.

</details>

## Related guides

- [2D particles and spawn distributions](particles-2d.md)
- [3D particles and physics integration](particles-3d.md)
- [Resizable and unordered arrays](arrays.md)
