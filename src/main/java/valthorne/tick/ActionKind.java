package valthorne.tick;

/**
 * Internal operation performed when a scheduled wait completes.
 *
 * <p>This package-private enum identifies the built-in lifecycle requests and
 * user callbacks stored by {@link ScheduledAction}. Applications register
 * actions through {@link DelayedAction} rather than using this type directly.</p>
 *
 * @author Albert Beaupre
 * @since September 22nd, 2026
 */
enum ActionKind {
    /**
     * Pause a running tick.
     */
    PAUSE,
    /**
     * Resume a paused tick when pause conditions permit it.
     */
    CONTINUE,
    /**
     * End a running or paused tick.
     */
    STOP,
    /**
     * Invoke a user-provided one-shot action.
     */
    CUSTOM
}
