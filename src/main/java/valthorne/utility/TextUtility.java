package valthorne.utility;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * The Text class provides utility methods for manipulating text strings.
 *
 * @author Albert Beaupre
 * @version 1.0
 * @since May 1st, 2024
 */
public class TextUtility {
    public static final String[] VOWELS = {"a", "e", "i", "o", "u", "A", "E", "I", "O", "U"};

    private String string;

    /**
     * Constructs a Text object with the given string.
     *
     * @param string The input text string.
     */
    public TextUtility(String string) {
        this.string = string;
    }

    /**
     * Constructs a Text object with the given string.
     *
     * @param string The input text string.
     */
    public static TextUtility of(String string) {
        return new TextUtility(string);
    }

    /**
     * Constructs a Text object converting the given byte to a string.
     *
     * @param b The byte to convert.
     */
    public static TextUtility of(byte b) {
        return new TextUtility(String.valueOf(b));
    }

    /**
     * Constructs a Text object converting the given character to a string.
     *
     * @param c The character to convert.
     */
    public static TextUtility of(char c) {
        return new TextUtility(String.valueOf(c));
    }

    /**
     * Constructs a Text object converting the given boolean to a string.
     *
     * @param b The boolean to convert.
     */
    public static TextUtility of(boolean b) {
        return new TextUtility(String.valueOf(b));
    }

    /**
     * Constructs a Text object converting the given float to a string.
     *
     * @param f The float to convert.
     */
    public static TextUtility of(float f) {
        return new TextUtility(String.valueOf(f));
    }

    /**
     * Constructs a Text object converting the given short to a string.
     *
     * @param s The short to convert.
     */
    public static TextUtility of(short s) {
        return new TextUtility(String.valueOf(s));
    }

    /**
     * Constructs a Text object converting the given integer to a string.
     *
     * @param integer The integer to convert.
     */
    public static TextUtility of(int integer) {
        return new TextUtility(String.valueOf(integer));
    }

    /**
     * Constructs a Text object converting the given double to a string.
     *
     * @param d The double to convert.
     */
    public static TextUtility of(double d) {
        return new TextUtility(String.valueOf(d));
    }

    /**
     * Constructs a Text object converting the given long to a string.
     *
     * @param l The long to convert.
     */
    public static TextUtility of(long l) {
        return new TextUtility(String.valueOf(l));
    }


    /**
     * Wraps the text to fit within the specified line length.
     *
     * @param lineLength The maximum line length.
     * @param wrapWords  If true, words will be wrapped to the next line if they exceed the line length.
     *                   If false, the text will be broken at the line length boundary regardless of word boundaries.
     * @return The modified Text object with wrapped text.
     */
    public TextUtility wrap(int lineLength, boolean wrapWords) {
        String[] words = string.split("\\s+");
        StringBuilder wrappedText = new StringBuilder();
        int currentLineLength = 0;

        for (String word : words) {
            if (currentLineLength + word.length() <= lineLength) {
                wrappedText.append(word).append(" ");
                currentLineLength += word.length() + 1; // +1 for the space
            } else {
                if (wrapWords) {
                    wrappedText.append("\n").append(word).append(" ");
                    currentLineLength = word.length() + 1; // +1 for the space
                } else {
                    wrappedText.append("\n").append(word).append(" ");
                    currentLineLength = 0;
                }
            }
        }
        return new TextUtility(wrappedText.toString().trim());
    }

    /**
     * Adds a prefix "a" or "an" to the text based on the presence of vowels.
     *
     * @param string The prefix string to be added.
     * @return The modified Text object.
     */
    public TextUtility withPrefix(String string) {
        for (String vowel : VOWELS) {
            if (string.startsWith(vowel)) {
                return new TextUtility("an " + string);
            }
        }
        return new TextUtility("a " + string);
    }

    /**
     * Capitalizes the first letter of the text and converts the rest to lowercase.
     *
     * @return The modified Text object.
     */
    public TextUtility upperFirst() {
        return new TextUtility(string.substring(0, 1).toUpperCase() + string.substring(1).toLowerCase());
    }

    /**
     * Appends a suffix to the text.
     *
     * @param suffix The suffix string to be added.
     * @return The modified Text object.
     */
    public TextUtility withSuffix(String suffix) {
        return new TextUtility(string + " " + suffix);
    }

    /**
     * Replaces all occurrences of a substring with another string.
     *
     * @param target      The target substring to be replaced.
     * @param replacement The replacement string.
     * @return The modified Text object.
     */
    public TextUtility replaceAll(String target, String replacement) {
        return new TextUtility(string.replaceAll(target, replacement));
    }

    /**
     * Truncates the text to a specified length.
     *
     * @param length The maximum length of the text.
     * @return The modified Text object.
     */
    public TextUtility truncate(int length) {
        return new TextUtility(string.substring(0, length));
    }

    /**
     * Counts the number of words in the text.
     *
     * @return The number of words in the text.
     */
    public int countWords() {
        String[] words = string.split("\\s+");
        return words.length;
    }

    /**
     * Reverses the order of words in the text.
     *
     * @return The modified Text object with reversed words.
     */
    public TextUtility reverseWords() {
        String[] words = string.split("\\s+");
        StringBuilder reversed = new StringBuilder();
        for (int i = words.length - 1; i >= 0; i--) {
            reversed.append(words[i]).append(" ");
        }
        return new TextUtility(reversed.toString().trim());
    }

    /**
     * Checks if the text is a pangram (contains every letter of the alphabet at least once).
     *
     * @return true if the text is a pangram, otherwise false.
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
     * Formats the internal string to be represented as a storage unit.
     * <p>
     * For example, 1024 bytes is equal to 1 kilobyte, so it would look like: "1KB"
     * </p>
     *
     * @return the formatted string.
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
     * Removes all non-alphanumeric characters from the text.
     *
     * @return The modified Text object with non-alphanumeric characters removed.
     */
    public TextUtility removeNonAlphaNumeric() {
        return new TextUtility(string.replaceAll("[^a-zA-Z0-9\\s]", ""));
    }

    /**
     * Reverses the characters in the text.
     *
     * @return The modified Text object with reversed characters.
     */
    public TextUtility reverse() {
        return new TextUtility(new StringBuilder(string).reverse().toString());
    }

    /**
     * Checks if the text is a palindrome (reads the same forwards and backwards).
     *
     * @return true if the text is a palindrome, otherwise false.
     */
    public boolean isPalindrome() {
        String reversed = new StringBuilder(string).reverse().toString();
        return string.equalsIgnoreCase(reversed);
    }

    /**
     * Removes HTML tags from the text.
     *
     * @return The modified Text object with HTML tags removed.
     */
    public TextUtility stripHtmlTags() {
        return new TextUtility(string.replaceAll("\\<.*?\\>", ""));
    }

    /**
     * Converts a camelCase or PascalCase string to separate words.
     *
     * @return The modified Text object with words separated.
     */
    public TextUtility camelCaseToWords() {
        return new TextUtility(string.replaceAll("([a-z])([A-Z])", "$1 $2"));
    }

    /**
     * Counts the number of lines in the text.
     *
     * @return The number of lines in the text.
     */
    public int countLines() {
        return string.split("\\r?\\n").length;
    }

    /**
     * Converts the text to title case.
     *
     * @return The modified Text object in title case.
     */
    public TextUtility toTitleCase() {
        String[] words = string.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            result.append(word.substring(0, 1).toUpperCase()).append(word.substring(1)).append(" ");
        }
        return new TextUtility(result.toString().trim());
    }

    /**
     * Abbreviates the text to a specified maximum length, adding an ellipsis if truncated.
     *
     * @param maxLength The maximum length of the text.
     * @return The modified Text object.
     */
    public TextUtility abbreviate(int maxLength) {
        return new TextUtility(string.substring(0, maxLength - 3) + "...");
    }

    /**
     * Pads the text to the specified length with whitespace if it is shorter.
     *
     * @param length The target length to pad the text to.
     * @return The modified Text object with padded text.
     */
    public TextUtility padToLength(int length) {
        String whitespace = " ";
        if (string.length() < length)
            string = string.concat(whitespace.repeat(length - string.length()));
        return new TextUtility(string);
    }

    /**
     * Shuffles the characters within the text randomly.
     *
     * @return The modified Text object with shuffled characters.
     */
    public TextUtility shuffle() {
        char[] chars = string.toCharArray();
        List<Character> charList = new ArrayList<>();
        for (char c : chars) {
            charList.add(c);
        }
        Collections.shuffle(charList);
        StringBuilder shuffled = new StringBuilder();
        for (Character c : charList) {
            shuffled.append(c);
        }
        return new TextUtility(shuffled.toString());
    }

    /**
     * Checks if the text represents a numeric value.
     *
     * @return true if the text is numeric, otherwise false.
     */
    public boolean isNumeric() {
        return string.matches("-?\\d+(\\.\\d+)?");
    }

    /**
     * Counts the number of consonants in the text (non-vowel, non-digit characters).
     *
     * @return The count of consonants.
     */
    public int countConsonants() {
        String consonants = string.replaceAll("[aeiouAEIOU0-9\\s]", "");
        return consonants.length();
    }

    /**
     * Concatenates multiple strings to the end of the current text.
     *
     * @param strings The strings to concatenate.
     * @return The modified Text object with concatenated text.
     */
    public TextUtility concatenate(String... strings) {
        StringBuilder builder = new StringBuilder();
        for (String str : strings) {
            builder.append(str);
        }
        return new TextUtility(builder.toString());
    }

    /**
     * Base64 encodes the text using the default character encoding (UTF-8).
     *
     * @return The modified Text object with Base64-encoded text.
     */
    public TextUtility base64Encode() {
        byte[] encodedBytes = Base64.getEncoder().encode(string.getBytes());
        return new TextUtility(new String(encodedBytes, StandardCharsets.UTF_8));
    }

    /**
     * Base64 decodes the text to its original form using the default character encoding (UTF-8).
     *
     * @return The modified Text object with Base64-decoded text.
     */
    public TextUtility base64Decode() {
        byte[] decodedBytes = Base64.getDecoder().decode(string);
        return new TextUtility(new String(decodedBytes, StandardCharsets.UTF_8));
    }

    /**
     * Sets the string of this Text to the specified string.
     *
     * @param string The string to set.
     * @return This instance for chaining.
     */
    public TextUtility set(String string) {
        this.string = string;
        return this;
    }

    /**
     * Retrieves the byte array tied to the internal string.
     *
     * @return The byte array.
     */
    public byte[] toBytes() {
        return string.getBytes();
    }

    /**
     * Gets the current text string.
     *
     * @return The current text string.
     */
    @Override
    public String toString() {
        return string;
    }
}