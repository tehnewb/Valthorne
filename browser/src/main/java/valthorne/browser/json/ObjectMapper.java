package valthorne.browser.json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Creates isolated JSON parsers for LDtk data. No reflection, bytecode generation,
 * shared mutable parsing state, or browser double-only number conversion is used.
 */
public final class ObjectMapper {
    /**
     * Creates a stateless parser factory.
     */
    public ObjectMapper() {
    }

    /**
     * Parses a UTF-8 JSON document with a bounded nesting depth.
     * @param bytes encoded document
     * @return root JSON value
     * @throws IOException if syntax, nesting, or scalar encoding is invalid
     */
    public JsonNode readTree(byte[] bytes) throws IOException {
        return new JsonParser(new String(bytes, StandardCharsets.UTF_8)).parse();
    }
}
