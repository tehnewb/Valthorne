package org.lwjgl.glfw;

/**
 * Receives browser drop events using the engine's GLFW-compatible values.
 */
@FunctionalInterface
public interface GLFWDropCallbackI {
    /**
     * Delivers the event on the application coroutine.
     * @param window copied event value
     * @param count copied event value
     * @param names copied event value
     */
    void invoke(long window, int count, long names);

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
