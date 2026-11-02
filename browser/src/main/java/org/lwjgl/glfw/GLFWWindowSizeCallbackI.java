package org.lwjgl.glfw;

/**
 * Receives browser window size events using the engine's GLFW-compatible values.
 */
@FunctionalInterface
public interface GLFWWindowSizeCallbackI {
    /**
     * Delivers the event on the application coroutine.
     * @param window copied event value
     * @param width copied event value
     * @param height copied event value
     */
    void invoke(long window, int width, int height);

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
