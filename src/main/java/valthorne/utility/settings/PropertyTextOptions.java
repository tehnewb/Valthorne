package valthorne.utility.settings;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Optional comments emitted with a human-readable property set. Headers appear as
 * standalone comment lines before the properties, while entry comments appear at the
 * end of the matching property line. Property output remains sorted by name regardless
 * of the map implementation used for comments.
 *
 * @param headers  standalone header comments, without a leading comment marker
 * @param comments property names mapped to end-of-line comments
 * @author Albert Beaupre
 * @since September 21st, 2026
 */
public record PropertyTextOptions(List<String> headers, Map<String, String> comments) {

    private static final PropertyTextOptions NONE = new PropertyTextOptions(List.of(), Map.of());

    /**
     * Creates immutable text options.
     *
     * @throws NullPointerException     if a collection, key, or comment is null
     * @throws IllegalArgumentException if a comment contains a line break
     */
    public PropertyTextOptions {
        headers = List.copyOf(Objects.requireNonNull(headers, "headers"));
        comments = Map.copyOf(Objects.requireNonNull(comments, "comments"));
        headers.forEach(PropertyTextOptions::validateComment);
        comments.forEach((name, comment) -> {
            Objects.requireNonNull(name, "comment property name");
            validateComment(comment);
        });
    }

    /**
     * Returns options that emit no comments.
     *
     * @return shared empty options
     */
    public static PropertyTextOptions none() {
        return NONE;
    }

    /**
     * Creates options containing only standalone header comments.
     *
     * @param headers header comments
     * @return immutable text options
     */
    public static PropertyTextOptions withHeaders(String... headers) {
        return new PropertyTextOptions(List.of(headers), Map.of());
    }

    private static void validateComment(String comment) {
        Objects.requireNonNull(comment, "comment");
        if (comment.indexOf('\n') >= 0 || comment.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Comments must occupy a single line");
        }
    }
}
