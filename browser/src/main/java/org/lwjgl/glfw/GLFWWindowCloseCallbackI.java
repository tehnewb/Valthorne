package org.lwjgl.glfw;

/**
 * Receives browser window close events using the engine's GLFW-compatible values.
 */
@FunctionalInterface
public interface GLFWWindowCloseCallbackI {
    /**
     * Delivers the event on the application coroutine.
     * @param window copied event value
     */
    void invoke(long window);

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
