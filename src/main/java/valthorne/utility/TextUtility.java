package valthorne.utility;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * <h1>TextUtility</h1>
 *
 * <p>
 * {@code TextUtility} is a mutable, chainable text helper built for situations where you want to
 * progressively transform a single piece of text through a fluent API. Instead of repeatedly
 * creating new strings at the call site and manually reassigning them, this class keeps an internal
 * {@link String} value and lets you apply multiple operations to that value in sequence.
 * </p>
 *
 * <p>
 * The class is intentionally stateful. Most modifier methods update the current internal text and
 * then return {@code this}, which makes it convenient for method chaining. This design works well in
 * UI formatting, debug output generation, lightweight text cleanup, quick user-facing string
 * transformations, and other engine-side or tooling-side scenarios where a mutable wrapper is more
 * convenient than manually composing several separate string expressions.
 * </p>
 *
 * <h2>How this class behaves</h2>
 * <ul>
 *     <li>The current text is stored in a single internal field.</li>
 *     <li>Most transformation methods mutate that field and return the same instance.</li>
 *     <li>Query methods such as word counts or palindrome checks inspect the current text without replacing it.</li>
 *     <li>Factory methods are provided for common primitive types so this utility can be created fluently.</li>
 * </ul>
 *
 * <h2>Important design notes</h2>
 * <ul>
 *     <li>This class is mutable, so one instance should usually represent one evolving text value.</li>
 *     <li>Null input is tolerated in some places, but most transformation methods expect a non-null internal string.</li>
 *     <li>Methods that interpret the internal text as another type, such as {@link #formatStorageUnits()}, require compatible content.</li>
 *     <li>Regex-based methods use the Java regular-expression engine, not plain literal replacement.</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * String result = TextUtility.of("hello amazing world")
 *         .upperFirst()
 *         .withSuffix("from Valthorne")
 *         .abbreviate(24)
 *         .toString();
 *
 * System.out.println(result);
 *
 * TextUtility utility = TextUtility.of("playerHealthValue");
 * utility.camelCaseToWords().toTitleCase();
 * System.out.println(utility); // Player Health Value
 *
 * String encoded = TextUtility.of("save data")
 *         .base64Encode()
 *         .toString();
 *
 * System.out.println(encoded);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public class TextUtility {

    /**
     * Common vowels used by {@link #withPrefix(String)} when deciding whether to prepend
     * {@code "a"} or {@code "an"}.
     */
    public static final String[] VOWELS = {"a", "e", "i", "o", "u", "A", "E", "I", "O", "U"};

    private String string; // The current mutable text value wrapped by this utility instance.

    /**
     * Creates a new utility instance that wraps the provided text.
     *
     * <p>
     * The provided value becomes the internal text that all future operations read from and write to.
     * No defensive copy is needed because {@link String} is immutable.
     * </p>
     *
     * @param string the initial text value to wrap
     */
    public TextUtility(String string) {
        this.string = string;
    }

    /**
     * Creates a new utility instance from a string value.
     *
     * <p>
     * This is the main convenience factory for fluent creation.
     * </p>
     *
     * @param string the initial text value
     * @return a new utility instance wrapping the provided text
     */
    public static TextUtility of(String string) {
        return new TextUtility(string);
    }

    /**
     * Creates a new utility instance from a byte value.
     *
     * <p>
     * The byte is converted using {@link String#valueOf(int)} before being wrapped.
     * </p>
     *
     * @param b the byte value to wrap
     * @return a new utility instance containing the byte as text
     */
    public static TextUtility of(byte b) {
        return new TextUtility(String.valueOf(b));
    }

    /**
     * Creates a new utility instance from a character value.
     *
     * <p>
     * The character is converted using {@link String#valueOf(char)} before being wrapped.
     * </p>
     *
     * @param c the character value to wrap
     * @return a new utility instance containing the character as text
     */
    public static TextUtility of(char c) {
        return new TextUtility(String.valueOf(c));
    }

    /**
     * Creates a new utility instance from a boolean value.
     *
     * <p>
     * The boolean is converted using {@link String#valueOf(boolean)} before being wrapped.
     * </p>
     *
     * @param b the boolean value to wrap
     * @return a new utility instance containing the boolean as text
     */
    public static TextUtility of(boolean b) {
        return new TextUtility(String.valueOf(b));
    }

    /**
     * Creates a new utility instance from a float value.
     *
     * <p>
     * The float is converted using {@link String#valueOf(float)} before being wrapped.
     * </p>
     *
     * @param f the float value to wrap
     * @return a new utility instance containing the float as text
     */
    public static TextUtility of(float f) {
        return new TextUtility(String.valueOf(f));
    }

    /**
     * Creates a new utility instance from a short value.
     *
     * <p>
     * The short is converted using {@link String#valueOf(int)} before being wrapped.
     * </p>
     *
     * @param s the short value to wrap
     * @return a new utility instance containing the short as text
     */
    public static TextUtility of(short s) {
        return new TextUtility(String.valueOf(s));
    }

    /**
     * Creates a new utility instance from an integer value.
     *
     * <p>
     * The integer is converted using {@link String#valueOf(int)} before being wrapped.
     * </p>
     *
     * @param integer the integer value to wrap
     * @return a new utility instance containing the integer as text
     */
    public static TextUtility of(int integer) {
        return new TextUtility(String.valueOf(integer));
    }

    /**
     * Creates a new utility instance from a double value.
     *
     * <p>
     * The double is converted using {@link String#valueOf(double)} before being wrapped.
     * </p>
     *
     * @param d the double value to wrap
     * @return a new utility instance containing the double as text
     */
    public static TextUtility of(double d) {
        return new TextUtility(String.valueOf(d));
    }

    /**
     * Creates a new utility instance from a long value.
     *
     * <p>
     * The long is converted using {@link String#valueOf(long)} before being wrapped.
     * </p>
     *
     * @param l the long value to wrap
     * @return a new utility instance containing the long as text
     */
    public static TextUtility of(long l) {
        return new TextUtility(String.valueOf(l));
    }

    /**
     * Wraps the current text into multiple lines.
     *
     * <p>
     * The text is split into whitespace-delimited words, then rebuilt line by line. When the next
     * word would exceed the requested line length, a newline is inserted first. The
     * {@code wrapWords} flag controls how the internal line-length tracker behaves after a forced
     * line break.
     * </p>
     *
     * <p>
     * This method does not hyphenate or split long individual words. It wraps only at whitespace
     * boundaries.
     * </p>
     *
     * @param lineLength the preferred maximum line length
     * @param wrapWords  whether the current line length should continue tracking the moved word after a line break
     * @return this utility instance for chaining
     */
    public TextUtility wrap(int lineLength, boolean wrapWords) {
        String[] words = string.split("\\s+");
        StringBuilder wrappedText = new StringBuilder();
        int currentLineLength = 0;

        for (String word : words) {
            if (currentLineLength + word.length() <= lineLength) {
                wrappedText.append(word).append(" ");
                currentLineLength += word.length() + 1;
            } else {
                wrappedText.append("\n").append(word).append(" ");
                currentLineLength = wrapWords ? word.length() + 1 : 0;
            }
        }

        this.string = wrappedText.toString().trim();
        return this;
    }

    /**
     * Prepends {@code "a"} or {@code "an"} to the provided input string.
     *
     * <p>
     * This method does not inspect the current internal text. Instead, it uses the provided
     * argument as the noun phrase and then replaces the internal value with the resulting prefixed
     * text.
     * </p>
     *
     * <p>
     * The decision is based on a simple first-character vowel test and is therefore intentionally
     * lightweight rather than linguistically perfect.
     * </p>
     *
     * @param string the word or phrase to prefix
     * @return this utility instance for chaining
     */
    public TextUtility withPrefix(String string) {
        for (String vowel : VOWELS) {
            if (string.startsWith(vowel)) {
                this.string = "an " + string;
                return this;
            }
        }

        this.string = "a " + string;
        return this;
    }

    /**
     * Uppercases the first character and lowercases the remainder of the text.
     *
     * <p>
     * This is useful for normalizing a sentence-like word or short phrase when the desired result
     * is a single leading uppercase character followed by lowercase characters.
     * </p>
     *
     * @return this utility instance for chaining
     */
    public TextUtility upperFirst() {
        if (string == null || string.isEmpty()) {
            return this;
        }

        this.string = string.substring(0, 1).toUpperCase() + string.substring(1).toLowerCase();
        return this;
    }

    /**
     * Appends a suffix to the current text separated by a single space.
     *
     * <p>
     * This method always inserts one space between the current text and the provided suffix.
     * </p>
     *
     * @param suffix the suffix text to append
     * @return this utility instance for chaining
     */
    public TextUtility withSuffix(String suffix) {
        this.string = string + " " + suffix;
        return this;
    }

    /**
     * Replaces all regex matches in the current text.
     *
     * <p>
     * This method delegates to {@link String#replaceAll(String, String)}, which means
     * {@code target} is treated as a regular expression rather than a literal string.
     * </p>
     *
     * @param target      the regex pattern to replace
     * @param replacement the replacement text
     * @return this utility instance for chaining
     */
    public TextUtility replaceAll(String target, String replacement) {
        this.string = string.replaceAll(target, replacement);
        return this;
    }

    /**
     * Truncates the current text to the requested number of characters.
     *
     * <p>
     * If the text is already short enough, it is left unchanged. This method performs a hard cut
     * and does not append ellipses.
     * </p>
     *
     * @param length the maximum number of characters to keep
     * @return this utility instance for chaining
     * @throws IllegalArgumentException if {@code length} is negative
     */
    public TextUtility truncate(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length cannot be negative");
        }

        this.string = string.length() <= length ? string : string.substring(0, length);
        return this;
    }

    /**
     * Counts whitespace-delimited words in the current text.
     *
     * <p>
     * Blank or null content returns zero. Word splitting is performed using one or more whitespace
     * characters.
     * </p>
     *
     * @return the number of detected words
     */
    public int countWords() {
        if (string == null || string.isBlank()) {
            return 0;
        }

        String[] words = string.trim().split("\\s+");
        return words.length;
    }

    /**
     * Reverses the order of words in the current text.
     *
     * <p>
     * Word boundaries are determined using whitespace splitting. The characters inside each word are
     * preserved; only the word order changes.
     * </p>
     *
     * @return this utility instance for chaining
     */
    public TextUtility reverseWords() {
        String[] words = string.split("\\s+");
        StringBuilder reversed = new StringBuilder();

        for (int i = words.length - 1; i >= 0; i--) {
            reversed.append(words[i]);
            if (i > 0) {
                reversed.append(" ");
            }
        }

        this.string = reversed.toString();
        return this;
    }

    /**
     * Checks whether the current text is a pangram.
     *
     * <p>
     * A pangram is a sentence or phrase that contains every English alphabet letter at least once.
     * Case is ignored during the test.
     * </p>
     *
     * @return true if all letters from {@code a} through {@code z} appear at least once
     */
    public boolean isPangram() {
        String lowercaseText = string.toLowerCase();

        for (char letter = 'a'; letter <= 'z'; letter++) {
            if (lowercaseText.indexOf(letter) == -1) {
                return false;
            }
        }

        return true;
    }

    /**
     * Interprets the current text as a byte count and returns a formatted storage-unit string.
     *
     * <p>
     * The internal text is parsed as a {@code long}. The returned value is formatted using binary
     * unit steps of 1024 for KB, MB, and GB.
     * </p>
     *
     * <p>
     * This method does not replace the internal text. It only returns the formatted result.
     * </p>
     *
     * @return the formatted byte-count string
     * @throws NumberFormatException if the current text is not a valid integer byte count
     */
    public String formatStorageUnits() {
        long bytes = Long.parseLong(this.string);
        final long KILOBYTE = 1024L;
        final long MEGABYTE = KILOBYTE * 1024L;
        final long GIGABYTE = MEGABYTE * 1024L;

        if (bytes >= GIGABYTE) {
            return String.format("%.2fGB", bytes / (double) GIGABYTE);
        } else if (bytes >= MEGABYTE) {
            return String.format("%.2fMB", bytes / (double) MEGABYTE);
        } else if (bytes >= KILOBYTE) {
            return String.format("%.2fKB", bytes / (double) KILOBYTE);
        } else {
            return bytes + "B";
        }
    }

    /**
     * Removes all non-alphanumeric characters except whitespace.
     *
     * <p>
     * Characters outside {@code a-z}, {@code A-Z}, {@code 0-9}, and whitespace are removed.
     * </p>
     *
     * @return this utility instance for chaining
     */
    public TextUtility removeNonAlphaNumeric() {
        this.string = string.replaceAll("[^a-zA-Z0-9\\s]", "");
        return this;
    }

    /**
     * Reverses the characters in the current text.
     *
     * <p>
     * This is a character-level reversal, not a word-order reversal.
     * </p>
     *
     * @return this utility instance for chaining
     */
    public TextUtility reverse() {
        this.string = new StringBuilder(string).reverse().toString();
        return this;
    }

    /**
     * Checks whether the current text reads the same forward and backward.
     *
     * <p>
     * The comparison is case-insensitive. No normalization is applied for spaces or punctuation, so
     * those characters still affect the result.
     * </p>
     *
     * @return true if the text is a case-insensitive palindrome
     */
    public boolean isPalindrome() {
        String reversed = new StringBuilder(string).reverse().toString();
        return string.equalsIgnoreCase(reversed);
    }

    /**
     * Removes simple HTML tags from the current text.
     *
     * <p>
     * This is a regex-based strip operation intended for lightweight cleanup. It is not a full HTML
     * parser and should not be treated as a robust HTML sanitization tool.
     * </p>
     *
     * @return this utility instance for chaining
     */
    public TextUtility stripHtmlTags() {
        this.string = string.replaceAll("<.*?>", "");
        return this;
    }

    /**
     * Inserts spaces between lowercase-to-uppercase camel-case transitions.
     *
     * <p>
     * For example, {@code "playerHealthValue"} becomes {@code "player Health Value"}.
     * </p>
     *
     * @return this utility instance for chaining
     */
    public TextUtility camelCaseToWords() {
        this.string = string.replaceAll("([a-z])([A-Z])", "$1 $2");
        return this;
    }

    /**
     * Counts the number of lines in the current text.
     *
     * <p>
     * Line counting is based on splitting with either Unix or Windows newline separators.
     * </p>
     *
     * @return the number of lines represented by the current text
     */
    public int countLines() {
        return string.split("\\r?\\n").length;
    }

    /**
     * Converts each whitespace-delimited word to title-like casing.
     *
     * <p>
     * The first character of each word is uppercased. The remainder of each word is lowercase.
     * </p>
     *
     * @return this utility instance for chaining
     */
    public TextUtility toTitleCase() {
        String[] words = string.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
            }
            if (i < words.length - 1) {
                result.append(" ");
            }
        }

        this.string = result.toString();
        return this;
    }

    /**
     * Abbreviates the current text to a maximum length using ellipses.
     *
     * <p>
     * If the text already fits, it remains unchanged. For very small maximum lengths of three or
     * less, the result becomes a sequence of dots with that exact length.
     * </p>
     *
     * @param maxLength the maximum allowed result length
     * @return this utility instance for chaining
     * @throws IllegalArgumentException if {@code maxLength} is negative
     */
    public TextUtility abbreviate(int maxLength) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("maxLength cannot be negative");
        }

        if (string.length() <= maxLength) {
            return this;
        }

        if (maxLength <= 3) {
            this.string = ".".repeat(maxLength);
            return this;
        }

        this.string = string.substring(0, maxLength - 3) + "...";
        return this;
    }

    /**
     * Pads the current text with spaces until it reaches the requested length.
     *
     * <p>
     * Padding is applied only when the current text is shorter than the target length.
     * </p>
     *
     * @param length the minimum total length of the resulting text
     * @return this utility instance for chaining
     * @throws IllegalArgumentException if {@code length} is negative
     */
    public TextUtility padToLength(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length cannot be negative");
        }

        if (string.length() < length) {
            string = string.concat(" ".repeat(length - string.length()));
        }

        return this;
    }

    /**
     * Randomly shuffles the characters in the current text.
     *
     * <p>
     * The text is converted into a list of characters, shuffled using
     * {@link Collections#shuffle(List)}, and then rebuilt.
     * </p>
     *
     * @return this utility instance for chaining
     */
    public TextUtility shuffle() {
        char[] chars = string.toCharArray();
        List<Character> characters = new ArrayList<>(chars.length);

        for (char c : chars) {
            characters.add(c);
        }

        Collections.shuffle(characters);

        StringBuilder shuffled = new StringBuilder(characters.size());
        for (Character c : characters) {
            shuffled.append(c);
        }

        this.string = shuffled.toString();
        return this;
    }

    /**
     * Checks whether the current text looks numeric.
     *
     * <p>
     * This method supports optional leading negative signs and an optional decimal portion.
     * It is intended as a lightweight regex-based numeric test rather than a full parser.
     * </p>
     *
     * @return true if the current text matches the numeric pattern used by this class
     */
    public boolean isNumeric() {
        return string.matches("-?\\d+(\\.\\d+)?");
    }

    /**
     * Counts consonant characters in the current text.
     *
     * <p>
     * Vowels, digits, and whitespace are removed first. The remaining characters are counted as
     * consonants by this method's simple rule set.
     * </p>
     *
     * @return the number of consonant characters detected
     */
    public int countConsonants() {
        String consonants = string.replaceAll("[aeiouAEIOU0-9\\s]", "");
        return consonants.length();
    }

    /**
     * Appends one or more additional strings directly to the current text.
     *
     * <p>
     * No separator is inserted automatically. Each provided string is appended exactly as given.
     * </p>
     *
     * @param strings the strings to append in order
     * @return this utility instance for chaining
     */
    public TextUtility concatenate(String... strings) {
        StringBuilder builder = new StringBuilder(this.string);

        for (String value : strings) {
            builder.append(value);
        }

        this.string = builder.toString();
        return this;
    }

    /**
     * Base64-encodes the current text using UTF-8 bytes.
     *
     * <p>
     * The current text is first converted to UTF-8 bytes, encoded with
     * {@link Base64#getEncoder()}, and then stored back as text.
     * </p>
     *
     * @return this utility instance for chaining
     */
    public TextUtility base64Encode() {
        byte[] encodedBytes = Base64.getEncoder().encode(string.getBytes(StandardCharsets.UTF_8));
        this.string = new String(encodedBytes, StandardCharsets.UTF_8);
        return this;
    }

    /**
     * Base64-decodes the current text using UTF-8 bytes.
     *
     * <p>
     * The current text is interpreted as Base64-encoded content, decoded into bytes, and then
     * converted back into a UTF-8 string.
     * </p>
     *
     * @return this utility instance for chaining
     * @throws IllegalArgumentException if the current text is not valid Base64
     */
    public TextUtility base64Decode() {
        byte[] decodedBytes = Base64.getDecoder().decode(string);
        this.string = new String(decodedBytes, StandardCharsets.UTF_8);
        return this;
    }

    /**
     * Replaces the internal text with a new value.
     *
     * <p>
     * This method is useful when you want to reuse the same utility instance with a different
     * source string.
     * </p>
     *
     * @param string the new text value to store
     * @return this utility instance for chaining
     */
    public TextUtility set(String string) {
        this.string = string;
        return this;
    }

    /**
     * Converts the current text into UTF-8 bytes.
     *
     * <p>
     * This is useful when the current transformed text needs to be written to a file, network
     * stream, cache payload, or other binary destination.
     * </p>
     *
     * @return the UTF-8 byte representation of the current text
     */
    public byte[] toBytes() {
        return string.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Returns the current internal text value.
     *
     * <p>
     * This is the main terminal operation when the utility has been used in a fluent chain and the
     * final text is needed.
     * </p>
     *
     * @return the current wrapped text
     */
    @Override
    public String toString() {
        return string;
    }
}