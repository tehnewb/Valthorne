package valthorne.browser.json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Strict, allocation-bounded recursive JSON reader used only during asset loading.
 * Integral tokens preserve Java long precision. A depth limit prevents malicious
 * documents from exhausting the coroutine stack; diagnostics include UTF-16 offsets.
 */
final class JsonParser {
    private final String source; // Immutable decoded document, released after parsing.
    private int position; // Next UTF-16 code unit to consume.

    /**
     * Owns one source string for a single parse.
     * @param source JSON text
     */
    JsonParser(String source) {
        this.source = source;
        if (!source.isEmpty() && source.charAt(0) == '\ufeff') position = 1;
    }

    /**
     * Parses one value and rejects trailing non-whitespace data.
     * @return root value
     * @throws IOException on malformed input
     */
    JsonNode parse() throws IOException {
        JsonNode result = value(0);
        whitespace();
        if (position != source.length()) throw failure("Trailing JSON data");
        return result;
    }

    /**
     * Decodes a scalar or container with a bounded recursion depth.
     * @param depth active container depth
     * @return decoded node
     * @throws IOException on malformed or excessively nested input
     */
    private JsonNode value(int depth) throws IOException {
        if (depth > 256) throw failure("JSON nesting exceeds 256 levels");
        whitespace();
        if (position == source.length()) throw failure("Missing JSON value");
        char next = source.charAt(position);
        if (next == '"') return new JsonNode(string());
        if (next == '{') {
            position++;
            LinkedHashMap<String, JsonNode> object = new LinkedHashMap<>();
            whitespace();
            if (take('}')) return new JsonNode(object);
            do {
                whitespace();
                if (position == source.length() || source.charAt(position) != '"') throw failure("Expected JSON property name");
                String key = string();
                whitespace();
                if (!take(':')) throw failure("Expected colon after JSON property");
                object.put(key, value(depth + 1));
                whitespace();
                if (take('}')) return new JsonNode(object);
            } while (take(','));
            throw failure("Expected comma or object end");
        }
        if (next == '[') {
            position++;
            ArrayList<JsonNode> array = new ArrayList<>();
            whitespace();
            if (take(']')) return new JsonNode(array);
            do {
                array.add(value(depth + 1));
                whitespace();
                if (take(']')) return new JsonNode(array);
            } while (take(','));
            throw failure("Expected comma or array end");
        }
        if (next == 't') return literal("true", Boolean.TRUE);
        if (next == 'f') return literal("false", Boolean.FALSE);
        if (next == 'n') return literal("null", null);
        return number();
    }

    /**
     * Consumes an exact JSON keyword.
     * @param token required spelling
     * @param decoded scalar result
     * @return decoded scalar node
     * @throws IOException on an invalid keyword
     */
    private JsonNode literal(String token, Object decoded) throws IOException {
        if (!source.startsWith(token, position)) throw failure("Invalid JSON literal");
        position += token.length();
        return new JsonNode(decoded);
    }

    /**
     * Decodes escapes while preserving UTF-16 surrogate pairs exactly.
     * @return string contents without quotes
     * @throws IOException on unescaped control data or incomplete escapes
     */
    private String string() throws IOException {
        position++;
        StringBuilder text = new StringBuilder();
        while (position < source.length()) {
            char ch = source.charAt(position++);
            if (ch == '"') return text.toString();
            if (ch < 32) throw failure("Unescaped control character in JSON string");
            if (ch != '\\') {
                text.append(ch);
                continue;
            }
            if (position == source.length()) throw failure("Incomplete JSON escape");
            char escaped = source.charAt(position++);
            switch (escaped) {
                case '"', '\\', '/' -> text.append(escaped);
                case 'b' -> text.append('\b');
                case 'f' -> text.append('\f');
                case 'n' -> text.append('\n');
                case 'r' -> text.append('\r');
                case 't' -> text.append('\t');
                case 'u' -> {
                    if (source.length() - position < 4) throw failure("Incomplete Unicode escape");
                    int code = 0;
                    for (int i = 0; i < 4; i++) {
                        char hex = source.charAt(position++);
                        int digit = hex >= '0' && hex <= '9' ? hex - '0' : hex >= 'a' && hex <= 'f' ? hex - 'a' + 10 : hex >= 'A' && hex <= 'F' ? hex - 'A' + 10 : -1;
                        if (digit < 0) throw failure("Invalid Unicode escape");
                        code = code * 16 + digit;
                    }
                    text.append((char) code);
                }
                default -> throw failure("Invalid JSON escape");
            }
        }
        throw failure("Unterminated JSON string");
    }

    /**
     * Parses JSON number grammar before selecting exact long or double storage.
     * @return numeric node
     * @throws IOException on invalid grammar or non-finite values
     */
    private JsonNode number() throws IOException {
        int start = position;
        take('-');
        if (!take('0')) digits();
        boolean integral = true;
        if (take('.')) {
            integral = false;
            digits();
        }
        if (take('e') || take('E')) {
            integral = false;
            if (!take('+')) take('-');
            digits();
        }
        String token = source.substring(start, position);
        if (integral) {
            try {
                return new JsonNode(Long.valueOf(token));
            } catch (NumberFormatException outOfRange) {
                return new JsonNode(decimal(token));
            }
        }
        return new JsonNode(decimal(token));
    }

    /**
     * Parses a finite numeric token after grammar validation.
     * @param token validated numeric spelling
     * @return finite double value
     * @throws IOException if the value exceeds the browser's finite number range
     */
    private double decimal(String token) throws IOException {
        try {
            double decoded = Double.parseDouble(token);
            if (!Double.isFinite(decoded)) throw failure("JSON number exceeds finite range");
            return decoded;
        } catch (NumberFormatException invalid) {
            throw new IOException("Invalid JSON number at UTF-16 offset " + position, invalid);
        }
    }

    /**
     * Consumes at least one decimal digit.
     * @throws IOException if a required digit is missing
     */
    private void digits() throws IOException {
        int start = position;
        while (position < source.length() && source.charAt(position) >= '0' && source.charAt(position) <= '9') position++;
        if (start == position) throw failure("Expected decimal digit");
    }

    /**
     * Consumes one optional punctuation code unit.
     * @param expected code unit to match
     * @return whether it was consumed
     */
    private boolean take(char expected) {
        if (position == source.length() || source.charAt(position) != expected) return false;
        position++;
        return true;
    }

    /**
     * Skips only the four whitespace characters permitted by JSON.
     */
    private void whitespace() {
        while (position < source.length()) {
            char ch = source.charAt(position);
            if (ch != ' ' && ch != '\t' && ch != '\n' && ch != '\r') return;
            position++;
        }
    }

    /**
     * Creates a checked syntax diagnostic with the current document offset.
     * @param message diagnostic description
     * @return parser failure
     */
    private IOException failure(String message) {
        return new IOException(message + " at UTF-16 offset " + position);
    }
}
