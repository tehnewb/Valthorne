package org.lwjgl.glfw;

/**
 * Receives browser char events using the engine's GLFW-compatible values.
 */
@FunctionalInterface
public interface GLFWCharCallbackI {
    /**
     * Delivers the event on the application coroutine.
     * @param window copied event value
     * @param codepoint copied event value
     */
    void invoke(long window, int codepoint);

    /**
     * Releases this callback's ownership. DOM registrations are detached by the
     * matching setter; this Java callback contains no native allocation.
     */
    default void free() {
        /*
         * Browser callbacks are ordinary GC-managed objects.
         */
    }
}
