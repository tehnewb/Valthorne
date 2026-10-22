package valthorne.ui.nodes.nano;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * One application-owned Open In target offered for a compatible selected file.
 * Predicates run when the menu opens; handlers run on the owning UI thread.
 *
 * @param name displayed destination name
 * @param accepts predicate recognizing supported paths
 * @param open application callback receiving one selected file
 */
record NanoFileOpenTarget(String name, Predicate<Path> accepts, Consumer<Path> open) {

    /**
     * Validates a registration without running its predicate or handler.
     *
     * @param name nonblank destination label
     * @param accepts nonnull compatibility predicate
     * @param open nonnull UI-thread handler
     */
    NanoFileOpenTarget {
        if (Objects.requireNonNull(name).isBlank()) throw new IllegalArgumentException("An application name is required.");
        Objects.requireNonNull(accepts);
        Objects.requireNonNull(open);
    }
}
