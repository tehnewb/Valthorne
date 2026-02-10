package valthorne.io.pool;

import valthorne.collections.stack.FastStack;

import java.util.function.Supplier;


/**
 * A generic pool implementation for managing reusable objects to minimize object creation and improve performance.
 * Objects stored in this pool must implement the {@code Poolable} interface to support resetting their state
 * before being reused.
 *
 * @param <T> the type of objects managed by this pool
 * @author Albert Beaupre
 * @since December 9th, 2025
 */
public class Pool<T> {

    private final FastStack<T> pool;
    private final Supplier<T> factory;
    private final int maxSize;

    /**
     * Creates a pool with a factory and max size.
     *
     * @param factory Supplier to create new objects.
     * @param maxSize Maximum number of objects in the pool.
     */
    public Pool(Supplier<T> factory, int maxSize) {
        this.pool = new FastStack<>();
        this.factory = factory;
        this.maxSize = maxSize;
    }

    /**
     * Initializes the pool by pre-creating and adding a specified number of objects to the pool.
     *
     * @param count the number of objects to initialize and add to the pool
     */
    public void initialize(int count) {
        for (int i = 0; i < count; i++) {
            pool.push(factory.get());
        }
    }

    /**
     * Get an object from the pool or create a new one if empty.
     *
     * @return A pooled object.
     */
    public T obtain() {
        T obj = pool.pop();
        if (obj == null) {
            obj = factory.get();
        }
        return obj;
    }

    /**
     * Return an object to the pool.
     *
     * @param obj Object to return.
     */
    public void free(T obj) {
        if (obj == null) return;

        if (obj instanceof Poolable poolable)
            poolable.reset();

        if (pool.size() < maxSize)
            pool.push(obj);
    }

    /**
     * Clear all objects from the pool.
     */
    public void clear() {
        pool.clear();
    }

    /**
     * Get the current size of the pool.
     *
     * @return Number of objects currently in the pool.
     */
    public int size() {
        return pool.size();
    }
}