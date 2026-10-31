package valthorne.asset;

import java.util.function.Consumer;

/**
 * Couples one owned resource with its type-safe cleanup callback, avoiding
 * reflection or unchecked casts when heterogeneous resources are released.
 *
 * @param <T> owned resource type
 */
final class GameMemoryEntry<T> {

    private final T value; // Owned resource retained until the store releases this entry.
    private final Consumer<? super T> disposer; // Explicit callback matching the resource type.

    /**
     * Captures a resource and callback validated by the owning store.
     *
     * @param value owned resource
     * @param disposer cleanup callback
     */
    GameMemoryEntry(T value, Consumer<? super T> disposer) {
        this.value = value;
        this.disposer = disposer;
    }

    /**
     * Returns the resource without transferring ownership.
     *
     * @return stored resource
     */
    T value() {
        return value;
    }

    /**
     * Invokes the registered cleanup callback on the calling thread.
     */
    void dispose() {
        disposer.accept(value);
    }
}
