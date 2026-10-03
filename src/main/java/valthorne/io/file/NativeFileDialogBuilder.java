package valthorne.io.file;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Mutable fluent configuration for native Open, Save, and folder dialogs.
 * Obtain a builder through {@link NativeFileDialog#builder()}. Each build creates
 * an independent configuration; later builder changes do not affect earlier dialogs.
 * Configuration performs no filesystem access and does not load native libraries.
 * Builders are confined to their caller's thread.
 */
public final class NativeFileDialogBuilder {
    private String title = "Choose a file"; // Caption shared by the configured dialog operations.
    private String defaultPath; // Initial location or filename; null uses the OS default.
    private String filterDescription; // Optional label for the combined wildcard filter.
    private String[] filterPatterns = new String[0]; // Owned validated wildcard patterns; empty allows all files.

    /**
     * Creates the default configuration without initializing the native backend.
     */
    NativeFileDialogBuilder() {
    }

    /**
     * Sets the window caption. Empty captions are allowed.
     *
     * @param title non-null caption without embedded NUL characters
     * @return this builder
     * @throws NullPointerException if title is null
     * @throws IllegalArgumentException if title contains a NUL character
     */
    public NativeFileDialogBuilder title(String title) {
        this.title = validateText(title);
        return this;
    }

    /**
     * Sets an initial directory or suggested file path. Supply a directory for
     * folder selection, or a directory plus filename for Save. Relative paths
     * are made absolute without requiring the target to exist.
     *
     * @param path initial path, or null to restore the OS default
     * @return this builder
     */
    public NativeFileDialogBuilder defaultPath(Path path) {
        defaultPath = path == null ? null : validateText(path.toAbsolutePath().toString());
        return this;
    }

    /**
     * Replaces the combined file filter, for example {@code filter("Images", "*.png", "*.jpg")}.
     * Zero patterns clears filtering. Folder selection ignores file filters.
     * Patterns are copied; callers may subsequently modify their array.
     *
     * @param description optional filter label; null uses the backend default
     * @param patterns non-null array of non-null, nonempty wildcard patterns
     * @return this builder
     * @throws NullPointerException if the array or a pattern is null
     * @throws IllegalArgumentException if a pattern is empty or any text contains NUL
     */
    public NativeFileDialogBuilder filter(String description, String... patterns) {
        String label = description == null ? null : validateText(description);
        String[] copy = Objects.requireNonNull(patterns, "patterns").clone();
        for (String pattern : copy) {
            if (validateText(pattern).isEmpty())
                throw new IllegalArgumentException("Filter patterns must not be empty.");
        }
        filterDescription = label;
        filterPatterns = copy;
        return this;
    }

    /**
     * Captures the current settings without displaying a dialog or loading native code.
     *
     * @return independent reusable dialog configuration
     */
    public NativeFileDialog build() {
        return new NativeFileDialog(title, defaultPath, filterDescription, filterPatterns.clone());
    }

    /**
     * Validates text before passing it to a NUL-terminated native API.
     *
     * @param text non-null native argument
     * @return the unchanged validated text
     * @throws NullPointerException if text is null
     * @throws IllegalArgumentException if text contains NUL
     */
    private String validateText(String text) {
        Objects.requireNonNull(text, "text");
        if (text.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Dialog text must not contain NUL characters.");
        return text;
    }
}
