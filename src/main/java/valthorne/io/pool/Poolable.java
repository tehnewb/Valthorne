package valthorne.io.pool;


/**
 * Represents an interface for objects that can be pooled and reused.
 * Implementations of this interface must provide a mechanism to reset
 * their internal state to ensure they are ready for reuse when returned
 * to a pool.
 * <p>
 * Objects that implement this interface are typically used in conjunction
 * with object pooling mechanisms, such as the {@code Pool} class, to
 * reduce the overhead of object creation and garbage collection in
 * performance-critical applications.
 * <p>
 * The {@link #reset()} method is invoked when an object is returned
 * to a pool to reset its internal state and prepare it for subsequent use.
 *
 * @author Albert Beaupre
 * @since December 9th, 2025
 */
public interface Poolable {

    /**
     * Reset the object to its initial state for reuse.
     */
    void reset();
}