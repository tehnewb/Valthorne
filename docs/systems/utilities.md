# Text, number, and reflection utilities

Author: Albert Beaupre

[System manual](README.md)

## Purpose

These utilities collect text processing, number conversion/formatting, and reflection operations that do not belong to a larger runtime subsystem. Use the component reference to select a narrow operation rather than relying on a broad class name to imply behavior.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Text operations | TextUtility provides its documented parsing, transformation, and string helpers. |
| Numeric convenience | NumberUtility handles supported numeric representations and conversions. |
| Reflection | ReflectionUtility inspects or accesses Java members through its documented lookup rules. |
| Error behavior | Conversion failures and reflective exceptions follow the selected method's contract. |

## Getting started

1. Identify the exact operation and expected input format.
2. Read whether the helper returns a fallback value or throws on invalid input.
3. Use reflection only where a normal typed API does not supply the required extension point.
4. Keep parsing and reflection outside hot render loops unless their measured cost is acceptable.

## Ownership and lifecycle

Utilities do not establish a separate lifecycle, but reflected targets and returned references still belong to their original owners. Modern Java access restrictions can affect reflective access.

## Important behavior

- A fallback numeric result can hide malformed data if the caller never checks validity.
- Reflection member visibility and inheritance rules matter to lookup.
- Formatting text for display and serializing a stable interchange format are different requirements.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`NumberUtility`](#type-numberutility)
- [`ReflectionUtility`](#type-reflectionutility)
- [`TextUtility`](#type-textutility)

<a id="type-numberutility"></a>

### NumberUtility

[Source](../../src/main/java/valthorne/utility/NumberUtility.java#L82)

##### NumberUtility

`NumberUtility` is a mutable, chainable numeric helper built around a single internal
`double` value. It is designed for situations where you want to progressively transform
a number through a fluent API without repeatedly reassigning temporary variables.

This class extends `Number`, which means it can still be used in places where a normal
numeric wrapper is expected, while also exposing many convenience methods for arithmetic,
rounding, comparisons, percentage operations, trigonometry, logarithms, bitwise operations,
formatting, and unit conversion.

##### How this class works

- The instance stores one mutable numeric value in `number`.

- Most modifier methods update that value and return `this` for chaining.

- Query methods return derived information without replacing the stored value.

- Because the internal representation is a `double`, some operations are approximate by nature.

##### When this class is useful

- Quick math pipelines in gameplay code

- Formatting numbers for UI or debugging output

- Percentage and clamping operations

- Simple rounding or conversion helpers

- Readable chained numeric transformations

##### Important notes

- This class is mutable and not thread-safe.

- Bitwise methods cast the current value to an `int` before applying the operation.

- Methods such as `fact()`, `sqrt()`, `log()`, and `recip()` validate input and throw when invalid.

- Because this class stores a `double`, floating-point precision rules still apply.

##### Example

```java
NumberUtility damage = new NumberUtility(125);

double result = damage
        .incrByPerc(10)
        .clamp(0, 200)
        .round(2)
        .doubleValue();

System.out.println(result);

String formatted = new NumberUtility(15342.875)
        .divide(3)
        .round(2)
        .format("#,##0.00");

System.out.println(formatted);

NumberUtility movement = new NumberUtility(64);
movement
        .mult(1.5)
        .subtr(4)
        .ceil();

System.out.println(movement.intValue());
```

<details>
<summary>NumberUtility operation reference (55 declarations)</summary>

#### Constructor

```java
public NumberUtility()
```

Creates a new utility initialized to zero.

This constructor is useful when you want to create the utility first and assign or build
the value later through chained operations.

#### Constructor

```java
public NumberUtility(double number)
```

Creates a new utility initialized with the provided number.

The given value becomes the internal mutable number used by every later operation.

- **`number`** — the initial numeric value

#### set

```java
public NumberUtility set(double number)
```

Replaces the current internal value.

This is useful when reusing the same instance for a new numeric workflow.

- **`number`** — the new value to store

**Returns:** this utility instance for chaining

#### percent

```java
public NumberUtility percent(double input)
```

Replaces the current value with the requested percentage of the current value.

For example, if the current value is `200` and the input percentage is `25`,
the new value becomes `50`.

- **`input`** — the percentage to extract from the current value

**Returns:** this utility instance for chaining

#### clamp

```java
public NumberUtility clamp(double min, double max)
```

Clamps the current value between the provided minimum and maximum.

If the value is below `min`, it becomes `min`. If it is above `max`,
it becomes `max`. Otherwise it remains unchanged.

- **`min`** — the minimum allowed value
- **`max`** — the maximum allowed value

**Returns:** this utility instance for chaining

#### within

```java
public boolean within(double min, double max)
```

Checks whether the current value lies within the provided inclusive range.

Both range endpoints are included in the comparison.

- **`min`** — the lower inclusive bound
- **`max`** — the upper inclusive bound

**Returns:** true if the current value is within the range, otherwise false

#### add

```java
public NumberUtility add(double value)
```

Adds the provided value to the current number.

- **`value`** — the amount to add

**Returns:** this utility instance for chaining

#### subtr

```java
public NumberUtility subtr(double value)
```

Subtracts the provided value from the current number.

- **`value`** — the amount to subtract

**Returns:** this utility instance for chaining

#### mult

```java
public NumberUtility mult(double factor)
```

Multiplies the current value by the provided factor.

- **`factor`** — the multiplier to apply

**Returns:** this utility instance for chaining

#### divide

```java
public NumberUtility divide(double divisor)
```

Divides the current value by the provided divisor.

This method follows normal Java floating-point division behavior.

- **`divisor`** — the divisor to use

**Returns:** this utility instance for chaining

#### power

```java
public NumberUtility power(double exponent)
```

Raises the current value to the specified exponent.

This method delegates to `Math#pow(double, double)` using the current number
as the base.

- **`exponent`** — the exponent to apply

**Returns:** this utility instance for chaining

#### round

```java
public NumberUtility round(int decimalPlaces)
```

Rounds the current value to the given number of decimal places using
`RoundingMode#HALF_UP`.

- **`decimalPlaces`** — the number of decimal places to retain

**Returns:** this utility instance for chaining

#### round

```java
public NumberUtility round(int decimalPlaces, RoundingMode roundingMode)
```

Rounds the current value to the given number of decimal places using the specified rounding mode.

This method uses `BigDecimal` so the rounding mode is explicit and predictable.

- **`decimalPlaces`** — the number of decimal places to retain
- **`roundingMode`** — the rounding mode to apply

**Returns:** this utility instance for chaining

**Throws `NullPointerException`:** if `roundingMode` is null

#### abs

```java
public NumberUtility abs()
```

Replaces the current value with its absolute value.

**Returns:** this utility instance for chaining

#### negate

```java
public NumberUtility negate()
```

Negates the current value.

Positive values become negative, negative values become positive, and zero remains zero.

**Returns:** this utility instance for chaining

#### randomize

```java
public NumberUtility randomize(double min, double max)
```

Replaces the current value with a random number in the provided range.

The range is generated using `ThreadLocalRandom`. The minimum is inclusive and the
maximum is exclusive in practice, following the behavior of uniform floating-point generation.

- **`min`** — the lower bound of the random range
- **`max`** — the upper bound of the random range

**Returns:** this utility instance for chaining

#### isEqualTo

```java
public boolean isEqualTo(double otherValue)
```

Checks whether the current value is exactly equal to another value.

This uses `Double#compare(double, double)` for an exact floating-point comparison.

- **`otherValue`** — the value to compare against

**Returns:** true if the two values are exactly equal, otherwise false

#### isGreaterThan

```java
public boolean isGreaterThan(double otherValue)
```

Checks whether the current value is greater than the provided value.

- **`otherValue`** — the comparison target

**Returns:** true if the current value is greater

#### isLessThan

```java
public boolean isLessThan(double otherValue)
```

Checks whether the current value is less than the provided value.

- **`otherValue`** — the comparison target

**Returns:** true if the current value is less

#### format

```java
public String format(String formatPattern)
```

Formats the current value using a custom `DecimalFormat` pattern.

The shared formatter is synchronized because `DecimalFormat` is mutable and not thread-safe.

- **`formatPattern`** — the decimal format pattern to apply

**Returns:** the formatted numeric string

**Throws `NullPointerException`:** if `formatPattern` is null

#### intValue

```java
@Override
    public int intValue()
```

Returns the current value as an `int`.

This performs a narrowing primitive conversion using Java cast rules.

**Returns:** the current value converted to int

#### longValue

```java
@Override
    public long longValue()
```

Returns the current value as a `long`.

This performs a narrowing primitive conversion using Java cast rules.

**Returns:** the current value converted to long

#### floatValue

```java
@Override
    public float floatValue()
```

Returns the current value as a `float`.

This performs a narrowing primitive conversion using Java cast rules.

**Returns:** the current value converted to float

#### doubleValue

```java
@Override
    public double doubleValue()
```

Returns the current value as a `double`.

**Returns:** the current stored double value

#### getFractPart

```java
public double getFractPart()
```

Returns the fractional part of the current value.

This subtracts the truncated integer portion from the current number.

**Returns:** the fractional component of the current value

#### incrByPerc

```java
public NumberUtility incrByPerc(double percentage)
```

Increases the current value by the specified percentage.

For example, increasing `100` by `25` results in `125`.

- **`percentage`** — the percentage increase to apply

**Returns:** this utility instance for chaining

#### decrByPerc

```java
public NumberUtility decrByPerc(double percentage)
```

Decreases the current value by the specified percentage.

For example, decreasing `100` by `25` results in `75`.

- **`percentage`** — the percentage decrease to apply

**Returns:** this utility instance for chaining

#### truncate

```java
public NumberUtility truncate()
```

Removes the fractional component of the current value.

This behaves like numeric truncation toward zero.

**Returns:** this utility instance for chaining

#### ceil

```java
public NumberUtility ceil()
```

Replaces the current value with its mathematical ceiling.

**Returns:** this utility instance for chaining

#### flr

```java
public NumberUtility flr()
```

Replaces the current value with its mathematical floor.

**Returns:** this utility instance for chaining

#### signum

```java
public int signum()
```

Returns the sign of the current value.

The result is `-1`, `0`, or `1` depending on whether the value is
negative, zero, or positive.

**Returns:** the sign indicator of the current value

#### percDiff

```java
public double percDiff(double otherValue)
```

Computes the percentage difference between the current value and another value.

The calculation is based on the absolute difference divided by the absolute value of
`otherValue`, then multiplied by `100`.

- **`otherValue`** — the reference value

**Returns:** the percentage difference relative to `otherValue`

#### fact

```java
public NumberUtility fact()
```

Replaces the current value with its factorial.

This method only accepts non-negative whole-number values. Because the result is stored back
into a `double`, very large factorials will eventually lose precision.

**Returns:** this utility instance for chaining

**Throws `ArithmeticException`:** if the current value is negative or not a whole number

#### exp

```java
public NumberUtility exp()
```

Replaces the current value with Euler's exponential function of the current value.

This method delegates to `Math#exp(double)`.

**Returns:** this utility instance for chaining

#### log

```java
public NumberUtility log()
```

Replaces the current value with its natural logarithm.

This method is only valid for positive values.

**Returns:** this utility instance for chaining

**Throws `ArithmeticException`:** if the current value is not positive

#### roundToDecimalPlace

```java
public NumberUtility roundToDecimalPlace(int decimalPlace)
```

Rounds the current value to the requested decimal place using `Math#round(float)`-style scaling.

This is a lightweight rounding method compared to the `BigDecimal`-based
`round(int, RoundingMode)` method.

- **`decimalPlace`** — the number of decimal places to retain

**Returns:** this utility instance for chaining

#### sin

```java
public NumberUtility sin()
```

Replaces the current value with its sine.

The input is interpreted in radians.

**Returns:** this utility instance for chaining

#### cos

```java
public NumberUtility cos()
```

Replaces the current value with its cosine.

The input is interpreted in radians.

**Returns:** this utility instance for chaining

#### tan

```java
public NumberUtility tan()
```

Replaces the current value with its tangent.

The input is interpreted in radians.

**Returns:** this utility instance for chaining

#### sqrt

```java
public NumberUtility sqrt()
```

Replaces the current value with its square root.

This method is only valid for zero and positive values.

**Returns:** this utility instance for chaining

**Throws `ArithmeticException`:** if the current value is negative

#### cubeRoot

```java
public NumberUtility cubeRoot()
```

Replaces the current value with its cube root.

This method delegates to `Math#cbrt(double)` and supports negative inputs.

**Returns:** this utility instance for chaining

#### hypot

```java
public NumberUtility hypot(double otherSide)
```

Replaces the current value with the hypotenuse of the current value and another side.

This method delegates to `Math#hypot(double, double)`.

- **`otherSide`** — the second side length

**Returns:** this utility instance for chaining

#### pow

```java
public NumberUtility pow(double base)
```

Raises the provided base to the power of the current value.

This is the inverse ordering of `power(double)`.

- **`base`** — the base to raise

**Returns:** this utility instance for chaining

#### bitAnd

```java
public NumberUtility bitAnd(int otherValue)
```

Applies a bitwise AND between the integer form of the current value and the provided value.

The current value is first cast to `int`, the bitwise operation is applied, and the
resulting integer is stored back as a `double`.

- **`otherValue`** — the integer value to AND with

**Returns:** this utility instance for chaining

#### bitOr

```java
public NumberUtility bitOr(int otherValue)
```

Applies a bitwise OR between the integer form of the current value and the provided value.

The current value is first cast to `int`, the bitwise operation is applied, and the
resulting integer is stored back as a `double`.

- **`otherValue`** — the integer value to OR with

**Returns:** this utility instance for chaining

#### bitXor

```java
public NumberUtility bitXor(int otherValue)
```

Applies a bitwise XOR between the integer form of the current value and the provided value.

The current value is first cast to `int`, the bitwise operation is applied, and the
resulting integer is stored back as a `double`.

- **`otherValue`** — the integer value to XOR with

**Returns:** this utility instance for chaining

#### log

```java
public NumberUtility log(double base)
```

Replaces the current value with its logarithm in the specified base.

Both the current value and the base must be positive, and the base must not equal `1`.

- **`base`** — the logarithm base

**Returns:** this utility instance for chaining

**Throws `ArithmeticException`:** if the input or base is invalid

#### isApproxEqualTo

```java
public boolean isApproxEqualTo(double otherValue, double epsilon)
```

Checks whether the current value is approximately equal to another value using a tolerance.

- **`otherValue`** — the comparison target
- **`epsilon`** — the maximum allowed absolute difference

**Returns:** true if the values are within `epsilon` of each other

#### roundToNearestMultiple

```java
public NumberUtility roundToNearestMultiple(double multiple)
```

Rounds the current value to the nearest multiple of the provided step.

For example, rounding `37` to the nearest multiple of `5` produces `35`.

- **`multiple`** — the multiple to round toward

**Returns:** this utility instance for chaining

#### recip

```java
public NumberUtility recip()
```

Replaces the current value with its reciprocal.

The reciprocal is `1 / number`.

**Returns:** this utility instance for chaining

**Throws `ArithmeticException`:** if the current value is zero

#### isEven

```java
public boolean isEven()
```

Checks whether the integer form of the current value is even.

The current value is cast to `long` before evaluation.

**Returns:** true if the integer form is even

#### isOdd

```java
public boolean isOdd()
```

Checks whether the integer form of the current value is odd.

The current value is cast to `long` before evaluation.

**Returns:** true if the integer form is odd

#### absDiff

```java
public double absDiff(double otherValue)
```

Computes the absolute difference between the current value and another value.

- **`otherValue`** — the comparison target

**Returns:** the absolute numeric difference

#### convert

```java
public long convert(TimeUnit from, TimeUnit to)
```

Converts the current value between `TimeUnit`s.

The current number is cast to `long` before conversion.

- **`from`** — the source time unit
- **`to`** — the target time unit

**Returns:** the converted long value

**Throws `NullPointerException`:** if `from` or `to` is null

#### toString

```java
@Override
    public String toString()
```

Returns the current internal number as a string.

This method delegates to `String#valueOf(double)`.

**Returns:** the current numeric value as text

</details>

<a id="type-reflectionutility"></a>

### ReflectionUtility

[Source](../../src/main/java/valthorne/utility/ReflectionUtility.java#L13)

Provides JavaBean-style accessor names and exact-signature method discovery.
Name generation does not verify that an accessor exists or invoke it. Discovery
includes methods declared directly on a class and public inherited methods, without
changing accessibility or performing argument conversion.

<details>
<summary>ReflectionUtility operation reference (3 declarations)</summary>

#### toSetterName

```java
public static String toSetterName(String fieldName)
```

Converts a field name to its corresponding setter method name.
For example, "fieldName" becomes "setFieldName".

- **`fieldName`** — The name of the field.

**Returns:** The setter method name.

**Throws `IllegalArgumentException`:** if fieldName is null or empty.

#### toGetterName

```java
public static String toGetterName(java.lang.reflect.Field field)
```

Converts a field name to its corresponding getter method name.
For boolean fields, returns "isFieldName"; for others, returns "getFieldName".

- **`field`** — The field to generate the getter name for.

**Returns:** The getter method name.

**Throws `IllegalArgumentException`:** if field is null.

#### hasMethod

```java
public static boolean hasMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes)
```

Checks for an exact method signature, first among declared methods of any
visibility and then among public inherited methods. Parameter types must match
the declaration exactly; boxing and assignable argument types are not resolved.
A security failure in the initial lookup is reported to standard error and
returns false; a security failure during the inherited lookup can propagate.

- **`clazz`** — the class to inspect
- **`methodName`** — the name of the method
- **`parameterTypes`** — the parameter types of the method

**Returns:** true if the method exists, false otherwise

**Throws `NullPointerException`:** if the class or method name is null

</details>

<a id="type-textutility"></a>

### TextUtility

[Source](../../src/main/java/valthorne/utility/TextUtility.java#L67)

##### TextUtility

`TextUtility` is a mutable, chainable text helper built for situations where you want to
progressively transform a single piece of text through a fluent API. Instead of repeatedly
creating new strings at the call site and manually reassigning them, this class keeps an internal
`String` value and lets you apply multiple operations to that value in sequence.

The class is intentionally stateful. Most modifier methods update the current internal text and
then return `this`, which makes it convenient for method chaining. This design works well in
UI formatting, debug output generation, lightweight text cleanup, quick user-facing string
transformations, and other engine-side or tooling-side scenarios where a mutable wrapper is more
convenient than manually composing several separate string expressions.

##### How this class behaves

- The current text is stored in a single internal field.

- Most transformation methods mutate that field and return the same instance.

- Query methods such as word counts or palindrome checks inspect the current text without replacing it.

- Factory methods are provided for common primitive types so this utility can be created fluently.

##### Important design notes

- This class is mutable, so one instance should usually represent one evolving text value.

- Null input is tolerated in some places, but most transformation methods expect a non-null internal string.

- Methods that interpret the internal text as another type, such as `formatStorageUnits()`, require compatible content.

- Regex-based methods use the Java regular-expression engine, not plain literal replacement.

##### Example

```java
String result = TextUtility.of("hello amazing world")
        .upperFirst()
        .withSuffix("from Valthorne")
        .abbreviate(24)
        .toString();

System.out.println(result);

TextUtility utility = TextUtility.of("playerHealthValue");
utility.camelCaseToWords().toTitleCase();
System.out.println(utility); // Player Health Value

String encoded = TextUtility.of("save data")
        .base64Encode()
        .toString();

System.out.println(encoded);
```

<details>
<summary>TextUtility operation reference (39 declarations)</summary>

#### VOWELS

```java
public static final  String[] VOWELS
```

Common vowels used by `withPrefix(String)` when deciding whether to prepend
`"a"` or `"an"`.

#### Constructor

```java
public TextUtility(String string)
```

Creates a new utility instance that wraps the provided text.

The provided value becomes the internal text that all future operations read from and write to.
No defensive copy is needed because `String` is immutable.

- **`string`** — the initial text value to wrap

#### of

```java
public static TextUtility of(String string)
```

Creates a new utility instance from a string value.

This is the main convenience factory for fluent creation.

- **`string`** — the initial text value

**Returns:** a new utility instance wrapping the provided text

#### of

```java
public static TextUtility of(byte b)
```

Creates a new utility instance from a byte value.

The byte is converted using `String#valueOf(int)` before being wrapped.

- **`b`** — the byte value to wrap

**Returns:** a new utility instance containing the byte as text

#### of

```java
public static TextUtility of(char c)
```

Creates a new utility instance from a character value.

The character is converted using `String#valueOf(char)` before being wrapped.

- **`c`** — the character value to wrap

**Returns:** a new utility instance containing the character as text

#### of

```java
public static TextUtility of(boolean b)
```

Creates a new utility instance from a boolean value.

The boolean is converted using `String#valueOf(boolean)` before being wrapped.

- **`b`** — the boolean value to wrap

**Returns:** a new utility instance containing the boolean as text

#### of

```java
public static TextUtility of(float f)
```

Creates a new utility instance from a float value.

The float is converted using `String#valueOf(float)` before being wrapped.

- **`f`** — the float value to wrap

**Returns:** a new utility instance containing the float as text

#### of

```java
public static TextUtility of(short s)
```

Creates a new utility instance from a short value.

The short is converted using `String#valueOf(int)` before being wrapped.

- **`s`** — the short value to wrap

**Returns:** a new utility instance containing the short as text

#### of

```java
public static TextUtility of(int integer)
```

Creates a new utility instance from an integer value.

The integer is converted using `String#valueOf(int)` before being wrapped.

- **`integer`** — the integer value to wrap

**Returns:** a new utility instance containing the integer as text

#### of

```java
public static TextUtility of(double d)
```

Creates a new utility instance from a double value.

The double is converted using `String#valueOf(double)` before being wrapped.

- **`d`** — the double value to wrap

**Returns:** a new utility instance containing the double as text

#### of

```java
public static TextUtility of(long l)
```

Creates a new utility instance from a long value.

The long is converted using `String#valueOf(long)` before being wrapped.

- **`l`** — the long value to wrap

**Returns:** a new utility instance containing the long as text

#### wrap

```java
public TextUtility wrap(int lineLength, boolean wrapWords)
```

Wraps the current text into multiple lines.

The text is split into whitespace-delimited words, then rebuilt line by line. When the next
word would exceed the requested line length, a newline is inserted first. The
`wrapWords` flag controls how the internal line-length tracker behaves after a forced
line break.

This method does not hyphenate or split long individual words. It wraps only at whitespace
boundaries.

- **`lineLength`** — the preferred maximum line length
- **`wrapWords`** — whether the current line length should continue tracking the moved word after a line break

**Returns:** this utility instance for chaining

#### withPrefix

```java
public TextUtility withPrefix(String string)
```

Prepends `"a"` or `"an"` to the provided input string.

This method does not inspect the current internal text. Instead, it uses the provided
argument as the noun phrase and then replaces the internal value with the resulting prefixed
text.

The decision is based on a simple first-character vowel test and is therefore intentionally
lightweight rather than linguistically perfect.

- **`string`** — the word or phrase to prefix

**Returns:** this utility instance for chaining

#### upperFirst

```java
public TextUtility upperFirst()
```

Uppercases the first character and lowercases the remainder of the text.

This is useful for normalizing a sentence-like word or short phrase when the desired result
is a single leading uppercase character followed by lowercase characters.

**Returns:** this utility instance for chaining

#### withSuffix

```java
public TextUtility withSuffix(String suffix)
```

Appends a suffix to the current text separated by a single space.

This method always inserts one space between the current text and the provided suffix.

- **`suffix`** — the suffix text to append

**Returns:** this utility instance for chaining

#### replaceAll

```java
public TextUtility replaceAll(String target, String replacement)
```

Replaces all regex matches in the current text.

This method delegates to `String#replaceAll(String, String)`, which means
`target` is treated as a regular expression rather than a literal string.

- **`target`** — the regex pattern to replace
- **`replacement`** — the replacement text

**Returns:** this utility instance for chaining

#### truncate

```java
public TextUtility truncate(int length)
```

Truncates the current text to the requested number of characters.

If the text is already short enough, it is left unchanged. This method performs a hard cut
and does not append ellipses.

- **`length`** — the maximum number of characters to keep

**Returns:** this utility instance for chaining

**Throws `IllegalArgumentException`:** if `length` is negative

#### countWords

```java
public int countWords()
```

Counts whitespace-delimited words in the current text.

Blank or null content returns zero. Word splitting is performed using one or more whitespace
characters.

**Returns:** the number of detected words

#### reverseWords

```java
public TextUtility reverseWords()
```

Reverses the order of words in the current text.

Word boundaries are determined using whitespace splitting. The characters inside each word are
preserved; only the word order changes.

**Returns:** this utility instance for chaining

#### isPangram

```java
public boolean isPangram()
```

Checks whether the current text is a pangram.

A pangram is a sentence or phrase that contains every English alphabet letter at least once.
Case is ignored during the test.

**Returns:** true if all letters from `a` through `z` appear at least once

#### formatStorageUnits

```java
public String formatStorageUnits()
```

Interprets the current text as a byte count and returns a formatted storage-unit string.

The internal text is parsed as a `long`. The returned value is formatted using binary
unit steps of 1024 for KB, MB, and GB.

This method does not replace the internal text. It only returns the formatted result.

**Returns:** the formatted byte-count string

**Throws `NumberFormatException`:** if the current text is not a valid integer byte count

#### removeNonAlphaNumeric

```java
public TextUtility removeNonAlphaNumeric()
```

Removes all non-alphanumeric characters except whitespace.

Characters outside `a-z`, `A-Z`, `0-9`, and whitespace are removed.

**Returns:** this utility instance for chaining

#### reverse

```java
public TextUtility reverse()
```

Reverses the characters in the current text.

This is a character-level reversal, not a word-order reversal.

**Returns:** this utility instance for chaining

#### isPalindrome

```java
public boolean isPalindrome()
```

Checks whether the current text reads the same forward and backward.

The comparison is case-insensitive. No normalization is applied for spaces or punctuation, so
those characters still affect the result.

**Returns:** true if the text is a case-insensitive palindrome

#### stripHtmlTags

```java
public TextUtility stripHtmlTags()
```

Removes simple HTML tags from the current text.

This is a regex-based strip operation intended for lightweight cleanup. It is not a full HTML
parser and should not be treated as a robust HTML sanitization tool.

**Returns:** this utility instance for chaining

#### camelCaseToWords

```java
public TextUtility camelCaseToWords()
```

Inserts spaces between lowercase-to-uppercase camel-case transitions.

For example, `"playerHealthValue"` becomes `"player Health Value"`.

**Returns:** this utility instance for chaining

#### countLines

```java
public int countLines()
```

Counts the number of lines in the current text.

Line counting is based on splitting with either Unix or Windows newline separators.

**Returns:** the number of lines represented by the current text

#### toTitleCase

```java
public TextUtility toTitleCase()
```

Converts each whitespace-delimited word to title-like casing.

The first character of each word is uppercased. The remainder of each word is lowercase.

**Returns:** this utility instance for chaining

#### abbreviate

```java
public TextUtility abbreviate(int maxLength)
```

Abbreviates the current text to a maximum length using ellipses.

If the text already fits, it remains unchanged. For very small maximum lengths of three or
less, the result becomes a sequence of dots with that exact length.

- **`maxLength`** — the maximum allowed result length

**Returns:** this utility instance for chaining

**Throws `IllegalArgumentException`:** if `maxLength` is negative

#### padToLength

```java
public TextUtility padToLength(int length)
```

Pads the current text with spaces until it reaches the requested length.

Padding is applied only when the current text is shorter than the target length.

- **`length`** — the minimum total length of the resulting text

**Returns:** this utility instance for chaining

**Throws `IllegalArgumentException`:** if `length` is negative

#### shuffle

```java
public TextUtility shuffle()
```

Randomly shuffles the characters in the current text.

The text is converted into a list of characters, shuffled using
`Collections#shuffle(List)`, and then rebuilt.

**Returns:** this utility instance for chaining

#### isNumeric

```java
public boolean isNumeric()
```

Checks whether the current text looks numeric.

This method supports optional leading negative signs and an optional decimal portion.
It is intended as a lightweight regex-based numeric test rather than a full parser.

**Returns:** true if the current text matches the numeric pattern used by this class

#### countConsonants

```java
public int countConsonants()
```

Counts consonant characters in the current text.

Vowels, digits, and whitespace are removed first. The remaining characters are counted as
consonants by this method's simple rule set.

**Returns:** the number of consonant characters detected

#### concatenate

```java
public TextUtility concatenate(String... strings)
```

Appends one or more additional strings directly to the current text.

No separator is inserted automatically. Each provided string is appended exactly as given.

- **`strings`** — the strings to append in order

**Returns:** this utility instance for chaining

#### base64Encode

```java
public TextUtility base64Encode()
```

Base64-encodes the current text using UTF-8 bytes.

The current text is first converted to UTF-8 bytes, encoded with
`Base64#getEncoder()`, and then stored back as text.

**Returns:** this utility instance for chaining

#### base64Decode

```java
public TextUtility base64Decode()
```

Base64-decodes the current text using UTF-8 bytes.

The current text is interpreted as Base64-encoded content, decoded into bytes, and then
converted back into a UTF-8 string.

**Returns:** this utility instance for chaining

**Throws `IllegalArgumentException`:** if the current text is not valid Base64

#### set

```java
public TextUtility set(String string)
```

Replaces the internal text with a new value.

This method is useful when you want to reuse the same utility instance with a different
source string.

- **`string`** — the new text value to store

**Returns:** this utility instance for chaining

#### toBytes

```java
public byte[] toBytes()
```

Converts the current text into UTF-8 bytes.

This is useful when the current transformed text needs to be written to a file, network
stream, cache payload, or other binary destination.

**Returns:** the UTF-8 byte representation of the current text

#### toString

```java
@Override
    public String toString()
```

Returns the current internal text value.

This is the main terminal operation when the utility has been used in a fluent chain and the
final text is needed.

**Returns:** the current wrapped text

</details>

## Related guides

- [Math and 2D geometry](math.md)
- [Classpath and filesystem utilities](files.md)
- [Plugin loading and lifecycle](plugins.md)
