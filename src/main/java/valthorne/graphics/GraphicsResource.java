package valthorne.graphics;

/**
 * Participates in graphics-device replacement without changing application state.
 * Registered resources release in reverse creation order and restore in creation
 * order, on the rendering thread between frames. Release must be idempotent and
 * retain CPU configuration; restore must clean up partially allocated objects if
 * it fails. Normal disposal must unregister the resource.
 *
 * <p>Render targets are recreated empty and GPU-generated data must be regenerated.
 * Implementations may reject a switch while a drawing/capture scope is open.
 * Native handles borrowed by application code must be fetched again afterward.</p>
 */
public interface GraphicsResource {
    /**
     * Rejects an unsafe transition before any device resources are released.
     *
     * @param backend requested backend
     */
    default void validateGraphicsReset(GraphicsBackend backend) {
    }

    /**
     * Releases native device resources while retaining CPU state for restoration.
     */
    default void releaseGraphicsResources() {
    }

    /**
     * Restores native resources on the current device without resetting app state.
     */
    default void restoreGraphicsResources() {
    }
}
