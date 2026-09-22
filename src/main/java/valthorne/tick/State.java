package valthorne.tick;

/**
 * The externally observable lifecycle state of a {@link Tick}.
 *
 * <p>Read the current value through {@link Tick#getState()}. A paused tick retains
 * callback timing progress and continues to observe lifecycle conditions and
 * scheduled actions. A stopped tick does not advance until explicitly started.</p>
 *
 * @author Albert Beaupre
 * @since September 22nd, 2026
 */
public enum State {
    /**
     * Inactive; requires an explicit start or restart.
     */
    STOPPED,
    /**
     * Active; time advances when execution conditions allow it.
     */
    RUNNING,
    /**
     * Suspended; progress is retained and lifecycle conditions remain active.
     */
    PAUSED
}
