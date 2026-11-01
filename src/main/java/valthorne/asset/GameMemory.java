package valthorne.asset;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Owns named runtime resources uploaded or constructed by the application.
 * Unlike {@link Assets}, this store performs no loading or background work.
 * Register each resource once and borrow it through {@link #get(String, Class)}.
 * Retrieval uses an expected constant-time hash lookup directly to the resource,
 * without temporary objects or a cleanup-entry indirection. A separate array-backed
 * list preserves disposal order without linked map entries. Registration checks
 * object identity in linear time to reject owned aliases; register resources during
 * setup rather than in frame loops.
 *
 * <p>Confine this store to the thread and graphics context that own its resources.
 * Destroy UI and other consumers before disposing the store, then release CPU
 * assets and the graphics context. Resources are released in reverse registration
 * order: register textures before particle systems that borrow those textures.
 * Do not separately register resources already owned by another registered object,
 * or register the same owned resource in multiple stores.</p>
 *
 * <pre>{@code
 * GameMemory memory = new GameMemory();
 * memory.put("logo", pixels.asTexture(), Texture::dispose);
 * Image logo = new Image(memory.get("logo", Texture.class));
 * // After removing consumers, while the graphics context is still valid:
 * memory.dispose();
 * }</pre>
 *
 * <p>Ownership transfers only after successful registration. If registration fails,
 * the caller retains responsibility for its value. Disposers must not mutate this
 * store. Disposal attempts every callback even if one fails, and cannot be retried.
 * No synchronization, automatic eviction, reference counting, or shutdown hook
 * is provided.</p>
 */
public final class GameMemory {

    private final HashMap<String, Object> resources = new HashMap<>(); // Direct resource lookup without traversing cleanup entries.
    private final ArrayList<GameMemoryEntry<?>> entries = new ArrayList<>(); // Compact registration order for identity checks and reverse cleanup.
    private boolean disposed; // True before cleanup begins, preventing reentrant access.

    /**
     * Creates an empty resource owner without allocating native resources.
     */
    public GameMemory() {
    }

    /**
     * Takes ownership of a value under a unique, nonblank key.
     * The callback is retained and invoked exactly once when this store is disposed.
     * It must release only resources owned by the supplied value.
     *
     * @param key unique resource name
     * @param value non-null resource whose ownership transfers on success
     * @param disposer non-null cleanup callback executed on the disposing thread
     * @param <T> resource type
     * @return the registered value
     * @throws IllegalStateException if this store has been disposed
     * @throws IllegalArgumentException if the key is blank, already used, or the value is already owned
     * @throws NullPointerException if any argument is null
     */
    public <T> T put(String key, T value, Consumer<? super T> disposer) {
        requireOpen();
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(disposer, "disposer");
        if (key.isBlank()) throw new IllegalArgumentException("Resource key must not be blank");
        if (resources.containsKey(key)) throw new IllegalArgumentException("Resource key already registered: " + key);
        for (int i = 0; i < entries.size(); i++) {
            GameMemoryEntry<?> entry = entries.get(i);
            if (entry.value() == value) throw new IllegalArgumentException("Resource is already owned by this store");
        }
        entries.add(new GameMemoryEntry<>(value, disposer));
        try {
            resources.put(key, value);
        } catch (RuntimeException | Error failure) {
            entries.remove(entries.size() - 1);
            resources.remove(key);
            throw failure;
        }
        return value;
    }

    /**
     * Borrows a resource without transferring ownership or extending its lifetime.
     * The returned reference must not be disposed by the borrower or used after
     * this store is disposed.
     *
     * @param key registered resource name
     * @param type expected resource type
     * @param <T> requested type
     * @return shared registered resource
     * @throws IllegalStateException if this store has been disposed
     * @throws IllegalArgumentException if no resource exists under the key
     * @throws ClassCastException if the value is incompatible with the requested type
     * @throws NullPointerException if either argument is null
     */
    public <T> T get(String key, Class<T> type) {
        requireOpen();
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        Object value = resources.get(key);
        if (value == null) throw new IllegalArgumentException("Unknown resource: " + key);
        return type.cast(value);
    }

    /**
     * Releases resources in reverse registration order and clears all references.
     * Repeated calls do nothing, including reentrant calls from a disposer.
     * If callbacks fail, every remaining callback is still attempted; the first
     * failure is rethrown with later distinct failures suppressed. A failed
     * callback is not retried. Invoke while all required native contexts are valid.
     *
     * @throws RuntimeException if a disposer throws a runtime exception
     * @throws Error if a disposer throws an error before any other failure
     */
    public void dispose() {
        if (disposed) return;
        disposed = true;
        Throwable failure = null;
        try {
            for (int i = entries.size() - 1; i >= 0; i--) {
                try {
                    entries.get(i).dispose();
                } catch (RuntimeException | Error next) {
                    if (failure == null) failure = next;
                    else if (failure != next) failure.addSuppressed(next);
                }
            }
        } finally {
            entries.clear();
            resources.clear();
        }
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
    }

    /**
     * Rejects operations after disposal begins, including calls from disposers.
     *
     * @throws IllegalStateException if cleanup has begun
     */
    private void requireOpen() {
        if (disposed) throw new IllegalStateException("GameMemory has been disposed");
    }
}
