# Bit fields and flags

Author: Albert Beaupre

[System manual](README.md)

## Purpose

Bit-field wrappers store Boolean flags in an integral value. They are useful for compact state such as enabled/visible/pressed flags or a small set of options. Operations address bit positions or replace the complete backing pattern.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Individual flags | Set, clear, toggle, and test selected positions. |
| Whole patterns | Read or replace the full integral representation. |
| Width variants | Choose byte, short, int, or long according to the number of flags. |
| Generic contract | Bits provides the shared abstraction used by concrete wrappers. |

## Getting started

1. Assign named constants to bit positions so callers do not use unexplained numeric indices.
2. Choose a wrapper wide enough for every flag.
3. Use individual operations for state changes and complete-pattern access for serialization or bulk replacement.
4. Validate positions according to the concrete wrapper's supported range.

## Ownership and lifecycle

These wrappers are mutable values, not atomic synchronization primitives. The sign bit is still a usable bit pattern where the API allows it.

## Important behavior

- Bit positions and already-shifted masks are different inputs.
- Replacing all bits can clear flags set by another subsystem.
- Do not share mutation across threads without an explicit synchronization design.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`Bits`](#type-bits)
- [`ByteBits`](#type-bytebits)
- [`IntBits`](#type-intbits)
- [`LongBits`](#type-longbits)
- [`ShortBits`](#type-shortbits)

<a id="type-bits"></a>

### Bits

[Source](../../src/main/java/valthorne/collections/bits/Bits.java#L84)

##### Bits

`Bits` is a dynamic bit container backed by a `long[]` where each
`long` stores 64 individual bit states. This class is intended for
compact, fast, low-level bit manipulation when you want direct control over
bit storage without the heavier behavior of higher-level abstractions.

Internally, bit indices are mapped into the `words` array using
64-bit word segmentation:

- The word index is computed with `index >> 6`

- The bit mask is computed with `1L << index`

- Each word stores the enabled/disabled state of 64 consecutive bits

This class supports:

- setting and clearing individual bits

- reading individual bit states

- finding the next set bit

- finding the next clear bit

- resizing the underlying storage

- serializing to and from little-endian byte arrays

The `size` field tracks how many bits are currently enabled, not the
total storage capacity. That means `size()` returns the number of set
bits currently stored in this container.

This implementation is intentionally lightweight and performance-oriented.
It does not validate every possible incorrect input and does not attempt to
provide the same safety guarantees as `java.util.BitSet`. It is better
suited for internal engine code, data structures, masks, occupancy flags,
serialization helpers, and other systems where raw speed and compact storage
matter more than defensive guardrails.

##### Example

```java
Bits bits = new Bits(128);

bits.set(0);
bits.set(5);
bits.set(70);

System.out.println(bits.get(0));   // true
System.out.println(bits.get(1));   // false

bits.clear(5);

int nextSet = bits.nextSetBit(0);      // 0
int nextClear = bits.nextClearBit(0);  // usually 1

byte[] raw = bits.toByteArray();
Bits restored = Bits.valueOf(raw);

System.out.println(restored);
System.out.println(restored.size());
```

<details>
<summary>Bits operation reference (13 declarations)</summary>

#### Constructor

```java
public Bits(int size)
```

Creates a new `Bits` container with enough internal storage to
address the requested number of bit positions.

The backing `words` array is sized by rounding the requested bit
count up to the nearest 64-bit word boundary. No bits are enabled during
construction, so the initial set-bit count remains zero.

Examples:

- `new Bits(1)` allocates one 64-bit word

- `new Bits(64)` allocates one 64-bit word

- `new Bits(65)` allocates two 64-bit words

- **`size`** — the initial logical bit capacity to support

#### valueOf

```java
public static Bits valueOf(byte[] byteArray)
```

Creates a new `Bits` instance from a raw byte array.

The byte array is interpreted in little-endian bit order. Each byte
contributes 8 bits, and each set source bit is copied into the returned
`Bits` object at the corresponding absolute bit index.

This method constructs a `Bits` instance sized to
`byteArray.length * 8`, then replays all enabled bits through
`set(int)` so the internal set-bit count is built correctly.

- **`byteArray`** — the raw byte data to decode into a bit container

**Returns:** a new `Bits` instance representing the given bytes

#### set

```java
public void set(int index)
```

Enables the bit at the given index.

If the requested bit lies outside the currently allocated word range,
the backing array is expanded by doubling its length. After capacity
has been ensured, the target word and bit mask are computed and the
bit is enabled.

The set-bit count `size` is only incremented when the bit was
previously clear. Calling this method on an already enabled bit leaves
the count unchanged.

- **`index`** — the zero-based bit index to enable

#### clear

```java
public void clear(int index)
```

Clears the bit at the given index.

If the requested bit lies outside the current backing storage, the method
returns immediately because that bit is already effectively clear.

If the target bit exists and is currently enabled, this method disables it
and decrements the tracked set-bit count `size`. Clearing a bit that
is already clear does nothing.

- **`index`** — the zero-based bit index to clear

#### set

```java
public void set(int index, boolean set)
```

Sets or clears the bit at the given index depending on the provided flag.

This is a convenience overload that forwards to `set(int)` when
`set` is true, and to `clear(int)` when `set` is false.

- **`index`** — the zero-based bit index to modify
- **`set`** — true to enable the bit, false to clear it

#### get

```java
public boolean get(int index)
```

Returns whether the bit at the given index is currently enabled.

If the requested index maps outside the allocated word range, this method
returns `false`. Otherwise it checks the target word using a bit mask.

- **`index`** — the zero-based bit index to inspect

**Returns:** true if the bit is enabled, otherwise false

#### nextSetBit

```java
public int nextSetBit(int fromIndex)
```

Finds the next enabled bit starting at or after the supplied index.

This method performs an efficient forward scan. It first checks the word
containing `fromIndex` by shifting away all bits before the requested
starting position. If any enabled bits remain, the exact index is found
with `Long#numberOfTrailingZeros(long)`.

If no set bit exists in that partially scanned word, later words are
checked one by one until a non-zero word is found.

- **`fromIndex`** — the first index that is allowed to match

**Returns:** the index of the next enabled bit, or `-1` if none exists

#### nextClearBit

```java
public int nextClearBit(int fromIndex)
```

Finds the next clear bit starting at or after the supplied index.

This method mirrors `nextSetBit(int)` but searches for disabled bits
instead of enabled bits. It inverts scanned words so clear bits become
detectable through the same trailing-zero scan approach.

If the requested starting word lies beyond the current backing array, the
original implementation returns the smaller of the requested index and the
current total bit capacity represented by `words.length << 6`. That
behavior is preserved here exactly.

- **`fromIndex`** — the first index that is allowed to match

**Returns:** the index of the next clear bit, or `-1` if none is found

#### clearAll

```java
public void clearAll()
```

Clears all stored bits in this container.

This method fills the entire backing word array with zeroes, making every
tracked bit disabled.

This implementation preserves your original logic exactly and therefore does
not update `size`. That means the tracked set-bit count may no longer
reflect the actual number of enabled bits after this method runs unless the
count is rebuilt separately.

#### resizeTo

```java
public void resizeTo(int size)
```

Resizes the internal backing storage to support the requested logical bit range.

This method resizes the `words` array to exactly the number of 64-bit
words needed to address the supplied size. Existing data is preserved up to the
new array length.

If the new size is smaller than the current capacity, higher words may be
truncated. This method does not recompute `size`, so the stored count
remains whatever it was before resizing.

- **`size`** — the new logical bit capacity to support

#### size

```java
public int size()
```

Returns the number of currently enabled bits.

This value is not the total addressable capacity. It is the tracked count of
bits currently set to `true`.

**Returns:** the number of enabled bits currently tracked by this container

#### toByteArray

```java
public byte[] toByteArray()
```

Converts this bit container into a little-endian byte array.

The produced byte array contains the raw word data in little-endian order.
Full words are written directly, and the final word contributes only as many
bytes as are needed to represent its non-zero trailing content.

This is useful for compact persistence, binary transport, custom save formats,
and restoring state later through `valueOf(byte[])`.

**Returns:** a little-endian byte array representing the current bit contents

#### toString

```java
@Override
    public String toString()
```

Builds a human-readable binary representation of the internal word array.

Each word is rendered as a 64-character binary segment padded with leading
zeroes so the storage layout is easy to inspect visually during debugging.

The output format is:

```java
Bits[ 0000...0001 0000...0100 ]
```

**Returns:** a string representation of all backing words in binary form

</details>

<a id="type-bytebits"></a>

### ByteBits

[Source](../../src/main/java/valthorne/collections/bits/ByteBits.java#L68)

##### ByteBits

`ByteBits` is a compact bit container backed by a single `byte`. It is useful when
you need to store up to eight on/off flags in one small value while still being able to query,
modify, compare, and scan those flags through a readable object-oriented API.

Each bit index maps directly to one bit inside the internal byte:

- Index `0` is the least significant bit

- Index `7` is the most significant bit

This class is appropriate for cases such as:

- storing small permission masks

- tracking component or state flags

- encoding boolean toggles compactly

- working with binary protocols that use one byte of flags

##### Behavior notes

All bit operations in this class are restricted to the valid byte index range of `0..7`.
Any attempt to access a bit outside that range throws an `IndexOutOfBoundsException`.
This makes the API safer and prevents accidental misuse that would otherwise silently operate on
values outside the intended byte-width contract.

##### Example

```java
ByteBits flags = new ByteBits();

flags.set(0);
flags.set(3);
flags.set(7, true);

boolean hasBit3 = flags.get(3);
int firstSet = flags.nextSetBit(0);
int firstClear = flags.nextClearBit(0);
int count = flags.size();

System.out.println(flags);        // Example: 10001001
System.out.println(hasBit3);      // true
System.out.println(firstSet);     // 0
System.out.println(firstClear);   // 1
System.out.println(count);        // 3

flags.flip(3);
flags.clear(7);

ByteBits required = new ByteBits((byte) 0b00000001);
boolean containsRequired = flags.allMatch(required);
```

<details>
<summary>ByteBits operation reference (20 declarations)</summary>

#### Constructor

```java
public ByteBits(byte bits)
```

Creates a new `ByteBits` instance initialized with the provided raw byte value.

This constructor is useful when you already have a packed flag byte from another source,
such as file data, network data, serialization, or a previously computed bit mask.

- **`bits`** — the initial raw bit pattern to store

#### Constructor

```java
public ByteBits()
```

Creates a new `ByteBits` instance with all bits cleared.

After construction, every bit from index `0` through `7` is unset.

#### get

```java
public boolean get(int index)
```

Returns whether the bit at the specified index is currently set.

The provided index must be in the inclusive range `0..7`. This method checks the
corresponding bit in the internal byte and returns `true` when that bit is `1`.

- **`index`** — the bit index to read

**Returns:** `true` if the bit is set, otherwise `false`

**Throws `IndexOutOfBoundsException`:** if `index` is outside `0..7`

#### set

```java
public void set(int index)
```

Sets the bit at the specified index to `1`.

This operation preserves every other bit and only enables the requested flag.

- **`index`** — the bit index to set

**Throws `IndexOutOfBoundsException`:** if `index` is outside `0..7`

#### clear

```java
public void clear(int index)
```

Clears the bit at the specified index to `0`.

This operation preserves every other bit and only disables the requested flag.

- **`index`** — the bit index to clear

**Throws `IndexOutOfBoundsException`:** if `index` is outside `0..7`

#### set

```java
public void set(int index, boolean set)
```

Sets or clears the bit at the specified index based on the supplied boolean value.

When `set` is `true`, this behaves the same as `set(int)`.
When `set` is `false`, this behaves the same as `clear(int)`.

- **`index`** — the bit index to modify
- **`set`** — `true` to set the bit, `false` to clear it

**Throws `IndexOutOfBoundsException`:** if `index` is outside `0..7`

#### toString

```java
@Override
    public String toString()
```

Returns a binary string representation of the stored bits.

Unlike `Integer#toBinaryString(int)` alone, this method always returns a fixed-width
8-character binary string so the full byte pattern is visible, including leading zeroes.

**Returns:** an 8-character binary representation of the current bit state

#### isEmpty

```java
public boolean isEmpty()
```

Returns whether all bits in this container are currently cleared.

This is equivalent to checking whether the raw byte value is zero.

**Returns:** `true` if no bits are set, otherwise `false`

#### size

```java
public int size()
```

Returns the number of set bits currently stored in this byte.

This method counts how many of the eight bit positions currently contain `1`.

**Returns:** the number of enabled bits

#### clearAll

```java
public void clearAll()
```

Clears every bit in this container.

After this call, `isEmpty()` will return `true`.

#### flip

```java
public void flip(int index)
```

Flips the bit at the specified index.

If the bit is currently set, it becomes cleared. If it is currently cleared, it becomes set.

- **`index`** — the bit index to flip

**Throws `IndexOutOfBoundsException`:** if `index` is outside `0..7`

#### nextClearBit

```java
public int nextClearBit(int fromIndex)
```

Finds the index of the next clear bit starting at the supplied index.

The search begins at `fromIndex` and proceeds upward through the byte. If a clear bit
is found, its index is returned immediately. If no clear bit exists in the remaining range,
this method returns `-1`.

- **`fromIndex`** — the starting bit index for the scan

**Returns:** the index of the next clear bit, or `-1` if none is found

**Throws `IndexOutOfBoundsException`:** if `fromIndex` is outside `0..7`

#### nextSetBit

```java
public int nextSetBit(int fromIndex)
```

Finds the index of the next set bit starting at the supplied index.

The search begins at `fromIndex` and proceeds upward through the byte. If a set bit
is found, its index is returned immediately. If no set bit exists in the remaining range,
this method returns `-1`.

- **`fromIndex`** — the starting bit index for the scan

**Returns:** the index of the next set bit, or `-1` if none is found

**Throws `IndexOutOfBoundsException`:** if `fromIndex` is outside `0..7`

#### anyMatch

```java
public boolean anyMatch(ByteBits other)
```

Returns whether this instance shares at least one set bit with another `ByteBits`.

This performs a bitwise AND between the two values and checks whether the result is non-zero.
It is useful when you want to know whether two flag sets overlap at all.

- **`other`** — the other bit container to compare against

**Returns:** `true` if both instances have at least one common set bit, otherwise `false`

**Throws `NullPointerException`:** if `other` is null

#### anyMatch

```java
public boolean anyMatch(byte bits)
```

Returns whether this instance shares at least one set bit with the supplied raw byte.

This is the raw-byte overload of `anyMatch(ByteBits)`.

- **`bits`** — the raw bit pattern to compare against

**Returns:** `true` if at least one corresponding bit is set in both values

#### allMatch

```java
public boolean allMatch(ByteBits bits)
```

Returns whether all set bits in the supplied `ByteBits` are also set in this instance.

This method does not require the two byte values to be identical. It only checks whether this
instance fully contains the bit mask represented by the supplied argument.

- **`bits`** — the bit container whose set bits must all be present in this instance

**Returns:** `true` if every set bit in `bits` is also set here

**Throws `NullPointerException`:** if `bits` is null

#### allMatch

```java
public boolean allMatch(byte bits)
```

Returns whether all set bits in the supplied raw byte are also set in this instance.

This is the raw-byte overload of `allMatch(ByteBits)`.

- **`bits`** — the raw bit mask to test for containment

**Returns:** `true` if every set bit in `bits` is also set in this instance

#### matches

```java
public boolean matches(ByteBits other)
```

Returns whether this instance stores exactly the same raw bit pattern as another
`ByteBits` instance.

This is a strict equality check on the internal byte value.

- **`other`** — the other bit container to compare against

**Returns:** `true` if both instances have identical bit patterns

**Throws `NullPointerException`:** if `other` is null

#### matches

```java
public boolean matches(byte bits)
```

Returns whether this instance stores exactly the same raw bit pattern as the supplied byte.

- **`bits`** — the raw byte to compare against

**Returns:** `true` if the stored byte matches the supplied byte exactly

#### getBits

```java
public byte getBits()
```

Returns the raw byte value currently stored by this instance.

This method is useful when you need to serialize, transmit, or otherwise work directly with the
packed byte representation.

**Returns:** the stored raw bit byte

</details>

<a id="type-intbits"></a>

### IntBits

[Source](../../src/main/java/valthorne/collections/bits/IntBits.java#L52)

##### IntBits

`IntBits` is a compact bit container backed by a single `int`. It exposes a readable
API for working with up to 32 independent binary flags inside one integer value.

This class is useful when you need a fast and memory-efficient mask for things such as:

- entity flags

- UI state masks

- render feature toggles

- permission sets

- compact save or packet fields

Bit index `0` maps to the least significant bit and bit index `31` maps to the most
significant bit. All index-based methods validate the supplied index to ensure operations stay
inside the valid integer bit range.

##### Example

```java
IntBits flags = new IntBits();

flags.set(1);
flags.set(4);
flags.flip(4);
flags.set(10, true);

boolean hasBit1 = flags.get(1);
int count = flags.size();
int nextSet = flags.nextSetBit(0);

IntBits required = new IntBits();
required.set(1);

boolean exact = flags.matches(required);
boolean overlap = flags.anyMatch(required);
boolean contains = flags.allMatch(required);
```

<details>
<summary>IntBits operation reference (23 declarations)</summary>

#### Constructor

```java
public IntBits()
```

Creates a new `IntBits` instance with all bits initially cleared.

#### Constructor

```java
public IntBits(int bits)
```

Creates a new `IntBits` instance initialized with the supplied raw bit pattern.

- **`bits`** — the initial raw integer mask

#### get

```java
public boolean get(int index)
```

Returns whether the bit at the specified index is set.

- **`index`** — the bit index to query

**Returns:** `true` if that bit is enabled, otherwise `false`

**Throws `IndexOutOfBoundsException`:** if `index` is outside `0..31`

#### set

```java
public void set(int index)
```

Sets the bit at the specified index to `1`.

- **`index`** — the bit index to set

**Throws `IndexOutOfBoundsException`:** if `index` is outside `0..31`

#### clear

```java
public void clear(int index)
```

Clears the bit at the specified index to `0`.

- **`index`** — the bit index to clear

**Throws `IndexOutOfBoundsException`:** if `index` is outside `0..31`

#### set

```java
public void set(int index, boolean set)
```

Sets or clears the bit at the specified index based on the supplied boolean value.

- **`index`** — the bit index to modify
- **`set`** — `true` to set the bit, `false` to clear it

**Throws `IndexOutOfBoundsException`:** if `index` is outside `0..31`

#### toString

```java
@Override
    public String toString()
```

Returns a fixed-width 32-bit binary string representation of the current bit pattern.

**Returns:** a 32-character binary string

#### isEmpty

```java
public boolean isEmpty()
```

Returns whether all bits are currently cleared.

**Returns:** `true` if the stored mask is zero

#### size

```java
public int size()
```

Returns the number of set bits currently stored in this container.

**Returns:** the population count of the current mask

#### clearAll

```java
public void clearAll()
```

Clears every bit in this container.

#### flip

```java
public void flip(int index)
```

Toggles the bit at the specified index.

- **`index`** — the bit index to flip

**Throws `IndexOutOfBoundsException`:** if `index` is outside `0..31`

#### xor

```java
public void xor(IntBits other)
```

Applies a bitwise XOR operation using another `IntBits` instance.

- **`other`** — the other bit container

**Throws `NullPointerException`:** if `other` is null

#### and

```java
public void and(IntBits other)
```

Applies a bitwise AND operation using another `IntBits` instance.

- **`other`** — the other bit container

**Throws `NullPointerException`:** if `other` is null

#### or

```java
public void or(IntBits other)
```

Applies a bitwise OR operation using another `IntBits` instance.

- **`other`** — the other bit container

**Throws `NullPointerException`:** if `other` is null

#### clone

```java
@Override
    public IntBits clone()
```

Creates and returns a copy of this `IntBits` instance.

**Returns:** a new `IntBits` containing the same bit pattern

#### nextClearBit

```java
public int nextClearBit(int fromIndex)
```

Finds the next clear bit starting at the supplied index.

- **`fromIndex`** — the starting index for the scan

**Returns:** the next clear bit index, or `-1` if none is found

**Throws `IndexOutOfBoundsException`:** if `fromIndex` is outside `0..31`

#### nextSetBit

```java
public int nextSetBit(int fromIndex)
```

Finds the next set bit starting at the supplied index.

- **`fromIndex`** — the starting index for the scan

**Returns:** the next set bit index, or `-1` if none is found

**Throws `IndexOutOfBoundsException`:** if `fromIndex` is outside `0..31`

#### matches

```java
public boolean matches(IntBits other)
```

Returns whether this instance stores exactly the same bit pattern as another `IntBits`.

- **`other`** — the other bit container to compare against

**Returns:** `true` if both masks are identical

**Throws `NullPointerException`:** if `other` is null

#### matches

```java
public boolean matches(int bits)
```

Returns whether this instance stores exactly the same raw bit pattern as the supplied int.

- **`bits`** — the raw integer mask to compare against

**Returns:** `true` if the stored mask matches exactly

#### allMatch

```java
public boolean allMatch(IntBits other)
```

Returns whether all set bits in the supplied `IntBits` are also set in this instance.

- **`other`** — the mask whose set bits must all be present here

**Returns:** `true` if this instance contains the supplied mask

**Throws `NullPointerException`:** if `other` is null

#### allMatch

```java
public boolean allMatch(int bits)
```

Returns whether all set bits in the supplied raw int are also set in this instance.

- **`bits`** — the raw integer mask to test for containment

**Returns:** `true` if this instance contains that mask

#### anyMatch

```java
public boolean anyMatch(IntBits other)
```

Returns whether this instance shares at least one set bit with another `IntBits`.

- **`other`** — the other bit container to compare against

**Returns:** `true` if both masks overlap on at least one set bit

**Throws `NullPointerException`:** if `other` is null

#### getBits

```java
public int getBits()
```

Returns the raw integer bit mask stored by this container.

**Returns:** the packed integer bit value

</details>

<a id="type-longbits"></a>

### LongBits

[Source](../../src/main/java/valthorne/collections/bits/LongBits.java#L73)

##### LongBits

`LongBits` is a compact bit container backed by a single `long`. It allows you to
work with up to 64 binary flags inside one primitive value while exposing a readable API for
querying, modifying, scanning, combining, and comparing those flags.

This class is useful when you need a lightweight flag set for things such as:

- entity state flags

- component masks

- permission masks

- binary protocol fields

- compact runtime feature toggles

Each bit index maps directly to one position inside the internal `long`:
index `0` is the least significant bit and index `63` is the most significant bit.
Every operation in this class validates the bit index so invalid positions fail fast instead of
silently producing incorrect results.

##### Behavior overview

- `get(int)` reads a bit

- `set(int)` and `clear(int)` modify individual bits

- `flip(int)` toggles a bit

- `xor(LongBits)`, `and(LongBits)`, and `or(LongBits)` combine masks

- `nextSetBit(int)` and `nextClearBit(int)` scan forward through the bit set

- `matches(LongBits)`, `allMatch(LongBits)`, and `anyMatch(LongBits)`
support exact, contained, and overlapping comparisons

##### Example

```java
LongBits flags = new LongBits();

flags.set(0);
flags.set(5);
flags.set(63);

boolean enabled = flags.get(5);
int count = flags.size();
int nextSet = flags.nextSetBit(0);
int nextClear = flags.nextClearBit(0);

LongBits required = new LongBits();
required.set(0);
required.set(5);

boolean containsAll = flags.allMatch(required);
boolean overlaps = flags.anyMatch(required);

System.out.println(flags);        // 64-bit binary string
System.out.println(enabled);      // true
System.out.println(count);        // 3
System.out.println(nextSet);      // 0
System.out.println(nextClear);    // 1
System.out.println(containsAll);  // true
System.out.println(overlaps);     // true
```

<details>
<summary>LongBits operation reference (23 declarations)</summary>

#### Constructor

```java
public LongBits()
```

Creates a new `LongBits` instance with all bits initially cleared.

After construction, every bit from index `0` through `63` is unset.

#### Constructor

```java
public LongBits(long bits)
```

Creates a new `LongBits` instance initialized with the supplied raw bit pattern.

This constructor is useful when you already have a packed `long` mask from serialized
data, a native system, a save format, or another bit-oriented API.

- **`bits`** — the initial raw bit pattern to store

#### get

```java
public boolean get(int index)
```

Returns whether the bit at the specified index is currently set.

The supplied index must be in the inclusive range `0..63`. This method checks that
position in the stored `long` and returns `true` when the bit is `1`.

- **`index`** — the bit index to query

**Returns:** `true` if the bit is set, otherwise `false`

**Throws `IndexOutOfBoundsException`:** if `index` is outside `0..63`

#### set

```java
public void set(int index)
```

Sets the bit at the specified index to `1`.

All other bits remain unchanged.

- **`index`** — the bit index to set

**Throws `IndexOutOfBoundsException`:** if `index` is outside `0..63`

#### clear

```java
public void clear(int index)
```

Clears the bit at the specified index to `0`.

All other bits remain unchanged.

- **`index`** — the bit index to clear

**Throws `IndexOutOfBoundsException`:** if `index` is outside `0..63`

#### set

```java
public void set(int index, boolean set)
```

Sets or clears the bit at the specified index depending on the supplied boolean value.

This is a convenience overload that routes to `set(int)` when `set` is true
and `clear(int)` when `set` is false.

- **`index`** — the bit index to modify
- **`set`** — `true` to set the bit, `false` to clear it

**Throws `IndexOutOfBoundsException`:** if `index` is outside `0..63`

#### toString

```java
@Override
    public String toString()
```

Returns a fixed-width binary string representation of the current bit pattern.

This method always returns all 64 bit positions, including leading zeroes, so the entire
state of the mask is visible.

**Returns:** a 64-character binary string

#### isEmpty

```java
public boolean isEmpty()
```

Returns whether all bits in this container are currently cleared.

**Returns:** `true` if the stored value is zero, otherwise `false`

#### size

```java
public int size()
```

Returns the number of set bits currently stored in this container.

This counts how many bit positions currently contain `1`.

**Returns:** the population count of the current mask

#### clearAll

```java
public void clearAll()
```

Clears every bit in this container.

After this call, the stored mask becomes zero.

#### flip

```java
public void flip(int index)
```

Toggles the bit at the specified index.

A set bit becomes clear and a clear bit becomes set.

- **`index`** — the bit index to flip

**Throws `IndexOutOfBoundsException`:** if `index` is outside `0..63`

#### xor

```java
public void xor(LongBits other)
```

Applies a bitwise XOR operation using another `LongBits` instance.

Every differing bit between the two masks becomes set and every matching bit becomes clear.

- **`other`** — the other bit container to XOR with

**Throws `NullPointerException`:** if `other` is null

#### and

```java
public void and(LongBits other)
```

Applies a bitwise AND operation using another `LongBits` instance.

Only bits that are set in both masks remain set afterward.

- **`other`** — the other bit container to AND with

**Throws `NullPointerException`:** if `other` is null

#### or

```java
public void or(LongBits other)
```

Applies a bitwise OR operation using another `LongBits` instance.

Any bit set in either mask becomes set in this instance afterward.

- **`other`** — the other bit container to OR with

**Throws `NullPointerException`:** if `other` is null

#### clone

```java
@Override
    public LongBits clone()
```

Creates and returns a copy of this `LongBits` instance.

The returned object stores the same raw bit pattern but is fully independent from the
original instance.

**Returns:** a new `LongBits` containing the same bit pattern

#### nextClearBit

```java
public int nextClearBit(int fromIndex)
```

Finds the next clear bit starting at the specified index.

The scan begins at `fromIndex` and proceeds upward toward bit `63`. If no clear
bit exists in that range, this method returns `-1`.

- **`fromIndex`** — the first index to inspect

**Returns:** the index of the next clear bit, or `-1` if none exists

**Throws `IndexOutOfBoundsException`:** if `fromIndex` is outside `0..63`

#### nextSetBit

```java
public int nextSetBit(int fromIndex)
```

Finds the next set bit starting at the specified index.

The scan begins at `fromIndex` and proceeds upward toward bit `63`. If no set
bit exists in that range, this method returns `-1`.

- **`fromIndex`** — the first index to inspect

**Returns:** the index of the next set bit, or `-1` if none exists

**Throws `IndexOutOfBoundsException`:** if `fromIndex` is outside `0..63`

#### matches

```java
public boolean matches(LongBits other)
```

Returns whether this instance stores exactly the same raw bit pattern as another
`LongBits` instance.

- **`other`** — the other bit container to compare against

**Returns:** `true` if both instances contain the exact same bits

**Throws `NullPointerException`:** if `other` is null

#### matches

```java
public boolean matches(long bits)
```

Returns whether this instance stores exactly the same raw bit pattern as the supplied long.

- **`bits`** — the raw bit pattern to compare against

**Returns:** `true` if the stored mask matches exactly

#### allMatch

```java
public boolean allMatch(LongBits bits)
```

Returns whether all set bits in the supplied `LongBits` are also set in this instance.

This checks containment, not exact equality.

- **`bits`** — the mask whose set bits must all exist in this instance

**Returns:** `true` if this instance fully contains the supplied mask

**Throws `NullPointerException`:** if `bits` is null

#### allMatch

```java
public boolean allMatch(long bits)
```

Returns whether all set bits in the supplied raw long are also set in this instance.

- **`bits`** — the raw bit mask to test for containment

**Returns:** `true` if this instance contains the supplied mask

#### anyMatch

```java
public boolean anyMatch(LongBits other)
```

Returns whether this instance shares at least one set bit with another `LongBits`.

This is useful when you only care whether two masks overlap at all.

- **`other`** — the other bit container to compare against

**Returns:** `true` if at least one bit is set in both masks

**Throws `NullPointerException`:** if `other` is null

#### getBits

```java
public long getBits()
```

Returns the raw long value currently stored by this container.

**Returns:** the packed long bit mask

</details>

<a id="type-shortbits"></a>

### ShortBits

[Source](../../src/main/java/valthorne/collections/bits/ShortBits.java#L56)

##### ShortBits

`ShortBits` is a compact bit container backed by a single `short`. It allows you to
store and manipulate up to 16 individual binary flags inside one short value while exposing an
easy-to-read utility-style API.

This class is useful when you want something smaller than an integer mask but still need direct
control over bit-level state, such as:

- small protocol fields

- tile or chunk flags

- limited feature masks

- compact state values

Bit indices range from `0` through `15`. All index-based methods validate the
requested index so invalid bit positions fail immediately.

##### Example

```java
ShortBits flags = new ShortBits();

flags.set(0);
flags.set(7);
flags.set(15);

boolean topBit = flags.get(15);
int count = flags.size();

ShortBits required = new ShortBits();
required.set(0);
required.set(7);

boolean containsAll = flags.allMatch(required);
boolean overlaps = flags.anyMatch(required);

System.out.println(flags);
System.out.println(topBit);
System.out.println(count);
System.out.println(containsAll);
System.out.println(overlaps);
```

<details>
<summary>ShortBits operation reference (20 declarations)</summary>

#### Constructor

```java
public ShortBits(short bits)
```

Creates a new `ShortBits` instance initialized with the supplied raw short value.

- **`bits`** — the initial raw bit pattern to store

#### Constructor

```java
public ShortBits()
```

Creates a new `ShortBits` instance with all bits initially cleared.

#### get

```java
public boolean get(int index)
```

Returns whether the bit at the specified index is currently set.

- **`index`** — the bit index to query

**Returns:** `true` if the bit is set, otherwise `false`

**Throws `IndexOutOfBoundsException`:** if `index` is outside `0..15`

#### set

```java
public void set(int index)
```

Sets the bit at the specified index to `1`.

- **`index`** — the bit index to set

**Throws `IndexOutOfBoundsException`:** if `index` is outside `0..15`

#### clear

```java
public void clear(int index)
```

Clears the bit at the specified index to `0`.

- **`index`** — the bit index to clear

**Throws `IndexOutOfBoundsException`:** if `index` is outside `0..15`

#### set

```java
public void set(int index, boolean set)
```

Sets or clears the bit at the specified index based on the supplied boolean value.

- **`index`** — the bit index to modify
- **`set`** — `true` to set the bit, `false` to clear it

**Throws `IndexOutOfBoundsException`:** if `index` is outside `0..15`

#### toString

```java
@Override
    public String toString()
```

Returns a fixed-width 16-bit binary string representation of the current short mask.

**Returns:** a 16-character binary string

#### isEmpty

```java
public boolean isEmpty()
```

Returns whether all bits are currently cleared.

**Returns:** `true` if the stored mask is zero

#### size

```java
public int size()
```

Returns the number of set bits currently stored in this container.

**Returns:** the population count of the current mask

#### clearAll

```java
public void clearAll()
```

Clears every bit in this container.

#### flip

```java
public void flip(int index)
```

Toggles the bit at the specified index.

- **`index`** — the bit index to flip

**Throws `IndexOutOfBoundsException`:** if `index` is outside `0..15`

#### nextClearBit

```java
public int nextClearBit(int fromIndex)
```

Finds the next clear bit starting at the supplied index.

- **`fromIndex`** — the starting index for the scan

**Returns:** the next clear bit index, or `-1` if none is found

**Throws `IndexOutOfBoundsException`:** if `fromIndex` is outside `0..15`

#### nextSetBit

```java
public int nextSetBit(int fromIndex)
```

Finds the next set bit starting at the supplied index.

- **`fromIndex`** — the starting index for the scan

**Returns:** the next set bit index, or `-1` if none is found

**Throws `IndexOutOfBoundsException`:** if `fromIndex` is outside `0..15`

#### matches

```java
public boolean matches(ShortBits other)
```

Returns whether this instance stores exactly the same bit pattern as another `ShortBits`.

- **`other`** — the other bit container to compare against

**Returns:** `true` if both instances have identical bit patterns

**Throws `NullPointerException`:** if `other` is null

#### matches

```java
public boolean matches(short bits)
```

Returns whether this instance stores exactly the same raw bit pattern as the supplied short.

- **`bits`** — the raw short mask to compare against

**Returns:** `true` if the stored mask matches exactly

#### allMatch

```java
public boolean allMatch(ShortBits bits)
```

Returns whether all set bits in the supplied `ShortBits` are also set in this instance.

- **`bits`** — the mask whose set bits must all be present here

**Returns:** `true` if this instance contains the supplied mask

**Throws `NullPointerException`:** if `bits` is null

#### allMatch

```java
public boolean allMatch(short bits)
```

Returns whether all set bits in the supplied raw short are also set in this instance.

- **`bits`** — the raw short mask to test for containment

**Returns:** `true` if this instance contains the supplied mask

#### anyMatch

```java
public boolean anyMatch(ShortBits other)
```

Returns whether this instance shares at least one set bit with another `ShortBits`.

- **`other`** — the other bit container to compare against

**Returns:** `true` if both masks overlap on at least one set bit

**Throws `NullPointerException`:** if `other` is null

#### getBits

```java
public short getBits()
```

Returns the raw short value currently stored by this container.

**Returns:** the packed short bit mask

#### setBits

```java
public void setBits(short bits)
```

Replaces all sixteen stored flags with the supplied bit pattern. No individual
bit positions are validated, and the sign bit is retained like any other flag.

- **`bits`** — complete replacement bit pattern

</details>

## Related guides

- [Binary buffers and byte order](buffers.md)
- [Math and 2D geometry](math.md)
