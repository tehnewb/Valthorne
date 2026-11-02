package org.lwjgl.glfw;

/**
 * Receives browser mouse button events using the engine's GLFW-compatible values.
 */
@FunctionalInterface
public interface GLFWMouseButtonCallbackI {
    /**
     * Delivers the event on the application coroutine.
     * @param window copied event value
     * @param button copied event value
     * @param action copied event value
     * @param mods copied event value
     */
    void invoke(long window, int button, int action, int mods);

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
