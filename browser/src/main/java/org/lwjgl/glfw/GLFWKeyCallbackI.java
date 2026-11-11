package org.lwjgl.glfw;

/**
 * Receives browser key events using the engine's GLFW-compatible values.
 */
@FunctionalInterface
public interface GLFWKeyCallbackI {
    /**
     * Delivers the event on the application coroutine.
     * @param window copied event value
     * @param key copied event value
     * @param scancode copied event value
     * @param action copied event value
     * @param mods copied event value
     */
    void invoke(long window, int key, int scancode, int action, int mods);

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
