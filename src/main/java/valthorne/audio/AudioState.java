package valthorne.audio;

/**
 * Observable state of the engine's current audio session. STARTING accepts queued
 * requests without requiring a native context yet. READY permits audio-thread
 * execution. STOPPING rejects new requests while the owning thread releases its
 * resources; STOPPED and FAILED are terminal for that session.
 */
public enum AudioState {
    /**
     * No audio session has been requested.
     */
    NOT_STARTED,
    /**
     * The owning worker is opening its device and context; commands may queue.
     */
    STARTING,
    /**
     * The native context is usable on the owning audio thread.
     */
    READY,
    /**
     * New commands are rejected while the owner completes native teardown.
     */
    STOPPING,
    /**
     * The session has released its native resources without a lifecycle failure.
     */
    STOPPED,
    /**
     * Startup, execution infrastructure, or native cleanup failed.
     */
    FAILED
}
