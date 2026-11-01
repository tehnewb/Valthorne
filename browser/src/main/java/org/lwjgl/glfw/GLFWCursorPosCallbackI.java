package org.lwjgl.glfw;

/**
 * Receives browser cursor pos events using the engine's GLFW-compatible values.
 */
@FunctionalInterface
public interface GLFWCursorPosCallbackI {
    /**
     * Delivers the event on the application coroutine.
     * @param window copied event value
     * @param x copied event value
     * @param y copied event value
     */
    void invoke(long window, double x, double y);

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
