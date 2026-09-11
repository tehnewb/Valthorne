package valthorne.event.events;

import valthorne.event.Event;
import valthorne.event.EventTypes;

/**
 * Carries committed text on the {@link EventTypes#TEXT_INPUT} route.
 * The keyboard's native character callback creates a string from each received
 * Unicode code point; callers may also supply a multi-character string. Text
 * insertion is separate from physical key commands such as navigation or shortcuts.
 *
 * <p>The payload is retained unchanged: empty strings are allowed, and this
 * class performs no normalization or Unicode validation. The string is immutable,
 * while inherited event-consumption state remains mutable and dispatch-local.
 * Do not publish the same event instance concurrently.</p>
 *
 * @author Albert Beaupre
 */
public final class TextInputEvent extends Event {
    private final String text; // Committed text retained unchanged, including an allowed empty string.

    /**
     * Creates a text-input event without dispatching it. The supplied immutable
     * string is retained by reference and may contain multiple code points.
     *
     * @param text the committed text to deliver
     * @throws NullPointerException if text is null
     */
    public TextInputEvent(String text) {
        super(EventTypes.TEXT_INPUT);
        this.text = java.util.Objects.requireNonNull(text);
    }

    /**
     * Returns the original text payload without trimming or normalization.
     * Its length counts UTF-16 code units and may differ from its code-point count.
     *
     * @return the nonnull committed string supplied at construction
     */
    public String getText() {return text;}
}
