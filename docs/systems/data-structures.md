# ID reuse queues, string maps, and trees

Author: Albert Beaupre

[System manual](README.md)

## Purpose

This guide covers the specialized structures that do not fit ordinary array or stack usage: StringObjectMap, integer-ID reuse queues, and IntBinaryTree. Their names do not imply every guarantee of the similarly named Java collections; use the documented implementation behavior.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| String map | Stores values keyed by strings using the engine's specialized map operations. |
| Returned IDs | UUIDQueue variants reuse returned numeric identifiers and otherwise issue sequential values. |
| Tree lookup | IntBinaryTree exposes insertion/search over indexed nodes. |
| Sentinels and growth | Capacity, empty state, and numeric overflow matter to these compact structures. |

## Getting started

1. Choose the structure only after checking ordering, duplicate, and empty-state behavior.
2. Keep persistent ownership separate from the stored references or identifiers.
3. Validate externally returned IDs so duplicates do not become simultaneously active allocations.
4. Use the detailed contracts to decide whether a standard Java collection better matches the needed guarantees.

## Ownership and lifecycle

These structures do not generally synchronize access or dispose stored objects. ID reuse is a caller-managed lifecycle: returning an ID twice is not automatically prevented.

## Important behavior

- The ID queues behave as reuse stacks and contain a documented modulo/full-capacity edge case.
- IntBinaryTree's current root insertion bookkeeping can discard the first value on the next insertion.
- A specialized structure should not be treated as a drop-in replacement for a general queue or set.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`StringObjectMap`](#type-stringobjectmap)
- [`IntUUIDQueue`](#type-intuuidqueue)
- [`LongUUIDQueue`](#type-longuuidqueue)
- [`ShortUUIDQueue`](#type-shortuuidqueue)
- [`IntBinaryTree`](#type-intbinarytree)

<a id="type-stringobjectmap"></a>

### StringObjectMap

[Source](../../src/main/java/valthorne/collections/map/StringObjectMap.java#L84)

`StringObjectMap` is a hash map implementation specialized for `String`
keys and arbitrary object values. It uses open addressing with linear probing
instead of chaining, which allows it to store data in compact parallel arrays
and avoid per-entry node allocations.

This map is designed for fast lookup, insertion, and removal when keys are strings.
Internally it stores keys in one array and values in another array at matching
indices. When collisions occur, probing continues forward through the table until
an empty slot or matching key is found.

The table size is always kept as a power of two. This allows the implementation
to use a bitmask instead of a modulo operation when wrapping indices, which is
faster and keeps probing logic simple.

Important characteristics of this implementation:

- Keys must be non-null.

- Values may be null.

- Insertion order is not preserved.

- The backing table automatically resizes when the load threshold is reached.

- Removal uses backward-shift deletion so probe chains remain valid.

This class is useful when you want a lightweight map for string-keyed lookups
without the overhead of a more general-purpose collection. It is especially
appropriate in engine code, asset registries, style/resource lookups, and
other systems where many repeated string-based queries occur.

##### Example Usage

```java
StringObjectMap<Integer> scores = new StringObjectMap<>();

scores.put("Albert", 10);
scores.put("Saphira", 25);
scores.put("Turaya", 17);

Integer albert = scores.get("Albert");
Integer missing = scores.getOrDefault("Unknown", 0);

boolean hasTuraya = scores.containsKey("Turaya");
boolean hasScore25 = scores.containsValue(25);

scores.remove("Albert");

String[] keys = scores.keys();
Integer[] values = scores.values(new Integer[scores.size()]);

int size = scores.size();
int capacity = scores.capacity();
boolean empty = scores.isEmpty();

scores.ensureCapacity(16);
scores.shrink(8);
scores.clear();
```

This example demonstrates the full intended use of the class: insertion,
lookup, default lookup, containment checks, removal, key/value export,
capacity management, and clearing.

- **`<T>`** — the value type stored in the map

<details>
<summary>StringObjectMap operation reference (19 declarations)</summary>

#### Constructor

```java
public StringObjectMap()
```

Creates a new map using the default initial capacity and default load factor.

The actual backing table size is rounded up to a power of two large enough
to satisfy the requested capacity and load factor.

#### Constructor

```java
public StringObjectMap(int initialCapacity)
```

Creates a new map using the given initial capacity and the default load factor.

- **`initialCapacity`** — the requested initial capacity before load factor adjustment

#### Constructor

```java
public StringObjectMap(int initialCapacity, float loadFactor)
```

Creates a new map using the given initial capacity and load factor.

The requested capacity is converted to an internal power-of-two table size
large enough to keep the map within the desired load factor.

- **`initialCapacity`** — the requested initial capacity
- **`loadFactor`** — the load factor used to determine resize thresholds

**Throws `IllegalArgumentException`:** if `initialCapacity` is negative

**Throws `IllegalArgumentException`:** if `loadFactor` is not greater than 0 and less than 1

#### put

```java
public T put(String key, T value)
```

Inserts or replaces a value associated with the given key.

If the key does not already exist, a new entry is inserted and the size grows
by one. If the key already exists, its existing value is replaced and the old
value is returned.

If the current size has reached the threshold, the table is resized before
insertion occurs.

- **`key`** — the non-null key to insert
- **`value`** — the value to store, which may be null

**Returns:** the previous value associated with the key, or null if the key was not present

**Throws `NullPointerException`:** if `key` is null

#### get

```java
public T get(String key)
```

Returns the value associated with the given key.

If the key is null or not present in the table, this method returns null.

- **`key`** — the key to look up

**Returns:** the stored value, or null if the key is absent

#### getOrDefault

```java
public T getOrDefault(String key, T defaultValue)
```

Returns the value associated with the given key, or a supplied fallback value
if the key is not present or resolves to null.

- **`key`** — the key to look up
- **`defaultValue`** — the fallback value to return when lookup yields null

**Returns:** the stored value if non-null, otherwise `defaultValue`

#### containsKey

```java
public boolean containsKey(String key)
```

Returns whether the map currently contains the given key.

- **`key`** — the key to test

**Returns:** `true` if the key exists in the map

#### containsValue

```java
public boolean containsValue(T value)
```

Returns whether the map currently contains the given value.

Because values are stored as general objects, this method performs a linear
scan through the occupied table slots. Null values are supported and checked
explicitly.

- **`value`** — the value to test

**Returns:** `true` if the value is present in any occupied entry

#### remove

```java
public T remove(String key)
```

Removes the entry associated with the given key.

If the key is present, its value is returned and the entry is removed using
backward-shift deletion so remaining probe chains still function correctly.
If the key is absent or null, this method returns null.

- **`key`** — the key to remove

**Returns:** the removed value, or null if the key was not present

#### clear

```java
public void clear()
```

Removes all entries from the map without changing the current table capacity.

All key and value slots are cleared and size becomes zero.

#### clear

```java
public void clear(int maximumCapacity)
```

Removes all entries from the map and optionally shrinks the backing table so it
does not exceed the capacity required for the specified maximum capacity.

If the current table is already at or below the required size, this behaves
like `clear()`. Otherwise a new smaller table is allocated.

- **`maximumCapacity`** — the maximum logical capacity to retain after clearing

#### ensureCapacity

```java
public void ensureCapacity(int additionalCapacity)
```

Ensures that the map can accept the given number of additional entries without
resizing again.

If the current table is already large enough, nothing happens. Otherwise the
table is resized to the next appropriate power-of-two size.

- **`additionalCapacity`** — the number of extra entries that should fit

#### shrink

```java
public void shrink(int maximumCapacity)
```

Shrinks the table if its current capacity is larger than needed.

The new size is chosen so it can still hold at least the current number of
entries and the requested maximum capacity under the configured load factor.

- **`maximumCapacity`** — the maximum desired logical capacity after shrinking

**Throws `IllegalArgumentException`:** if `maximumCapacity` is negative

#### size

```java
public int size()
```

Returns the number of entries currently stored in the map.

**Returns:** the current size

#### isEmpty

```java
public boolean isEmpty()
```

Returns whether the map currently contains no entries.

**Returns:** `true` if the map is empty

#### capacity

```java
public int capacity()
```

Returns the current backing table capacity.

This is the number of slots in the internal key/value arrays, not the number
of entries currently stored.

**Returns:** the backing table capacity

#### getLoadFactor

```java
public float getLoadFactor()
```

Returns the configured load factor used by this map.

**Returns:** the load factor

#### keys

```java
public String[] keys()
```

Returns a new array containing all keys currently stored in the map.

Keys are returned in table iteration order, which is not insertion order and
may change after resizing or removals.

**Returns:** an array containing all stored keys

#### values

```java
@SuppressWarnings("unchecked")
    public T[] values(T[] out)
```

Copies all stored values into the provided output array.

The output array must be large enough to hold all values currently stored.
If the output array is larger than the map size, the element immediately after
the last copied value is set to null.

Values are copied in table iteration order, which is not insertion order.

- **`out`** — the destination array

**Returns:** the same destination array, filled with values

**Throws `IllegalArgumentException`:** if `out.length < size()`

</details>

<a id="type-intuuidqueue"></a>

### IntUUIDQueue

[Source](../../src/main/java/valthorne/collections/queue/IntUUIDQueue.java#L17)

Sequential int index generator with a reusable-index buffer. Callers return
indices with push; buffered values are normally reused in last-in, first-out
order before sequential generation resumes. The class does not track live
allocations or reject duplicate returns, so uniqueness depends on caller use.

Arithmetic follows primitive overflow rules. The current pop calculation
also rejects a completely full buffer with an array-index exception; it does
not implement a general circular queue. Operations are unsynchronized.

<details>
<summary>IntUUIDQueue operation reference (5 declarations)</summary>

#### Constructor

```java
public IntUUIDQueue()
```

Creates an empty reuse buffer and starts sequential generation at zero.
Sixteen returned-index slots are initially allocated.

#### Constructor

```java
public IntUUIDQueue(int startingValue)
```

Creates an empty reuse buffer with a caller-selected generation start.
Negative starts are accepted; overflow is not checked.

- **`startingValue`** — first sequential index when no returned indices exist

#### pop

```java
public int pop()
```

Reuses the newest buffered index when the buffer is partially occupied;
otherwise generates and increments the next sequential value. Arithmetic
wraps at the primitive limit. At full buffer capacity, the modulo-based
slot calculation produces minus one before membership is decremented.

**Returns:** buffered value or next sequential value

**Throws `ArrayIndexOutOfBoundsException`:** if the reuse buffer is exactly full

#### push

```java
public void push(int index)
```

Returns an index for later reuse unless it is greater than the next sequential
value. Equality is accepted and advances that counter; negatives and duplicate
returns are not rejected. Full storage grows by doubling before insertion.

- **`index`** — value to append to the reuse buffer

#### getCompactQueue

```java
public int[] getCompactQueue()
```

Copies the occupied reuse-buffer prefix in insertion order. Zero values and
duplicates are preserved; the method neither filters entries nor alters the
buffer. Modifying the result does not change subsequent allocation.

**Returns:** independent array of currently buffered values

</details>

<a id="type-longuuidqueue"></a>

### LongUUIDQueue

[Source](../../src/main/java/valthorne/collections/queue/LongUUIDQueue.java#L17)

Sequential long index generator with a reusable-index buffer. Callers return
indices with push; buffered values are normally reused in last-in, first-out
order before sequential generation resumes. The class does not track live
allocations or reject duplicate returns, so uniqueness depends on caller use.

Arithmetic follows primitive overflow rules. The current pop calculation
also rejects a completely full buffer with an array-index exception; it does
not implement a general circular queue. Operations are unsynchronized.

<details>
<summary>LongUUIDQueue operation reference (5 declarations)</summary>

#### Constructor

```java
public LongUUIDQueue()
```

Creates an empty reuse buffer and starts sequential generation at zero.
Sixteen returned-index slots are initially allocated.

#### Constructor

```java
public LongUUIDQueue(long startingValue)
```

Creates an empty reuse buffer with a caller-selected generation start.
Negative starts are accepted; overflow is not checked.

- **`startingValue`** — first sequential index when no returned indices exist

#### pop

```java
public long pop()
```

Reuses the newest buffered index when the buffer is partially occupied;
otherwise generates and increments the next sequential value. Arithmetic
wraps at the primitive limit. At full buffer capacity, the modulo-based
slot calculation produces minus one before membership is decremented.

**Returns:** buffered value or next sequential value

**Throws `ArrayIndexOutOfBoundsException`:** if the reuse buffer is exactly full

#### push

```java
public void push(long index)
```

Returns an index for later reuse unless it is greater than the next sequential
value. Equality is accepted and advances that counter; negatives and duplicate
returns are not rejected. Full storage grows by doubling before insertion.

- **`index`** — value to append to the reuse buffer

#### getCompactQueue

```java
public long[] getCompactQueue()
```

Copies the occupied reuse-buffer prefix in insertion order. Zero values and
duplicates are preserved; the method neither filters entries nor alters the
buffer. Modifying the result does not change subsequent allocation.

**Returns:** independent array of currently buffered values

</details>

<a id="type-shortuuidqueue"></a>

### ShortUUIDQueue

[Source](../../src/main/java/valthorne/collections/queue/ShortUUIDQueue.java#L17)

Sequential short index generator with a reusable-index buffer. Callers return
indices with push; buffered values are normally reused in last-in, first-out
order before sequential generation resumes. The class does not track live
allocations or reject duplicate returns, so uniqueness depends on caller use.

Arithmetic follows primitive overflow rules. The current pop calculation
also rejects a completely full buffer with an array-index exception; it does
not implement a general circular queue. Operations are unsynchronized.

<details>
<summary>ShortUUIDQueue operation reference (5 declarations)</summary>

#### Constructor

```java
public ShortUUIDQueue()
```

Creates an empty reuse buffer and starts sequential generation at zero.
Sixteen returned-index slots are initially allocated.

#### Constructor

```java
public ShortUUIDQueue(int startingValue)
```

Creates an empty reuse buffer with a caller-selected generation start.
The int is narrowed to short without a range check.

- **`startingValue`** — first sequential index when no returned indices exist

#### pop

```java
public short pop()
```

Reuses the newest buffered index when the buffer is partially occupied;
otherwise generates and increments the next sequential value. Arithmetic
wraps at the primitive limit. At full buffer capacity, the modulo-based
slot calculation produces minus one before membership is decremented.

**Returns:** buffered value or next sequential value

**Throws `ArrayIndexOutOfBoundsException`:** if the reuse buffer is exactly full

#### push

```java
public void push(short index)
```

Returns an index for later reuse unless it is greater than the next sequential
value. Equality is accepted and advances that counter; negatives and duplicate
returns are not rejected. Full storage grows by doubling before insertion.

- **`index`** — value to append to the reuse buffer

#### getCompactQueue

```java
public short[] getCompactQueue()
```

Copies the occupied reuse-buffer prefix in insertion order. Zero values and
duplicates are preserved; the method neither filters entries nor alters the
buffer. Modifying the result does not change subsequent allocation.

**Returns:** independent array of currently buffered values

</details>

<a id="type-intbinarytree"></a>

### IntBinaryTree

[Source](../../src/main/java/valthorne/collections/tree/IntBinaryTree.java#L20)

Array-backed integer search tree whose nodes refer to children by array index.
Greater values follow the left link and other values follow the right link;
search uses the same ordering. There is no balancing, removal, or synchronization.

The current root insertion does not increment the storage counter. Consequently,
a subsequent insertion overwrites the root slot and can discard the first value;
this implementation does not provide ordinary set-preservation semantics.

```java
IntBinaryTree tree = new IntBinaryTree();
tree.insert(7);
boolean found = tree.search(7);
```

<details>
<summary>IntBinaryTree operation reference (3 declarations)</summary>

#### insert

```java
public void insert(int value)
```

Adds a node by following the tree's greater-left ordering and expands storage
when full. The initial root path returns without advancing size; the next
insertion therefore replaces slot zero. Duplicate values are not rejected.

- **`value`** — integer to insert

#### search

```java
public boolean search(int value)
```

Searches for a given integer value in the binary tree.

- **`value`** — the integer value to search for

**Returns:** true if the value is found, false otherwise

#### toString

```java
@Override
    public String toString()
```

Returns a string representation of the binary tree.

**Returns:** a string representation of the binary tree

</details>

<a id="type-intbinarytree-node"></a>

### IntBinaryTree.Node — internal support type

[Source](../../src/main/java/valthorne/collections/tree/IntBinaryTree.java#L104)

Mutable array-stored tree node containing an integer and child indices.
Minus one denotes a missing child. Links refer to the enclosing tree's storage
rather than directly owning child objects, and insertion mutates those links.

<details>
<summary>IntBinaryTree.Node operation reference (2 declarations)</summary>

#### Constructor

```java
public Node(int value)
```

Constructs a new node with the given value.

- **`value`** — the value to be stored in the node

#### toString

```java
@Override
        public String toString()
```

Returns a string representation of the node.

**Returns:** a string representation of the node

</details>

## Related guides

- [Resizable and unordered arrays](arrays.md)
- [Primitive and generic stacks](stacks.md)
- [Object pooling](pooling.md)
