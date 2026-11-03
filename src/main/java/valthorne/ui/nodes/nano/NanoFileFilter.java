package valthorne.ui.nodes.nano;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Named, immutable case-insensitive extension filter. Empty extensions accept all
 * files. Extensions omit the leading dot; compound suffixes such as "tar.gz" work.
 * The first extension is appended to extensionless Save filenames automatically.
 * @param description user-facing dropdown label
 * @param extensions allowed suffixes without dots at either end
 * @author Albert Beaupre
 */
public record NanoFileFilter(String description, List<String> extensions) {
    /**
     * Copies and normalizes extensions while rejecting empty labels and malformed suffixes.
     * @param description nonblank display label
     * @param extensions nonnull suffix list, empty for all files
     */
    public NanoFileFilter {
        if (Objects.requireNonNull(description).isBlank()) throw new IllegalArgumentException("Empty filter label");
        extensions = Objects.requireNonNull(extensions).stream().map(extension -> {
            String value = Objects.requireNonNull(extension).toLowerCase(Locale.ROOT);
            if (value.isBlank() || value.startsWith(".") || value.endsWith(".") || value.contains("/")
                    || value.contains("\\") || value.contains("*") || value.contains("?"))
                throw new IllegalArgumentException("Invalid extension: " + value);
            return value;
        }).distinct().toList();
    }

    /**
     * Matches only the final path component; directories bypass this rule in the browser.
     * @param path candidate file path
     * @return whether any allowed suffix matches, or all files are accepted
     */
    public boolean accepts(Path path) {
        String name = path.getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);
        if (extensions.isEmpty()) return true;
        for (int i = 0; i < extensions.size(); i++) {
            String extension = extensions.get(i);
            int dot = name.length() - extension.length() - 1;
            if (dot >= 0 && name.charAt(dot) == '.' && name.endsWith(extension)) return true;
        }
        return false;
    }
}
