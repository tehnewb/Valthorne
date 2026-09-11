# Resizable and unordered arrays

Author: Albert Beaupre

[System manual](README.md)

## Purpose

Two array families serve different storage models. Array and its primitive counterparts provide indexed storage that grows when set writes beyond its length; they do not track a separate logical element count. SwapOnRemove variants are collections with add/remove and a logical size, using the last element to fill a removed slot.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Indexed growth | `set(index, value)` can allocate through index plus one, preserving earlier values and default-filling gaps. |
| Length | For ordinary Array variants, length is allocated storage, including untouched slots. |
| Collection size | SwapOnRemove variants track the number of added elements separately from capacity. |
| Unordered removal | Removing an element moves the last active element into its slot. |
| Primitive variants | Store numeric and character values without boxing. |

## Getting started

1. Choose indexed storage when slot positions matter, or SwapOnRemove storage when you need add/remove and can tolerate reordering.
2. For indexed storage, write using set and read indices below length; reads do not grow storage.
3. For SwapOnRemove collections, iterate only the logical size and update external indices when removal moves an element.
4. Reacquire exposed backing arrays after growth; an earlier array can remain valid but no longer represent current storage.

## Usage example

Place these statements in your initialization or application method; imports belong at the top of the Java file.

```java
import valthorne.collections.array.IntArray;
import valthorne.collections.array.SwapOnRemoveIntArray;

IntArray slots = new IntArray(2);
slots.set(4, 25);
int allocatedLength = slots.length(); // 5, including untouched zero slots.

SwapOnRemoveIntArray active = new SwapOnRemoveIntArray();
active.add(10);
active.add(20);
active.remove(0); // Moves the last active value into slot zero.
```

## Ownership and lifecycle

Object arrays store references rather than owning application resources. Clearing a collection does not generally dispose the objects it contained. Backing-array access, when exposed, is live storage.

## Important behavior

- Ordinary Array variants have no append/remove collection contract and no count of explicitly assigned slots.
- Sparse writes allocate intermediate storage; a large index is not a sparse-map entry.
- Swap removal invalidates stable ordering and can change another item's index.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`Array`](#type-array)
- [`ByteArray`](#type-bytearray)
- [`CharArray`](#type-chararray)
- [`DoubleArray`](#type-doublearray)
- [`FloatArray`](#type-floatarray)
- [`IntArray`](#type-intarray)
- [`LongArray`](#type-longarray)
- [`ShortArray`](#type-shortarray)
- [`SwapOnRemoveArray`](#type-swaponremovearray)
- [`SwapOnRemoveByteArray`](#type-swaponremovebytearray)
- [`SwapOnRemoveCharArray`](#type-swaponremovechararray)
- [`SwapOnRemoveDoubleArray`](#type-swaponremovedoublearray)
- [`SwapOnRemoveFloatArray`](#type-swaponremovefloatarray)
- [`SwapOnRemoveIntArray`](#type-swaponremoveintarray)
- [`SwapOnRemoveLongArray`](#type-swaponremovelongarray)
- [`SwapOnRemoveShortArray`](#type-swaponremoveshortarray)

<a id="type-array"></a>

### Array

[Source](../../src/main/java/valthorne/collections/array/Array.java#L17)

Resizable indexed storage for object references with no separate logical element count.
Every allocated slot is readable, including untouched null entries. Set may
replace the backing array; callers holding getElements results must reacquire
that array after growth. Mutations are unsynchronized.

Growth allocates an Object array of twice the requested index, even after
construction with a concrete component type. A zero-capacity array cannot
accept index zero under that rule; use a positive initial size. Typed array
casts from getElements are not generally safe after growth.

- **`<T>`** — stored reference type; null is permitted

<details>
<summary>Array operation reference (6 declarations)</summary>

#### Constructor

```java
public Array(int size)
```

Allocates Object-backed storage of the requested length, initially filled with
null. Prefer positive size to permit a first write at index zero.

- **`size`** — initial allocated length

**Throws `NegativeArraySizeException`:** if size is negative

#### Constructor

```java
public Array(Class<T> type, int size)
```

Allocates storage with the supplied runtime component type. A subsequent
growth replaces it with Object-backed storage, so this constructor does not
guarantee a persistent concrete array type.

- **`type`** — reference component class used for initial allocation
- **`size`** — initial allocated length

**Throws `NullPointerException`:** if type is null

**Throws `NegativeArraySizeException`:** if size is negative

**Throws `ClassCastException`:** if a primitive component class is supplied

#### set

```java
public void set(int index, T value)
```

Stores a value, replacing backing storage when index reaches or exceeds length.
Growth uses an Object array of length index times two, losing any concrete
component type selected at construction. Index zero cannot grow an empty
array. Previously returned backing arrays no longer track the replacement.

- **`index`** — nonnegative destination slot whose growth size fits an int
- **`value`** — value to store

**Throws `ArrayIndexOutOfBoundsException`:** if index is negative or zero cannot grow empty storage

#### get

```java
public T get(int index)
```

Reads an allocated slot without resizing or changing membership.
Untouched slots return null; length describes all readable indices.

- **`index`** — slot from zero through length minus one

**Returns:** stored reference, without copying

**Throws `ArrayIndexOutOfBoundsException`:** if index is outside allocated storage

#### length

```java
public int length()
```

Returns backing-array length, including untouched and default-valued slots.
This is capacity, not a count of explicitly assigned values.

**Returns:** current allocated number of slots

#### getElements

```java
public T[] getElements()
```

Exposes the live backing array without copying it. Direct writes immediately
affect this object until a subsequent set replaces storage during growth.
The runtime component type is Object unless typed construction has occurred
and no growth has replaced that array. Assigning to a concrete T array can
therefore cause a caller-side ClassCastException.

**Returns:** current mutable backing array

</details>

<a id="type-bytearray"></a>

### ByteArray

[Source](../../src/main/java/valthorne/collections/array/ByteArray.java#L15)

Resizable indexed storage for byte values with no separate logical element count.
Every allocated slot is readable, including untouched zero entries. Set may
replace the backing array; callers holding getElements results must reacquire
that array after growth. Mutations are unsynchronized.

Growth allocates exactly index plus one slots, preserving earlier values.
There is no shrink operation or automatic growth on reads. Large sparse
indices still allocate every intermediate slot.

<details>
<summary>ByteArray operation reference (5 declarations)</summary>

#### Constructor

```java
public ByteArray(int size)
```

Allocates primitive storage of the requested length, initially filled with
zero. A later set beyond the last slot grows storage as needed.

- **`size`** — initial allocated length

**Throws `NegativeArraySizeException`:** if size is negative

#### set

```java
public void set(int index, byte value)
```

Stores a value, replacing backing storage when index reaches or exceeds length.
Growth allocates exactly index plus one slots, copying prior values and
leaving intermediate new slots zero. Previously returned backing arrays
no longer track the replacement.

- **`index`** — nonnegative destination slot whose growth size fits an int
- **`value`** — value to store

**Throws `ArrayIndexOutOfBoundsException`:** if index is negative

#### get

```java
public byte get(int index)
```

Reads an allocated slot without resizing or changing membership.
Untouched slots return zero; length describes all readable indices.

- **`index`** — slot from zero through length minus one

**Returns:** stored primitive value

**Throws `ArrayIndexOutOfBoundsException`:** if index is outside allocated storage

#### length

```java
public int length()
```

Returns backing-array length, including untouched and default-valued slots.
This is capacity, not a count of explicitly assigned values.

**Returns:** current allocated number of slots

#### getElements

```java
public byte[] getElements()
```

Exposes the live backing array without copying it. Direct writes immediately
affect this object until a subsequent set replaces storage during growth.
Retained arrays remain valid Java arrays after growth but no longer represent
this object's current storage.

**Returns:** current mutable backing array

</details>

<a id="type-chararray"></a>

### CharArray

[Source](../../src/main/java/valthorne/collections/array/CharArray.java#L15)

Resizable indexed storage for char values with no separate logical element count.
Every allocated slot is readable, including untouched zero entries. Set may
replace the backing array; callers holding getElements results must reacquire
that array after growth. Mutations are unsynchronized.

Growth allocates exactly index plus one slots, preserving earlier values.
There is no shrink operation or automatic growth on reads. Large sparse
indices still allocate every intermediate slot.

<details>
<summary>CharArray operation reference (5 declarations)</summary>

#### Constructor

```java
public CharArray(int size)
```

Allocates primitive storage of the requested length, initially filled with
zero. A later set beyond the last slot grows storage as needed.

- **`size`** — initial allocated length

**Throws `NegativeArraySizeException`:** if size is negative

#### set

```java
public void set(int index, char value)
```

Stores a value, replacing backing storage when index reaches or exceeds length.
Growth allocates exactly index plus one slots, copying prior values and
leaving intermediate new slots zero. Previously returned backing arrays
no longer track the replacement.

- **`index`** — nonnegative destination slot whose growth size fits an int
- **`value`** — value to store

**Throws `ArrayIndexOutOfBoundsException`:** if index is negative

#### get

```java
public char get(int index)
```

Reads an allocated slot without resizing or changing membership.
Untouched slots return zero; length describes all readable indices.

- **`index`** — slot from zero through length minus one

**Returns:** stored primitive value

**Throws `ArrayIndexOutOfBoundsException`:** if index is outside allocated storage

#### length

```java
public int length()
```

Returns backing-array length, including untouched and default-valued slots.
This is capacity, not a count of explicitly assigned values.

**Returns:** current allocated number of slots

#### getElements

```java
public char[] getElements()
```

Exposes the live backing array without copying it. Direct writes immediately
affect this object until a subsequent set replaces storage during growth.
Retained arrays remain valid Java arrays after growth but no longer represent
this object's current storage.

**Returns:** current mutable backing array

</details>

<a id="type-doublearray"></a>

### DoubleArray

[Source](../../src/main/java/valthorne/collections/array/DoubleArray.java#L15)

Resizable indexed storage for double values with no separate logical element count.
Every allocated slot is readable, including untouched zero entries. Set may
replace the backing array; callers holding getElements results must reacquire
that array after growth. Mutations are unsynchronized.

Growth allocates exactly index plus one slots, preserving earlier values.
There is no shrink operation or automatic growth on reads. Large sparse
indices still allocate every intermediate slot.

<details>
<summary>DoubleArray operation reference (5 declarations)</summary>

#### Constructor

```java
public DoubleArray(int size)
```

Allocates primitive storage of the requested length, initially filled with
zero. A later set beyond the last slot grows storage as needed.

- **`size`** — initial allocated length

**Throws `NegativeArraySizeException`:** if size is negative

#### set

```java
public void set(int index, double value)
```

Stores a value, replacing backing storage when index reaches or exceeds length.
Growth allocates exactly index plus one slots, copying prior values and
leaving intermediate new slots zero. Previously returned backing arrays
no longer track the replacement.

- **`index`** — nonnegative destination slot whose growth size fits an int
- **`value`** — value to store

**Throws `ArrayIndexOutOfBoundsException`:** if index is negative

#### get

```java
public double get(int index)
```

Reads an allocated slot without resizing or changing membership.
Untouched slots return zero; length describes all readable indices.

- **`index`** — slot from zero through length minus one

**Returns:** stored primitive value

**Throws `ArrayIndexOutOfBoundsException`:** if index is outside allocated storage

#### length

```java
public int length()
```

Returns backing-array length, including untouched and default-valued slots.
This is capacity, not a count of explicitly assigned values.

**Returns:** current allocated number of slots

#### getElements

```java
public double[] getElements()
```

Exposes the live backing array without copying it. Direct writes immediately
affect this object until a subsequent set replaces storage during growth.
Retained arrays remain valid Java arrays after growth but no longer represent
this object's current storage.

**Returns:** current mutable backing array

</details>

<a id="type-floatarray"></a>

### FloatArray

[Source](../../src/main/java/valthorne/collections/array/FloatArray.java#L15)

Resizable indexed storage for float values with no separate logical element count.
Every allocated slot is readable, including untouched zero entries. Set may
replace the backing array; callers holding getElements results must reacquire
that array after growth. Mutations are unsynchronized.

Growth allocates exactly index plus one slots, preserving earlier values.
There is no shrink operation or automatic growth on reads. Large sparse
indices still allocate every intermediate slot.

<details>
<summary>FloatArray operation reference (5 declarations)</summary>

#### Constructor

```java
public FloatArray(int size)
```

Allocates primitive storage of the requested length, initially filled with
zero. A later set beyond the last slot grows storage as needed.

- **`size`** — initial allocated length

**Throws `NegativeArraySizeException`:** if size is negative

#### set

```java
public void set(int index, float value)
```

Stores a value, replacing backing storage when index reaches or exceeds length.
Growth allocates exactly index plus one slots, copying prior values and
leaving intermediate new slots zero. Previously returned backing arrays
no longer track the replacement.

- **`index`** — nonnegative destination slot whose growth size fits an int
- **`value`** — value to store

**Throws `ArrayIndexOutOfBoundsException`:** if index is negative

#### get

```java
public float get(int index)
```

Reads an allocated slot without resizing or changing membership.
Untouched slots return zero; length describes all readable indices.

- **`index`** — slot from zero through length minus one

**Returns:** stored primitive value

**Throws `ArrayIndexOutOfBoundsException`:** if index is outside allocated storage

#### length

```java
public int length()
```

Returns backing-array length, including untouched and default-valued slots.
This is capacity, not a count of explicitly assigned values.

**Returns:** current allocated number of slots

#### getElements

```java
public float[] getElements()
```

Exposes the live backing array without copying it. Direct writes immediately
affect this object until a subsequent set replaces storage during growth.
Retained arrays remain valid Java arrays after growth but no longer represent
this object's current storage.

**Returns:** current mutable backing array

</details>

<a id="type-intarray"></a>

### IntArray

[Source](../../src/main/java/valthorne/collections/array/IntArray.java#L15)

Resizable indexed storage for int values with no separate logical element count.
Every allocated slot is readable, including untouched zero entries. Set may
replace the backing array; callers holding getElements results must reacquire
that array after growth. Mutations are unsynchronized.

Growth allocates exactly index plus one slots, preserving earlier values.
There is no shrink operation or automatic growth on reads. Large sparse
indices still allocate every intermediate slot.

<details>
<summary>IntArray operation reference (5 declarations)</summary>

#### Constructor

```java
public IntArray(int size)
```

Allocates primitive storage of the requested length, initially filled with
zero. A later set beyond the last slot grows storage as needed.

- **`size`** — initial allocated length

**Throws `NegativeArraySizeException`:** if size is negative

#### set

```java
public void set(int index, int value)
```

Stores a value, replacing backing storage when index reaches or exceeds length.
Growth allocates exactly index plus one slots, copying prior values and
leaving intermediate new slots zero. Previously returned backing arrays
no longer track the replacement.

- **`index`** — nonnegative destination slot whose growth size fits an int
- **`value`** — value to store

**Throws `ArrayIndexOutOfBoundsException`:** if index is negative

#### get

```java
public int get(int index)
```

Reads an allocated slot without resizing or changing membership.
Untouched slots return zero; length describes all readable indices.

- **`index`** — slot from zero through length minus one

**Returns:** stored primitive value

**Throws `ArrayIndexOutOfBoundsException`:** if index is outside allocated storage

#### length

```java
public int length()
```

Returns backing-array length, including untouched and default-valued slots.
This is capacity, not a count of explicitly assigned values.

**Returns:** current allocated number of slots

#### getElements

```java
public int[] getElements()
```

Exposes the live backing array without copying it. Direct writes immediately
affect this object until a subsequent set replaces storage during growth.
Retained arrays remain valid Java arrays after growth but no longer represent
this object's current storage.

**Returns:** current mutable backing array

</details>

<a id="type-longarray"></a>

### LongArray

[Source](../../src/main/java/valthorne/collections/array/LongArray.java#L15)

Resizable indexed storage for long values with no separate logical element count.
Every allocated slot is readable, including untouched zero entries. Set may
replace the backing array; callers holding getElements results must reacquire
that array after growth. Mutations are unsynchronized.

Growth allocates exactly index plus one slots, preserving earlier values.
There is no shrink operation or automatic growth on reads. Large sparse
indices still allocate every intermediate slot.

<details>
<summary>LongArray operation reference (5 declarations)</summary>

#### Constructor

```java
public LongArray(int size)
```

Allocates primitive storage of the requested length, initially filled with
zero. A later set beyond the last slot grows storage as needed.

- **`size`** — initial allocated length

**Throws `NegativeArraySizeException`:** if size is negative

#### set

```java
public void set(int index, long value)
```

Stores a value, replacing backing storage when index reaches or exceeds length.
Growth allocates exactly index plus one slots, copying prior values and
leaving intermediate new slots zero. Previously returned backing arrays
no longer track the replacement.

- **`index`** — nonnegative destination slot whose growth size fits an int
- **`value`** — value to store

**Throws `ArrayIndexOutOfBoundsException`:** if index is negative

#### get

```java
public long get(int index)
```

Reads an allocated slot without resizing or changing membership.
Untouched slots return zero; length describes all readable indices.

- **`index`** — slot from zero through length minus one

**Returns:** stored primitive value

**Throws `ArrayIndexOutOfBoundsException`:** if index is outside allocated storage

#### length

```java
public int length()
```

Returns backing-array length, including untouched and default-valued slots.
This is capacity, not a count of explicitly assigned values.

**Returns:** current allocated number of slots

#### getElements

```java
public long[] getElements()
```

Exposes the live backing array without copying it. Direct writes immediately
affect this object until a subsequent set replaces storage during growth.
Retained arrays remain valid Java arrays after growth but no longer represent
this object's current storage.

**Returns:** current mutable backing array

</details>

<a id="type-shortarray"></a>

### ShortArray

[Source](../../src/main/java/valthorne/collections/array/ShortArray.java#L15)

Resizable indexed storage for short values with no separate logical element count.
Every allocated slot is readable, including untouched zero entries. Set may
replace the backing array; callers holding getElements results must reacquire
that array after growth. Mutations are unsynchronized.

Growth allocates exactly index plus one slots, preserving earlier values.
There is no shrink operation or automatic growth on reads. Large sparse
indices still allocate every intermediate slot.

<details>
<summary>ShortArray operation reference (5 declarations)</summary>

#### Constructor

```java
public ShortArray(int size)
```

Allocates primitive storage of the requested length, initially filled with
zero. A later set beyond the last slot grows storage as needed.

- **`size`** — initial allocated length

**Throws `NegativeArraySizeException`:** if size is negative

#### set

```java
public void set(int index, short value)
```

Stores a value, replacing backing storage when index reaches or exceeds length.
Growth allocates exactly index plus one slots, copying prior values and
leaving intermediate new slots zero. Previously returned backing arrays
no longer track the replacement.

- **`index`** — nonnegative destination slot whose growth size fits an int
- **`value`** — value to store

**Throws `ArrayIndexOutOfBoundsException`:** if index is negative

#### get

```java
public short get(int index)
```

Reads an allocated slot without resizing or changing membership.
Untouched slots return zero; length describes all readable indices.

- **`index`** — slot from zero through length minus one

**Returns:** stored primitive value

**Throws `ArrayIndexOutOfBoundsException`:** if index is outside allocated storage

#### length

```java
public int length()
```

Returns backing-array length, including untouched and default-valued slots.
This is capacity, not a count of explicitly assigned values.

**Returns:** current allocated number of slots

#### getElements

```java
public short[] getElements()
```

Exposes the live backing array without copying it. Direct writes immediately
affect this object until a subsequent set replaces storage during growth.
Retained arrays remain valid Java arrays after growth but no longer represent
this object's current storage.

**Returns:** current mutable backing array

</details>

<a id="type-swaponremovearray"></a>

### SwapOnRemoveArray

[Source](../../src/main/java/valthorne/collections/array/SwapOnRemoveArray.java#L15)

The SwapOnRemoveArray class is a resizable array-based collection that allows elements to be efficiently
added and removed. When an element is removed, it is swapped with the last element in the array to maintain
array continuity and improve removal performance.

- **`<E>`** — the type of elements stored in the array

<details>
<summary>SwapOnRemoveArray operation reference (14 declarations)</summary>

#### Constructor

```java
public SwapOnRemoveArray()
```

Constructs a new SwapOnRemoveArray with an initial capacity of 10.

#### add

```java
public void add(E element)
```

Adds the specified element to the end of this array. If the array is full, it is resized to twice its
current capacity to accommodate more elements.

- **`element`** — the element to be added to the array

#### remove

```java
public E remove(int index)
```

Removes the element at the specified index from the array. The element to be removed
is swapped with the last element in the array, and the size of the array is decremented.
The removed element is returned.

- **`index`** — the index of the element to be removed

**Returns:** the element that was removed from the array

#### removeValue

```java
public void removeValue(E value)
```

Removes the specific value from the array. If the value is not found, nothing happens.

- **`value`** — the value to remove

#### get

```java
public E get(int index)
```

Returns the element at the specified index in the array.

- **`index`** — the index of the element to retrieve

**Returns:** the element at the specified index

#### clear

```java
public void clear()
```

Removes all elements from the array, leaving it empty.

#### indexOf

```java
public int indexOf(E element)
```

Returns the index of the first occurrence of the specified element in the array.

- **`element`** — the element to search for

**Returns:** the index of the element, or -1 if the element is not found

#### trimToSize

```java
public void trimToSize()
```

Trims the capacity of the array to the current size, reducing memory usage.

#### isEmpty

```java
public boolean isEmpty()
```

Checks if the array is empty.

**Returns:** true if the array is empty, false otherwise

#### isFull

```java
public boolean isFull()
```

Checks if the array is full.

**Returns:** true if the array is full, false otherwise

#### size

```java
public int size()
```

Returns the current number of elements in the array.

**Returns:** the size of the array

#### contains

```java
public boolean contains(E element)
```

Checks if the array contains the specified element.

- **`element`** — the element to be checked for existence in the array

**Returns:** true if the element is found, false otherwise

#### getData

```java
public E[] getData()
```

Returns the array of elements.

**Returns:** an array containing the elements in this collection

#### toString

```java
@Override
    public String toString()
```

Returns a string representation of the array.

**Returns:** a string representation of the array

</details>

<a id="type-swaponremovebytearray"></a>

### SwapOnRemoveByteArray

[Source](../../src/main/java/valthorne/collections/array/SwapOnRemoveByteArray.java#L14)

The SwapOnRemoveByteArray class is a resizable array-based collection that allows elements to be efficiently
added and removed. When an element is removed, it is swapped with the last element in the array to maintain
array continuity and improve removal performance.

<details>
<summary>SwapOnRemoveByteArray operation reference (14 declarations)</summary>

#### Constructor

```java
public SwapOnRemoveByteArray()
```

Constructs a new SwapOnRemoveByteArray with an initial capacity of 10.

#### add

```java
public void add(byte element)
```

Adds the specified element to the end of this array. If the array is full, it is resized to twice its
current capacity to accommodate more elements.

- **`element`** — the element to be added to the array

#### remove

```java
public byte remove(int index)
```

Removes the element at the specified index from the array. The element to be removed
is swapped with the last element in the array, and the size of the array is decremented.
The removed element is returned.

- **`index`** — the index of the element to be removed

**Returns:** the element that was removed from the array

#### removeValue

```java
public void removeValue(byte value)
```

Removes the specific value from the array. If the value is not found, nothing happens.

- **`value`** — the value to remove

#### size

```java
public int size()
```

Returns the current number of elements in the array.

**Returns:** the size of the array

#### contains

```java
public boolean contains(byte element)
```

Checks if the array contains the specified element.

- **`element`** — the element to be checked for existence in the array

**Returns:** true if the element is found, false otherwise

#### getData

```java
public byte[] getData()
```

Returns the array of elements.

**Returns:** an array containing the elements in this collection

#### get

```java
public byte get(int index)
```

Returns the element at the specified index in the array.

- **`index`** — the index of the element to retrieve

**Returns:** the element at the specified index

#### clear

```java
public void clear()
```

Removes all elements from the array, leaving it empty.

#### indexOf

```java
public int indexOf(byte element)
```

Returns the index of the first occurrence of the specified element in the array.

- **`element`** — the element to search for

**Returns:** the index of the element, or -1 if the element is not found

#### trimToSize

```java
public void trimToSize()
```

Trims the capacity of the array to the current size, reducing memory usage.

#### isEmpty

```java
public boolean isEmpty()
```

Checks if the array is empty.

**Returns:** true if the array is empty, false otherwise

#### isFull

```java
public boolean isFull()
```

Checks if the array is full.

**Returns:** true if the array is full, false otherwise

#### toString

```java
@Override
    public String toString()
```

Returns a string representation of the array.

**Returns:** a string representation of the array

</details>

<a id="type-swaponremovechararray"></a>

### SwapOnRemoveCharArray

[Source](../../src/main/java/valthorne/collections/array/SwapOnRemoveCharArray.java#L14)

The SwapOnRemoveCharArray class is a resizable array-based collection that allows elements to be efficiently
added and removed. When an element is removed, it is swapped with the last element in the array to maintain
array continuity and improve removal performance.

<details>
<summary>SwapOnRemoveCharArray operation reference (14 declarations)</summary>

#### Constructor

```java
public SwapOnRemoveCharArray()
```

Constructs a new SwapOnRemoveCharArray with an initial capacity of 10.

#### add

```java
public void add(char element)
```

Adds the specified element to the end of this array. If the array is full, it is resized to twice its
current capacity to accommodate more elements.

- **`element`** — the element to be added to the array

#### remove

```java
public char remove(int index)
```

Removes the element at the specified index from the array. The element to be removed
is swapped with the last element in the array, and the size of the array is decremented.
The removed element is returned.

- **`index`** — the index of the element to be removed

**Returns:** the element that was removed from the array

#### removeValue

```java
public void removeValue(char value)
```

Removes the specific value from the array. If the value is not found, nothing happens.

- **`value`** — the value to remove

#### size

```java
public int size()
```

Returns the current number of elements in the array.

**Returns:** the size of the array

#### contains

```java
public boolean contains(char element)
```

Checks if the array contains the specified element.

- **`element`** — the element to be checked for existence in the array

**Returns:** true if the element is found, false otherwise

#### getData

```java
public char[] getData()
```

Returns the array of elements.

**Returns:** an array containing the elements in this collection

#### get

```java
public char get(int index)
```

Returns the element at the specified index in the array.

- **`index`** — the index of the element to retrieve

**Returns:** the element at the specified index

#### clear

```java
public void clear()
```

Removes all elements from the array, leaving it empty.

#### indexOf

```java
public int indexOf(char element)
```

Returns the index of the first occurrence of the specified element in the array.

- **`element`** — the element to search for

**Returns:** the index of the element, or -1 if the element is not found

#### trimToSize

```java
public void trimToSize()
```

Trims the capacity of the array to the current size, reducing memory usage.

#### isEmpty

```java
public boolean isEmpty()
```

Checks if the array is empty.

**Returns:** true if the array is empty, false otherwise

#### isFull

```java
public boolean isFull()
```

Checks if the array is full.

**Returns:** true if the array is full, false otherwise

#### toString

```java
@Override
    public String toString()
```

Returns a string representation of the array.

**Returns:** a string representation of the array

</details>

<a id="type-swaponremovedoublearray"></a>

### SwapOnRemoveDoubleArray

[Source](../../src/main/java/valthorne/collections/array/SwapOnRemoveDoubleArray.java#L14)

The SwapOnRemoveDoubleArray class is a resizable array-based collection that allows elements to be efficiently
added and removed. When an element is removed, it is swapped with the last element in the array to maintain
array continuity and improve removal performance.

<details>
<summary>SwapOnRemoveDoubleArray operation reference (14 declarations)</summary>

#### Constructor

```java
public SwapOnRemoveDoubleArray()
```

Constructs a new SwapOnRemoveDoubleArray with an initial capacity of 10.

#### add

```java
public void add(double element)
```

Adds the specified element to the end of this array. If the array is full, it is resized to twice its
current capacity to accommodate more elements.

- **`element`** — the element to be added to the array

#### remove

```java
public double remove(int index)
```

Removes the element at the specified index from the array. The element to be removed
is swapped with the last element in the array, and the size of the array is decremented.
The removed element is returned.

- **`index`** — the index of the element to be removed

**Returns:** the element that was removed from the array

#### removeValue

```java
public void removeValue(double value)
```

Removes the specific value from the array. If the value is not found, nothing happens.

- **`value`** — the value to remove

#### size

```java
public int size()
```

Returns the current number of elements in the array.

**Returns:** the size of the array

#### contains

```java
public boolean contains(double element)
```

Checks if the array contains the specified element.

- **`element`** — the element to be checked for existence in the array

**Returns:** true if the element is found, false otherwise

#### getData

```java
public double[] getData()
```

Returns the array of elements.

**Returns:** an array containing the elements in this collection

#### get

```java
public double get(int index)
```

Returns the element at the specified index in the array.

- **`index`** — the index of the element to retrieve

**Returns:** the element at the specified index

#### clear

```java
public void clear()
```

Removes all elements from the array, leaving it empty.

#### indexOf

```java
public int indexOf(double element)
```

Returns the index of the first occurrence of the specified element in the array.

- **`element`** — the element to search for

**Returns:** the index of the element, or -1 if the element is not found

#### trimToSize

```java
public void trimToSize()
```

Trims the capacity of the array to the current size, reducing memory usage.

#### isEmpty

```java
public boolean isEmpty()
```

Checks if the array is empty.

**Returns:** true if the array is empty, false otherwise

#### isFull

```java
public boolean isFull()
```

Checks if the array is full.

**Returns:** true if the array is full, false otherwise

#### toString

```java
@Override
    public String toString()
```

Returns a string representation of the array.

**Returns:** a string representation of the array

</details>

<a id="type-swaponremovefloatarray"></a>

### SwapOnRemoveFloatArray

[Source](../../src/main/java/valthorne/collections/array/SwapOnRemoveFloatArray.java#L14)

The SwapOnRemoveFloatArray class is a resizable array-based collection that allows elements to be efficiently
added and removed. When an element is removed, it is swapped with the last element in the array to maintain
array continuity and improve removal performance.

<details>
<summary>SwapOnRemoveFloatArray operation reference (14 declarations)</summary>

#### Constructor

```java
public SwapOnRemoveFloatArray()
```

Constructs a new SwapOnRemoveFloatArray with an initial capacity of 10.

#### add

```java
public void add(float element)
```

Adds the specified element to the end of this array. If the array is full, it is resized to twice its
current capacity to accommodate more elements.

- **`element`** — the element to be added to the array

#### remove

```java
public float remove(int index)
```

Removes the element at the specified index from the array. The element to be removed
is swapped with the last element in the array, and the size of the array is decremented.
The removed element is returned.

- **`index`** — the index of the element to be removed

**Returns:** the element that was removed from the array

#### removeValue

```java
public void removeValue(float value)
```

Removes the specific value from the array. If the value is not found, nothing happens.

- **`value`** — the value to remove

#### size

```java
public int size()
```

Returns the current number of elements in the array.

**Returns:** the size of the array

#### contains

```java
public boolean contains(float element)
```

Checks if the array contains the specified element.

- **`element`** — the element to be checked for existence in the array

**Returns:** true if the element is found, false otherwise

#### getData

```java
public float[] getData()
```

Returns the array of elements.

**Returns:** an array containing the elements in this collection

#### get

```java
public float get(int index)
```

Returns the element at the specified index in the array.

- **`index`** — the index of the element to retrieve

**Returns:** the element at the specified index

#### clear

```java
public void clear()
```

Removes all elements from the array, leaving it empty.

#### indexOf

```java
public int indexOf(float element)
```

Returns the index of the first occurrence of the specified element in the array.

- **`element`** — the element to search for

**Returns:** the index of the element, or -1 if the element is not found

#### trimToSize

```java
public void trimToSize()
```

Trims the capacity of the array to the current size, reducing memory usage.

#### isEmpty

```java
public boolean isEmpty()
```

Checks if the array is empty.

**Returns:** true if the array is empty, false otherwise

#### isFull

```java
public boolean isFull()
```

Checks if the array is full.

**Returns:** true if the array is full, false otherwise

#### toString

```java
@Override
    public String toString()
```

Returns a string representation of the array.

**Returns:** a string representation of the array

</details>

<a id="type-swaponremoveintarray"></a>

### SwapOnRemoveIntArray

[Source](../../src/main/java/valthorne/collections/array/SwapOnRemoveIntArray.java#L14)

The SwapOnRemoveIntArray class is a resizable array-based collection that allows elements to be efficiently
added and removed. When an element is removed, it is swapped with the last element in the array to maintain
array continuity and improve removal performance.

<details>
<summary>SwapOnRemoveIntArray operation reference (14 declarations)</summary>

#### Constructor

```java
public SwapOnRemoveIntArray()
```

Constructs a new SwapOnRemoveIntArray with an initial capacity of 10.

#### add

```java
public void add(int element)
```

Adds the specified element to the end of this array. If the array is full, it is resized to twice its
current capacity to accommodate more elements.

- **`element`** — the element to be added to the array

#### remove

```java
public int remove(int index)
```

Removes the element at the specified index from the array. The element to be removed
is swapped with the last element in the array, and the size of the array is decremented.
The removed element is returned.

- **`index`** — the index of the element to be removed

**Returns:** the element that was removed from the array

#### removeValue

```java
public void removeValue(int value)
```

Removes the specific value from the array. If the value is not found, nothing happens.

- **`value`** — the value to remove

#### size

```java
public int size()
```

Returns the current number of elements in the array.

**Returns:** the size of the array

#### contains

```java
public boolean contains(int element)
```

Checks if the array contains the specified element.

- **`element`** — the element to be checked for existence in the array

**Returns:** true if the element is found, false otherwise

#### getData

```java
public int[] getData()
```

Returns the array of elements.

**Returns:** an array containing the elements in this collection

#### get

```java
public int get(int index)
```

Returns the element at the specified index in the array.

- **`index`** — the index of the element to retrieve

**Returns:** the element at the specified index

#### clear

```java
public void clear()
```

Removes all elements from the array, leaving it empty.

#### indexOf

```java
public int indexOf(int element)
```

Returns the index of the first occurrence of the specified element in the array.

- **`element`** — the element to search for

**Returns:** the index of the element, or -1 if the element is not found

#### trimToSize

```java
public void trimToSize()
```

Trims the capacity of the array to the current size, reducing memory usage.

#### isEmpty

```java
public boolean isEmpty()
```

Checks if the array is empty.

**Returns:** true if the array is empty, false otherwise

#### isFull

```java
public boolean isFull()
```

Checks if the array is full.

**Returns:** true if the array is full, false otherwise

#### toString

```java
@Override
    public String toString()
```

Returns a string representation of the array.

**Returns:** a string representation of the array

</details>

<a id="type-swaponremovelongarray"></a>

### SwapOnRemoveLongArray

[Source](../../src/main/java/valthorne/collections/array/SwapOnRemoveLongArray.java#L14)

The SwapOnRemoveLongArray class is a resizable array-based collection that allows elements to be efficiently
added and removed. When an element is removed, it is swapped with the last element in the array to maintain
array continuity and improve removal performance.

<details>
<summary>SwapOnRemoveLongArray operation reference (14 declarations)</summary>

#### Constructor

```java
public SwapOnRemoveLongArray()
```

Constructs a new SwapOnRemoveLongArray with an initial capacity of 10.

#### add

```java
public void add(long element)
```

Adds the specified element to the end of this array. If the array is full, it is resized to twice its
current capacity to accommodate more elements.

- **`element`** — the element to be added to the array

#### remove

```java
public long remove(int index)
```

Removes the element at the specified index from the array. The element to be removed
is swapped with the last element in the array, and the size of the array is decremented.
The removed element is returned.

- **`index`** — the index of the element to be removed

**Returns:** the element that was removed from the array

#### removeValue

```java
public void removeValue(long value)
```

Removes the specific value from the array. If the value is not found, nothing happens.

- **`value`** — the value to remove

#### size

```java
public int size()
```

Returns the current number of elements in the array.

**Returns:** the size of the array

#### contains

```java
public boolean contains(long element)
```

Checks if the array contains the specified element.

- **`element`** — the element to be checked for existence in the array

**Returns:** true if the element is found, false otherwise

#### getData

```java
public long[] getData()
```

Returns the array of elements.

**Returns:** an array containing the elements in this collection

#### toString

```java
@Override
    public String toString()
```

Returns a string representation of the array.

**Returns:** a string representation of the array

#### get

```java
public long get(int index)
```

Returns the element at the specified index in the array.

- **`index`** — the index of the element to retrieve

**Returns:** the element at the specified index

#### clear

```java
public void clear()
```

Removes all elements from the array, leaving it empty.

#### indexOf

```java
public int indexOf(long element)
```

Returns the index of the first occurrence of the specified element in the array.

- **`element`** — the element to search for

**Returns:** the index of the element, or -1 if the element is not found

#### trimToSize

```java
public void trimToSize()
```

Trims the capacity of the array to the current size, reducing memory usage.

#### isEmpty

```java
public boolean isEmpty()
```

Checks if the array is empty.

**Returns:** true if the array is empty, false otherwise

#### isFull

```java
public boolean isFull()
```

Checks if the array is full.

**Returns:** true if the array is full, false otherwise

</details>

<a id="type-swaponremoveshortarray"></a>

### SwapOnRemoveShortArray

[Source](../../src/main/java/valthorne/collections/array/SwapOnRemoveShortArray.java#L14)

The SwapOnRemoveShortArray class is a resizable array-based collection that allows elements to be efficiently
added and removed. When an element is removed, it is swapped with the last element in the array to maintain
array continuity and improve removal performance.

<details>
<summary>SwapOnRemoveShortArray operation reference (14 declarations)</summary>

#### Constructor

```java
public SwapOnRemoveShortArray()
```

Constructs a new SwapOnRemoveShortArray with an initial capacity of 10.

#### add

```java
public void add(short element)
```

Adds the specified element to the end of this array. If the array is full, it is resized to twice its
current capacity to accommodate more elements.

- **`element`** — the element to be added to the array

#### remove

```java
public short remove(int index)
```

Removes the element at the specified index from the array. The element to be removed
is swapped with the last element in the array, and the size of the array is decremented.
The removed element is returned.

- **`index`** — the index of the element to be removed

**Returns:** the element that was removed from the array

#### removeValue

```java
public void removeValue(short value)
```

Removes the specific value from the array. If the value is not found, nothing happens.

- **`value`** — the value to remove

#### size

```java
public int size()
```

Returns the current number of elements in the array.

**Returns:** the size of the array

#### contains

```java
public boolean contains(short element)
```

Checks if the array contains the specified element.

- **`element`** — the element to be checked for existence in the array

**Returns:** true if the element is found, false otherwise

#### getData

```java
public short[] getData()
```

Returns the array of elements.

**Returns:** an array containing the elements in this collection

#### get

```java
public short get(int index)
```

Returns the element at the specified index in the array.

- **`index`** — the index of the element to retrieve

**Returns:** the element at the specified index

#### clear

```java
public void clear()
```

Removes all elements from the array, leaving it empty.

#### indexOf

```java
public int indexOf(short element)
```

Returns the index of the first occurrence of the specified element in the array.

- **`element`** — the element to search for

**Returns:** the index of the element, or -1 if the element is not found

#### trimToSize

```java
public void trimToSize()
```

Trims the capacity of the array to the current size, reducing memory usage.

#### isEmpty

```java
public boolean isEmpty()
```

Checks if the array is empty.

**Returns:** true if the array is empty, false otherwise

#### isFull

```java
public boolean isFull()
```

Checks if the array is full.

**Returns:** true if the array is full, false otherwise

#### toString

```java
@Override
    public String toString()
```

Returns a string representation of the array.

**Returns:** a string representation of the array

</details>

## Related guides

- [Primitive and generic stacks](stacks.md)
- [ID reuse queues, string maps, and trees](data-structures.md)
- [Object pooling](pooling.md)
