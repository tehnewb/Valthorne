package valthorne.io.pool;

import valthorne.collections.stack.FastStack;

import java.util.function.Supplier;


/**
 * <p>
 * {@code Pool} is a generic reusable object pool designed to reduce repeated
 * allocations by recycling objects instead of constantly creating and discarding them.
 * This is especially useful in performance-sensitive systems such as rendering,
 * particle effects, physics, UI events, temporary math objects, and other high-frequency
 * runtime workflows where many short-lived objects may otherwise create avoidable
 * garbage collection pressure.
 * </p>
 *
 * <p>
 * The pool stores instances in an internal {@link FastStack}. When an object is
 * requested through {@link #obtain()}, the pool attempts to return an already existing
 * object from the stack. If none are available, a new instance is created through the
 * provided {@link Supplier} factory.
 * </p>
 *
 * <p>
 * When an object is returned through {@link #free(Object)}, the pool optionally resets
 * it if it implements {@link Poolable}, then places it back into the stack as long as
 * the configured maximum pool size has not been reached.
 * </p>
 *
 * <p>
 * This class does not require pooled objects to implement {@link Poolable}, but if they
 * do, the reset step allows them to be safely reused in a clean state. That makes this
 * pool flexible enough to manage both resettable and non-resettable object types.
 * </p>
 *
 * <p>
 * Typical usage involves:
 * </p>
 *
 * <ul>
 *     <li>creating the pool with a factory and a max size</li>
 *     <li>optionally preloading objects with {@link #initialize(int)}</li>
 *     <li>getting objects with {@link #obtain()}</li>
 *     <li>returning objects with {@link #free(Object)}</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Pool<MyParticle> particlePool = new Pool<>(MyParticle::new, 256);
 * particlePool.initialize(32);
 *
 * MyParticle particle = particlePool.obtain();
 * particle.setPosition(100, 200);
 * particle.setVelocity(1.5f, -0.5f);
 *
 * // Use the particle...
 *
 * particlePool.free(particle);
 *
 * int pooledCount = particlePool.size();
 *
 * particlePool.clear();
 * }</pre>
 *
 * <p>
 * This example demonstrates the full intended use of the class: construction,
 * pre-initialization, obtaining an object, using it, returning it, inspecting
 * the current pool size, and clearing the pool.
 * </p>
 *
 * @param <T> the type of objects managed by this pool
 * @author Albert Beaupre
 * @since December 9th, 2025
 */
public class Pool<T> {

    private final FastStack<T> pool; // Internal stack storing currently available pooled objects
    private final Supplier<T> factory; // Factory used to create new objects when the pool is empty
    private final int maxSize; // Maximum number of objects allowed to be retained in the pool

    /**
     * <p>
     * Creates a new object pool with the provided factory and maximum retention size.
     * </p>
     *
     * <p>
     * The factory is used whenever {@link #obtain()} is called and the pool has no
     * currently available objects. The max size controls how many returned objects
     * may be stored for future reuse. If more objects are freed after the pool has
     * reached its maximum size, those extra objects are simply not stored.
     * </p>
     *
     * @param factory supplier used to create new objects when needed
     * @param maxSize maximum number of objects the pool may retain
     */
    public Pool(Supplier<T> factory, int maxSize) {
        this.pool = new FastStack<>();
        this.factory = factory;
        this.maxSize = maxSize;
    }

    /**
     * <p>
     * Pre-populates the pool by creating and storing a specified number of objects.
     * </p>
     *
     * <p>
     * This is useful when you want to front-load object creation during initialization
     * rather than allowing the pool to grow lazily during runtime. Each created object
     * is produced through the configured factory and pushed into the internal stack.
     * </p>
     *
     * <p>
     * This method does not enforce {@code maxSize} directly, so callers should pass
     * values that make sense for their intended pool configuration.
     * </p>
     *
     * @param count the number of objects to create and add to the pool
     */
    public void initialize(int count) {
        for (int i = 0; i < count; i++) {
            pool.push(factory.get());
        }
    }

    /**
     * <p>
     * Obtains an object from the pool.
     * </p>
     *
     * <p>
     * If the pool contains an available object, that object is popped from the internal
     * stack and returned. If the pool is empty, a new object is created through the
     * configured factory and returned instead.
     * </p>
     *
     * <p>
     * This method never resets the object on obtain. Resetting happens when an object
     * is returned through {@link #free(Object)} if it implements {@link Poolable}.
     * </p>
     *
     * @return a pooled object, either reused or newly created
     */
    public T obtain() {
        T obj = pool.pop();
        if (obj == null) {
            obj = factory.get();
        }
        return obj;
    }

    /**
     * <p>
     * Returns an object to the pool for future reuse.
     * </p>
     *
     * <p>
     * If the provided object is {@code null}, the method returns immediately.
     * If the object implements {@link Poolable}, its {@link Poolable#reset()} method
     * is called before the object is stored. The object is only pushed back into the
     * pool if the current pool size is still below the configured maximum size.
     * </p>
     *
     * <p>
     * This behavior prevents the pool from growing without limit while still giving
     * resettable objects a clean state before reuse.
     * </p>
     *
     * @param obj the object to return to the pool
     */
    public void free(T obj) {
        if (obj == null) return;

        if (obj instanceof Poolable poolable) poolable.reset();

        if (pool.size() < maxSize) pool.push(obj);
    }

    /**
     * <p>
     * Removes all currently stored objects from the pool.
     * </p>
     *
     * <p>
     * This only clears the pool's internal storage. It does not dispose objects or
     * perform any other cleanup beyond removing all references from the stack.
     * </p>
     */
    public void clear() {
        pool.clear();
    }

    /**
     * <p>
     * Returns the current number of available objects stored in the pool.
     * </p>
     *
     * <p>
     * This value reflects how many objects can currently be reused without creating
     * new ones through the factory.
     * </p>
     *
     * @return the number of objects currently stored in the pool
     */
    public int size() {
        return pool.size();
    }
}