package valthorne.ui.nodes;

import java.util.Objects;

/**
 * Numeric spinner's centered text field, applying its pending value on focus loss.
 * The callback runs after normal caret and selection cleanup and delegates all
 * numeric validation, clamping, and notifications to the owning spinner. Ordinary
 * text fields retain their existing explicit submission behavior.
 */
final class SpinnerTextField extends TextField {

    private final Runnable commit; // Owning spinner's synchronous submission operation.

    /**
     * Creates an empty field with a mandatory focus-loss submission callback.
     *
     * @param commit operation that validates and commits pending numeric text
     */
    SpinnerTextField(Runnable commit) {
        this.commit = Objects.requireNonNull(commit);
        centered(true);
    }

    @Override
    public void setFocused(boolean focused) {
        boolean submit = isFocused() && !focused;
        super.setFocused(focused);
        if (submit) commit.run();
    }
}
