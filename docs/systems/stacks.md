# Primitive and generic stacks

Author: Albert Beaupre

[System manual](README.md)

## Purpose

FastStack and primitive counterparts implement last-in, first-out storage. They suit temporary traversal state, reusable worklists, and nested operations where the newest entry must be consumed first. Choose them for stack semantics rather than queue semantics.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Push/pop | Append at the top and consume the most recent entry. |
| Peek | Inspect the top without removing it. |
| Capacity | Backing arrays grow according to the implementation's policy. |
| Primitive variants | Avoid boxed values for numeric or character stacks. |

## Getting started

1. Construct the stack with a usable initial capacity.
2. Push work in the order that produces the desired reverse traversal.
3. Check emptiness or the documented sentinel before consuming.
4. Clear or reuse the stack after the operation rather than creating one per element.

## Usage example

Place these statements in your initialization or application method; imports belong at the top of the Java file.

```java
import valthorne.collections.stack.IntFastStack;

IntFastStack work = new IntFastStack(8);
work.push(10);
work.push(20);
while (!work.isEmpty()) {
    int next = work.pop(); // 20, then 10.
    System.out.println(next);
}
```

## Ownership and lifecycle

Stacks are mutable and not automatically synchronized. Stored objects remain owned by application code. Empty-result behavior is type-specific and can use a sentinel that is also a legitimate stored value.

## Important behavior

- Consult each pop/peek contract before relying on an exception for an empty stack.
- The zero-capacity growth edge case requires care; use a positive initial capacity.
- Do not interpret stack order as FIFO.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`ByteFastStack`](#type-bytefaststack)
- [`CharFastStack`](#type-charfaststack)
- [`DoubleFastStack`](#type-doublefaststack)
- [`FastStack`](#type-faststack)
- [`FloatFastStack`](#type-floatfaststack)
- [`IntFastStack`](#type-intfaststack)
- [`LongFastStack`](#type-longfaststack)
- [`ShortFastStack`](#type-shortfaststack)

<a id="type-bytefaststack"></a>

### ByteFastStack

[Source](../../src/main/java/valthorne/collections/stack/ByteFastStack.java#L21)

Array-backed last-in, first-out storage for byte values. Positive
capacity doubles when full; push and pop normally access only the top slot.
Empty pop returns -1, which is also a valid stored value, whereas empty
peek throws. Use isEmpty when distinguishing an empty pop matters.

Iterators walk top to bottom over live storage with an initial cursor and
no concurrent-modification checks. Do not mutate the stack while iterating.
The class is mutable and supplies no thread synchronization. Construct with
positive capacity: a zero-length array cannot grow by doubling.

<details>
<summary>ByteFastStack operation reference (10 declarations)</summary>

#### Constructor

```java
public ByteFastStack()
```

Creates an empty stack with ten storage slots. The backing array grows
when later pushes fill its current capacity.

#### Constructor

```java
public ByteFastStack(int size)
```

Allocates the requested initial storage without placing any elements in it.
Use positive capacity; zero is accepted here but the doubling growth rule
cannot make a zero-length array usable for push.

- **`size`** — initial number of storage slots

**Throws `NegativeArraySizeException`:** if size is negative

#### push

```java
public void push(byte data)
```

Appends a value at the top, doubling and copying the array when full.
Existing order is preserved. Storage itself does not box primitive values.

- **`data`** — value to push

**Throws `ArrayIndexOutOfBoundsException`:** if constructed with zero capacity

#### pop

```java
public byte pop()
```

Removes the most recently pushed element and clears its vacated slot.
Empty stacks return -1 without changing state; that result is not a
unique emptiness indicator because the same value can be pushed.

**Returns:** previous top value, or -1 when empty

#### peek

```java
public byte peek()
```

Reads the most recently pushed element without removing or copying it.
Unlike pop, an empty stack is reported by an exception.

**Returns:** current top value

**Throws `NoSuchElementException`:** if the stack is empty

#### isEmpty

```java
public boolean isEmpty()
```

Tests logical membership rather than backing-array capacity.

**Returns:** true when no elements are stored

#### size

```java
public int size()
```

Reads the number of occupied slots. Reserved backing capacity is excluded.

**Returns:** current element count

#### clear

```java
public void clear()
```

Fills the entire backing array with zero and resets the element count.
Allocated capacity is retained for reuse; existing iterators are invalid for
continued traversal after this mutation.

#### toString

```java
    public String toString()
```

Copies the occupied prefix and formats it in bottom-to-top storage order.
This order is the reverse of iterator traversal; unused capacity is omitted.

**Returns:** bracketed representation of the current elements

#### iterator

```java
    public Iterator<Byte> iterator()
```

Creates a top-to-bottom iterator whose initial cursor is the current top.
It reads live storage, does not detect later mutation, and does not support
removal. Keep the stack unchanged while using the iterator.

**Returns:** new iterator over current stack positions

</details>

<a id="type-bytefaststack-bytefaststackiterator"></a>

### ByteFastStack.ByteFastStackIterator — internal support type

[Source](../../src/main/java/valthorne/collections/stack/ByteFastStack.java#L153)

Live-storage iterator with an independent descending cursor initialized from
the enclosing stack's top slot. It is neither a snapshot nor fail-fast;
mutation of the enclosing stack during traversal is unsupported.

<details>
<summary>ByteFastStack.ByteFastStackIterator operation reference (3 declarations)</summary>

#### hasNext

```java
        public boolean hasNext()
```

Checks whether the saved cursor still addresses a nonnegative slot.
Does not compare against the enclosing stack's current size.

**Returns:** true when another cursor position remains

#### next

```java
        public Byte next()
```

Reads the current live backing-array slot and decrements the cursor.
The primitive value is boxed for the Iterator interface.

**Returns:** next value in top-to-bottom order

**Throws `NoSuchElementException`:** if the cursor is exhausted

#### remove

```java
        public void remove()
```

Rejects iterator removal without changing storage or cursor state.
Use the enclosing stack's operations outside iteration instead.

**Throws `UnsupportedOperationException`:** always

</details>

<a id="type-charfaststack"></a>

### CharFastStack

[Source](../../src/main/java/valthorne/collections/stack/CharFastStack.java#L21)

Array-backed last-in, first-out storage for char values. Positive
capacity doubles when full; push and pop normally access only the top slot.
Empty pop returns (char) -1, which is also a valid stored value, whereas empty
peek throws. Use isEmpty when distinguishing an empty pop matters.

Iterators walk top to bottom over live storage with an initial cursor and
no concurrent-modification checks. Do not mutate the stack while iterating.
The class is mutable and supplies no thread synchronization. Construct with
positive capacity: a zero-length array cannot grow by doubling.

<details>
<summary>CharFastStack operation reference (10 declarations)</summary>

#### Constructor

```java
public CharFastStack()
```

Creates an empty stack with ten storage slots. The backing array grows
when later pushes fill its current capacity.

#### Constructor

```java
public CharFastStack(int size)
```

Allocates the requested initial storage without placing any elements in it.
Use positive capacity; zero is accepted here but the doubling growth rule
cannot make a zero-length array usable for push.

- **`size`** — initial number of storage slots

**Throws `NegativeArraySizeException`:** if size is negative

#### push

```java
public void push(char data)
```

Appends a value at the top, doubling and copying the array when full.
Existing order is preserved. Storage itself does not box primitive values.

- **`data`** — value to push

**Throws `ArrayIndexOutOfBoundsException`:** if constructed with zero capacity

#### pop

```java
public char pop()
```

Removes the most recently pushed element and clears its vacated slot.
Empty stacks return (char) -1 without changing state; that result is not a
unique emptiness indicator because the same value can be pushed.

**Returns:** previous top value, or (char) -1 when empty

#### peek

```java
public char peek()
```

Reads the most recently pushed element without removing or copying it.
Unlike pop, an empty stack is reported by an exception.

**Returns:** current top value

**Throws `NoSuchElementException`:** if the stack is empty

#### isEmpty

```java
public boolean isEmpty()
```

Tests logical membership rather than backing-array capacity.

**Returns:** true when no elements are stored

#### size

```java
public int size()
```

Reads the number of occupied slots. Reserved backing capacity is excluded.

**Returns:** current element count

#### clear

```java
public void clear()
```

Fills the entire backing array with zero and resets the element count.
Allocated capacity is retained for reuse; existing iterators are invalid for
continued traversal after this mutation.

#### toString

```java
    public String toString()
```

Copies the occupied prefix and formats it in bottom-to-top storage order.
This order is the reverse of iterator traversal; unused capacity is omitted.

**Returns:** bracketed representation of the current elements

#### iterator

```java
    public Iterator<Character> iterator()
```

Creates a top-to-bottom iterator whose initial cursor is the current top.
It reads live storage, does not detect later mutation, and does not support
removal. Keep the stack unchanged while using the iterator.

**Returns:** new iterator over current stack positions

</details>

<a id="type-charfaststack-charfaststackiterator"></a>

### CharFastStack.CharFastStackIterator — internal support type

[Source](../../src/main/java/valthorne/collections/stack/CharFastStack.java#L150)

Live-storage iterator with an independent descending cursor initialized from
the enclosing stack's top slot. It is neither a snapshot nor fail-fast;
mutation of the enclosing stack during traversal is unsupported.

<details>
<summary>CharFastStack.CharFastStackIterator operation reference (3 declarations)</summary>

#### hasNext

```java
        public boolean hasNext()
```

Checks whether the saved cursor still addresses a nonnegative slot.
Does not compare against the enclosing stack's current size.

**Returns:** true when another cursor position remains

#### next

```java
        public Character next()
```

Reads the current live backing-array slot and decrements the cursor.
The primitive value is boxed for the Iterator interface.

**Returns:** next value in top-to-bottom order

**Throws `NoSuchElementException`:** if the cursor is exhausted

#### remove

```java
        public void remove()
```

Rejects iterator removal without changing storage or cursor state.
Use the enclosing stack's operations outside iteration instead.

**Throws `UnsupportedOperationException`:** always

</details>

<a id="type-doublefaststack"></a>

### DoubleFastStack

[Source](../../src/main/java/valthorne/collections/stack/DoubleFastStack.java#L21)

Array-backed last-in, first-out storage for double values. Positive
capacity doubles when full; push and pop normally access only the top slot.
Empty pop returns -1, which is also a valid stored value, whereas empty
peek throws. Use isEmpty when distinguishing an empty pop matters.

Iterators walk top to bottom over live storage with an initial cursor and
no concurrent-modification checks. Do not mutate the stack while iterating.
The class is mutable and supplies no thread synchronization. Construct with
positive capacity: a zero-length array cannot grow by doubling.

<details>
<summary>DoubleFastStack operation reference (10 declarations)</summary>

#### Constructor

```java
public DoubleFastStack()
```

Creates an empty stack with ten storage slots. The backing array grows
when later pushes fill its current capacity.

#### Constructor

```java
public DoubleFastStack(int size)
```

Allocates the requested initial storage without placing any elements in it.
Use positive capacity; zero is accepted here but the doubling growth rule
cannot make a zero-length array usable for push.

- **`size`** — initial number of storage slots

**Throws `NegativeArraySizeException`:** if size is negative

#### push

```java
public void push(double data)
```

Appends a value at the top, doubling and copying the array when full.
Existing order is preserved. Storage itself does not box primitive values.

- **`data`** — value to push

**Throws `ArrayIndexOutOfBoundsException`:** if constructed with zero capacity

#### pop

```java
public double pop()
```

Removes the most recently pushed element and clears its vacated slot.
Empty stacks return -1 without changing state; that result is not a
unique emptiness indicator because the same value can be pushed.

**Returns:** previous top value, or -1 when empty

#### peek

```java
public double peek()
```

Reads the most recently pushed element without removing or copying it.
Unlike pop, an empty stack is reported by an exception.

**Returns:** current top value

**Throws `NoSuchElementException`:** if the stack is empty

#### isEmpty

```java
public boolean isEmpty()
```

Tests logical membership rather than backing-array capacity.

**Returns:** true when no elements are stored

#### size

```java
public int size()
```

Reads the number of occupied slots. Reserved backing capacity is excluded.

**Returns:** current element count

#### clear

```java
public void clear()
```

Fills the entire backing array with zero and resets the element count.
Allocated capacity is retained for reuse; existing iterators are invalid for
continued traversal after this mutation.

#### toString

```java
    public String toString()
```

Copies the occupied prefix and formats it in bottom-to-top storage order.
This order is the reverse of iterator traversal; unused capacity is omitted.

**Returns:** bracketed representation of the current elements

#### iterator

```java
    public Iterator<Double> iterator()
```

Creates a top-to-bottom iterator whose initial cursor is the current top.
It reads live storage, does not detect later mutation, and does not support
removal. Keep the stack unchanged while using the iterator.

**Returns:** new iterator over current stack positions

</details>

<a id="type-doublefaststack-doublefaststackiterator"></a>

### DoubleFastStack.DoubleFastStackIterator — internal support type

[Source](../../src/main/java/valthorne/collections/stack/DoubleFastStack.java#L150)

Live-storage iterator with an independent descending cursor initialized from
the enclosing stack's top slot. It is neither a snapshot nor fail-fast;
mutation of the enclosing stack during traversal is unsupported.

<details>
<summary>DoubleFastStack.DoubleFastStackIterator operation reference (3 declarations)</summary>

#### hasNext

```java
        public boolean hasNext()
```

Checks whether the saved cursor still addresses a nonnegative slot.
Does not compare against the enclosing stack's current size.

**Returns:** true when another cursor position remains

#### next

```java
        public Double next()
```

Reads the current live backing-array slot and decrements the cursor.
The primitive value is boxed for the Iterator interface.

**Returns:** next value in top-to-bottom order

**Throws `NoSuchElementException`:** if the cursor is exhausted

#### remove

```java
        public void remove()
```

Rejects iterator removal without changing storage or cursor state.
Use the enclosing stack's operations outside iteration instead.

**Throws `UnsupportedOperationException`:** always

</details>

<a id="type-faststack"></a>

### FastStack

[Source](../../src/main/java/valthorne/collections/stack/FastStack.java#L22)

Array-backed last-in, first-out storage for object references. Positive
capacity doubles when full; push and pop normally access only the top slot.
Empty pop returns null, which is also a valid stored value, whereas empty
peek throws. Use isEmpty when distinguishing an empty pop matters.

Iterators walk top to bottom over live storage with an initial cursor and
no concurrent-modification checks. Do not mutate the stack while iterating.
The class is mutable and supplies no thread synchronization. Construct with
positive capacity: a zero-length array cannot grow by doubling.

- **`<T>`** — stored reference type; null elements are allowed

<details>
<summary>FastStack operation reference (10 declarations)</summary>

#### Constructor

```java
public FastStack()
```

Creates an empty stack with ten storage slots. The backing array grows
when later pushes fill its current capacity.

#### Constructor

```java
public FastStack(int size)
```

Allocates the requested initial storage without placing any elements in it.
Use positive capacity; zero is accepted here but the doubling growth rule
cannot make a zero-length array usable for push.

- **`size`** — initial number of storage slots

**Throws `NegativeArraySizeException`:** if size is negative

#### push

```java
public void push(T data)
```

Appends a borrowed reference at the top, doubling and copying the array when full.
Existing order is preserved. Null is accepted like any other reference.

- **`data`** — value to push

**Throws `ArrayIndexOutOfBoundsException`:** if constructed with zero capacity

#### pop

```java
public T pop()
```

Removes the most recently pushed element and clears its vacated slot.
Empty stacks return null without changing state; that result is not a
unique emptiness indicator because the same value can be pushed.

**Returns:** previous top value, or null when empty

#### peek

```java
public T peek()
```

Reads the most recently pushed element without removing or copying it.
Unlike pop, an empty stack is reported by an exception.

**Returns:** current top value

**Throws `NoSuchElementException`:** if the stack is empty

#### isEmpty

```java
public boolean isEmpty()
```

Tests logical membership rather than backing-array capacity.

**Returns:** true when no elements are stored

#### size

```java
public int size()
```

Reads the number of occupied slots. Reserved backing capacity is excluded.

**Returns:** current element count

#### clear

```java
public void clear()
```

Fills the entire backing array with null and resets the element count.
Allocated capacity is retained for reuse; existing iterators are invalid for
continued traversal after this mutation.

#### toString

```java
    public String toString()
```

Copies the occupied prefix and formats it in bottom-to-top storage order.
This order is the reverse of iterator traversal; unused capacity is omitted.

**Returns:** bracketed representation of the current elements

#### iterator

```java
    public Iterator<T> iterator()
```

Creates a top-to-bottom iterator whose initial cursor is the current top.
It reads live storage, does not detect later mutation, and does not support
removal. Keep the stack unchanged while using the iterator.

**Returns:** new iterator over current stack positions

</details>

<a id="type-faststack-faststackiterator"></a>

### FastStack.FastStackIterator — internal support type

[Source](../../src/main/java/valthorne/collections/stack/FastStack.java#L152)

Live-storage iterator with an independent descending cursor initialized from
the enclosing stack's top slot. It is neither a snapshot nor fail-fast;
mutation of the enclosing stack during traversal is unsupported.

<details>
<summary>FastStack.FastStackIterator operation reference (3 declarations)</summary>

#### hasNext

```java
        public boolean hasNext()
```

Checks whether the saved cursor still addresses a nonnegative slot.
Does not compare against the enclosing stack's current size.

**Returns:** true when another cursor position remains

#### next

```java
        public T next()
```

Reads the current live backing-array slot and decrements the cursor.
Returns the stored reference without copying it.

**Returns:** next value in top-to-bottom order

**Throws `NoSuchElementException`:** if the cursor is exhausted

#### remove

```java
        public void remove()
```

Rejects iterator removal without changing storage or cursor state.
Use the enclosing stack's operations outside iteration instead.

**Throws `UnsupportedOperationException`:** always

</details>

<a id="type-floatfaststack"></a>

### FloatFastStack

[Source](../../src/main/java/valthorne/collections/stack/FloatFastStack.java#L21)

Array-backed last-in, first-out storage for float values. Positive
capacity doubles when full; push and pop normally access only the top slot.
Empty pop returns -1, which is also a valid stored value, whereas empty
peek throws. Use isEmpty when distinguishing an empty pop matters.

Iterators walk top to bottom over live storage with an initial cursor and
no concurrent-modification checks. Do not mutate the stack while iterating.
The class is mutable and supplies no thread synchronization. Construct with
positive capacity: a zero-length array cannot grow by doubling.

<details>
<summary>FloatFastStack operation reference (10 declarations)</summary>

#### Constructor

```java
public FloatFastStack()
```

Creates an empty stack with ten storage slots. The backing array grows
when later pushes fill its current capacity.

#### Constructor

```java
public FloatFastStack(int size)
```

Allocates the requested initial storage without placing any elements in it.
Use positive capacity; zero is accepted here but the doubling growth rule
cannot make a zero-length array usable for push.

- **`size`** — initial number of storage slots

**Throws `NegativeArraySizeException`:** if size is negative

#### push

```java
public void push(float data)
```

Appends a value at the top, doubling and copying the array when full.
Existing order is preserved. Storage itself does not box primitive values.

- **`data`** — value to push

**Throws `ArrayIndexOutOfBoundsException`:** if constructed with zero capacity

#### pop

```java
public float pop()
```

Removes the most recently pushed element and clears its vacated slot.
Empty stacks return -1 without changing state; that result is not a
unique emptiness indicator because the same value can be pushed.

**Returns:** previous top value, or -1 when empty

#### peek

```java
public float peek()
```

Reads the most recently pushed element without removing or copying it.
Unlike pop, an empty stack is reported by an exception.

**Returns:** current top value

**Throws `NoSuchElementException`:** if the stack is empty

#### isEmpty

```java
public boolean isEmpty()
```

Tests logical membership rather than backing-array capacity.

**Returns:** true when no elements are stored

#### size

```java
public int size()
```

Reads the number of occupied slots. Reserved backing capacity is excluded.

**Returns:** current element count

#### clear

```java
public void clear()
```

Fills the entire backing array with zero and resets the element count.
Allocated capacity is retained for reuse; existing iterators are invalid for
continued traversal after this mutation.

#### toString

```java
    public String toString()
```

Copies the occupied prefix and formats it in bottom-to-top storage order.
This order is the reverse of iterator traversal; unused capacity is omitted.

**Returns:** bracketed representation of the current elements

#### iterator

```java
    public Iterator<Float> iterator()
```

Creates a top-to-bottom iterator whose initial cursor is the current top.
It reads live storage, does not detect later mutation, and does not support
removal. Keep the stack unchanged while using the iterator.

**Returns:** new iterator over current stack positions

</details>

<a id="type-floatfaststack-floatfaststackiterator"></a>

### FloatFastStack.FloatFastStackIterator — internal support type

[Source](../../src/main/java/valthorne/collections/stack/FloatFastStack.java#L148)

Live-storage iterator with an independent descending cursor initialized from
the enclosing stack's top slot. It is neither a snapshot nor fail-fast;
mutation of the enclosing stack during traversal is unsupported.

<details>
<summary>FloatFastStack.FloatFastStackIterator operation reference (3 declarations)</summary>

#### hasNext

```java
        public boolean hasNext()
```

Checks whether the saved cursor still addresses a nonnegative slot.
Does not compare against the enclosing stack's current size.

**Returns:** true when another cursor position remains

#### next

```java
        public Float next()
```

Reads the current live backing-array slot and decrements the cursor.
The primitive value is boxed for the Iterator interface.

**Returns:** next value in top-to-bottom order

**Throws `NoSuchElementException`:** if the cursor is exhausted

#### remove

```java
        public void remove()
```

Rejects iterator removal without changing storage or cursor state.
Use the enclosing stack's operations outside iteration instead.

**Throws `UnsupportedOperationException`:** always

</details>

<a id="type-intfaststack"></a>

### IntFastStack

[Source](../../src/main/java/valthorne/collections/stack/IntFastStack.java#L21)

Array-backed last-in, first-out storage for int values. Positive
capacity doubles when full; push and pop normally access only the top slot.
Empty pop returns -1, which is also a valid stored value, whereas empty
peek throws. Use isEmpty when distinguishing an empty pop matters.

Iterators walk top to bottom over live storage with an initial cursor and
no concurrent-modification checks. Do not mutate the stack while iterating.
The class is mutable and supplies no thread synchronization. Construct with
positive capacity: a zero-length array cannot grow by doubling.

<details>
<summary>IntFastStack operation reference (10 declarations)</summary>

#### Constructor

```java
public IntFastStack()
```

Creates an empty stack with ten storage slots. The backing array grows
when later pushes fill its current capacity.

#### Constructor

```java
public IntFastStack(int size)
```

Allocates the requested initial storage without placing any elements in it.
Use positive capacity; zero is accepted here but the doubling growth rule
cannot make a zero-length array usable for push.

- **`size`** — initial number of storage slots

**Throws `NegativeArraySizeException`:** if size is negative

#### push

```java
public void push(int data)
```

Appends a value at the top, doubling and copying the array when full.
Existing order is preserved. Storage itself does not box primitive values.

- **`data`** — value to push

**Throws `ArrayIndexOutOfBoundsException`:** if constructed with zero capacity

#### pop

```java
public int pop()
```

Removes the most recently pushed element and clears its vacated slot.
Empty stacks return -1 without changing state; that result is not a
unique emptiness indicator because the same value can be pushed.

**Returns:** previous top value, or -1 when empty

#### peek

```java
public int peek()
```

Reads the most recently pushed element without removing or copying it.
Unlike pop, an empty stack is reported by an exception.

**Returns:** current top value

**Throws `NoSuchElementException`:** if the stack is empty

#### isEmpty

```java
public boolean isEmpty()
```

Tests logical membership rather than backing-array capacity.

**Returns:** true when no elements are stored

#### size

```java
public int size()
```

Reads the number of occupied slots. Reserved backing capacity is excluded.

**Returns:** current element count

#### clear

```java
public void clear()
```

Fills the entire backing array with zero and resets the element count.
Allocated capacity is retained for reuse; existing iterators are invalid for
continued traversal after this mutation.

#### toString

```java
    public String toString()
```

Copies the occupied prefix and formats it in bottom-to-top storage order.
This order is the reverse of iterator traversal; unused capacity is omitted.

**Returns:** bracketed representation of the current elements

#### iterator

```java
    public Iterator<Integer> iterator()
```

Creates a top-to-bottom iterator whose initial cursor is the current top.
It reads live storage, does not detect later mutation, and does not support
removal. Keep the stack unchanged while using the iterator.

**Returns:** new iterator over current stack positions

</details>

<a id="type-intfaststack-intfaststackiterator"></a>

### IntFastStack.IntFastStackIterator — internal support type

[Source](../../src/main/java/valthorne/collections/stack/IntFastStack.java#L148)

Live-storage iterator with an independent descending cursor initialized from
the enclosing stack's top slot. It is neither a snapshot nor fail-fast;
mutation of the enclosing stack during traversal is unsupported.

<details>
<summary>IntFastStack.IntFastStackIterator operation reference (3 declarations)</summary>

#### hasNext

```java
        public boolean hasNext()
```

Checks whether the saved cursor still addresses a nonnegative slot.
Does not compare against the enclosing stack's current size.

**Returns:** true when another cursor position remains

#### next

```java
        public Integer next()
```

Reads the current live backing-array slot and decrements the cursor.
The primitive value is boxed for the Iterator interface.

**Returns:** next value in top-to-bottom order

**Throws `NoSuchElementException`:** if the cursor is exhausted

#### remove

```java
        public void remove()
```

Rejects iterator removal without changing storage or cursor state.
Use the enclosing stack's operations outside iteration instead.

**Throws `UnsupportedOperationException`:** always

</details>

<a id="type-longfaststack"></a>

### LongFastStack

[Source](../../src/main/java/valthorne/collections/stack/LongFastStack.java#L21)

Array-backed last-in, first-out storage for long values. Positive
capacity doubles when full; push and pop normally access only the top slot.
Empty pop returns -1, which is also a valid stored value, whereas empty
peek throws. Use isEmpty when distinguishing an empty pop matters.

Iterators walk top to bottom over live storage with an initial cursor and
no concurrent-modification checks. Do not mutate the stack while iterating.
The class is mutable and supplies no thread synchronization. Construct with
positive capacity: a zero-length array cannot grow by doubling.

<details>
<summary>LongFastStack operation reference (10 declarations)</summary>

#### Constructor

```java
public LongFastStack()
```

Creates an empty stack with ten storage slots. The backing array grows
when later pushes fill its current capacity.

#### Constructor

```java
public LongFastStack(int size)
```

Allocates the requested initial storage without placing any elements in it.
Use positive capacity; zero is accepted here but the doubling growth rule
cannot make a zero-length array usable for push.

- **`size`** — initial number of storage slots

**Throws `NegativeArraySizeException`:** if size is negative

#### push

```java
public void push(long data)
```

Appends a value at the top, doubling and copying the array when full.
Existing order is preserved. Storage itself does not box primitive values.

- **`data`** — value to push

**Throws `ArrayIndexOutOfBoundsException`:** if constructed with zero capacity

#### pop

```java
public long pop()
```

Removes the most recently pushed element and clears its vacated slot.
Empty stacks return -1 without changing state; that result is not a
unique emptiness indicator because the same value can be pushed.

**Returns:** previous top value, or -1 when empty

#### peek

```java
public long peek()
```

Reads the most recently pushed element without removing or copying it.
Unlike pop, an empty stack is reported by an exception.

**Returns:** current top value

**Throws `NoSuchElementException`:** if the stack is empty

#### isEmpty

```java
public boolean isEmpty()
```

Tests logical membership rather than backing-array capacity.

**Returns:** true when no elements are stored

#### size

```java
public int size()
```

Reads the number of occupied slots. Reserved backing capacity is excluded.

**Returns:** current element count

#### clear

```java
public void clear()
```

Fills the entire backing array with zero and resets the element count.
Allocated capacity is retained for reuse; existing iterators are invalid for
continued traversal after this mutation.

#### toString

```java
    public String toString()
```

Copies the occupied prefix and formats it in bottom-to-top storage order.
This order is the reverse of iterator traversal; unused capacity is omitted.

**Returns:** bracketed representation of the current elements

#### iterator

```java
    public Iterator<Long> iterator()
```

Creates a top-to-bottom iterator whose initial cursor is the current top.
It reads live storage, does not detect later mutation, and does not support
removal. Keep the stack unchanged while using the iterator.

**Returns:** new iterator over current stack positions

</details>

<a id="type-longfaststack-longfaststackiterator"></a>

### LongFastStack.LongFastStackIterator — internal support type

[Source](../../src/main/java/valthorne/collections/stack/LongFastStack.java#L150)

Live-storage iterator with an independent descending cursor initialized from
the enclosing stack's top slot. It is neither a snapshot nor fail-fast;
mutation of the enclosing stack during traversal is unsupported.

<details>
<summary>LongFastStack.LongFastStackIterator operation reference (3 declarations)</summary>

#### hasNext

```java
        public boolean hasNext()
```

Checks whether the saved cursor still addresses a nonnegative slot.
Does not compare against the enclosing stack's current size.

**Returns:** true when another cursor position remains

#### next

```java
        public Long next()
```

Reads the current live backing-array slot and decrements the cursor.
The primitive value is boxed for the Iterator interface.

**Returns:** next value in top-to-bottom order

**Throws `NoSuchElementException`:** if the cursor is exhausted

#### remove

```java
        public void remove()
```

Rejects iterator removal without changing storage or cursor state.
Use the enclosing stack's operations outside iteration instead.

**Throws `UnsupportedOperationException`:** always

</details>

<a id="type-shortfaststack"></a>

### ShortFastStack

[Source](../../src/main/java/valthorne/collections/stack/ShortFastStack.java#L21)

Array-backed last-in, first-out storage for short values. Positive
capacity doubles when full; push and pop normally access only the top slot.
Empty pop returns -1, which is also a valid stored value, whereas empty
peek throws. Use isEmpty when distinguishing an empty pop matters.

Iterators walk top to bottom over live storage with an initial cursor and
no concurrent-modification checks. Do not mutate the stack while iterating.
The class is mutable and supplies no thread synchronization. Construct with
positive capacity: a zero-length array cannot grow by doubling.

<details>
<summary>ShortFastStack operation reference (10 declarations)</summary>

#### Constructor

```java
public ShortFastStack()
```

Creates an empty stack with ten storage slots. The backing array grows
when later pushes fill its current capacity.

#### Constructor

```java
public ShortFastStack(int size)
```

Allocates the requested initial storage without placing any elements in it.
Use positive capacity; zero is accepted here but the doubling growth rule
cannot make a zero-length array usable for push.

- **`size`** — initial number of storage slots

**Throws `NegativeArraySizeException`:** if size is negative

#### push

```java
public void push(short data)
```

Appends a value at the top, doubling and copying the array when full.
Existing order is preserved. Storage itself does not box primitive values.

- **`data`** — value to push

**Throws `ArrayIndexOutOfBoundsException`:** if constructed with zero capacity

#### pop

```java
public short pop()
```

Removes the most recently pushed element and clears its vacated slot.
Empty stacks return -1 without changing state; that result is not a
unique emptiness indicator because the same value can be pushed.

**Returns:** previous top value, or -1 when empty

#### peek

```java
public short peek()
```

Reads the most recently pushed element without removing or copying it.
Unlike pop, an empty stack is reported by an exception.

**Returns:** current top value

**Throws `NoSuchElementException`:** if the stack is empty

#### isEmpty

```java
public boolean isEmpty()
```

Tests logical membership rather than backing-array capacity.

**Returns:** true when no elements are stored

#### size

```java
public int size()
```

Reads the number of occupied slots. Reserved backing capacity is excluded.

**Returns:** current element count

#### clear

```java
public void clear()
```

Fills the entire backing array with zero and resets the element count.
Allocated capacity is retained for reuse; existing iterators are invalid for
continued traversal after this mutation.

#### toString

```java
    public String toString()
```

Copies the occupied prefix and formats it in bottom-to-top storage order.
This order is the reverse of iterator traversal; unused capacity is omitted.

**Returns:** bracketed representation of the current elements

#### iterator

```java
    public Iterator<Short> iterator()
```

Creates a top-to-bottom iterator whose initial cursor is the current top.
It reads live storage, does not detect later mutation, and does not support
removal. Keep the stack unchanged while using the iterator.

**Returns:** new iterator over current stack positions

</details>

<a id="type-shortfaststack-shortfaststackiterator"></a>

### ShortFastStack.ShortFastStackIterator — internal support type

[Source](../../src/main/java/valthorne/collections/stack/ShortFastStack.java#L152)

Live-storage iterator with an independent descending cursor initialized from
the enclosing stack's top slot. It is neither a snapshot nor fail-fast;
mutation of the enclosing stack during traversal is unsupported.

<details>
<summary>ShortFastStack.ShortFastStackIterator operation reference (3 declarations)</summary>

#### hasNext

```java
        public boolean hasNext()
```

Checks whether the saved cursor still addresses a nonnegative slot.
Does not compare against the enclosing stack's current size.

**Returns:** true when another cursor position remains

#### next

```java
        public Short next()
```

Reads the current live backing-array slot and decrements the cursor.
The primitive value is boxed for the Iterator interface.

**Returns:** next value in top-to-bottom order

**Throws `NoSuchElementException`:** if the cursor is exhausted

#### remove

```java
        public void remove()
```

Rejects iterator removal without changing storage or cursor state.
Use the enclosing stack's operations outside iteration instead.

**Throws `UnsupportedOperationException`:** always

</details>

## Related guides

- [Resizable and unordered arrays](arrays.md)
- [Object pooling](pooling.md)
