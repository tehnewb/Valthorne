package valthorne.graphics.font.directwrite;

import java.lang.invoke.MethodHandle;

/**
 * Converts foreign-linker invocation failures into contextual Java exceptions.
 * This preparation-only wrapper never participates in cached OpenGL text drawing.
 */
final class WindowsFunction {
    private final MethodHandle handle; // Borrowed downcall whose native library outlives this wrapper.

    /**
     * Borrows one linked native function.
     *
     * @param handle foreign-linker downcall
     */
    WindowsFunction(MethodHandle handle) {
        this.handle = handle;
    }

    /**
     * Invokes the linked function without losing the underlying failure.
     *
     * @param arguments ABI-compatible boxed arguments
     * @return native result, or null for void
     */
    Object invoke(Object... arguments) {
        try {
            return handle.invokeWithArguments(arguments);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Windows native call failed", failure);
        }
    }
}
