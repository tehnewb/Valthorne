package valthorne.android.internal;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Reusable direct command storage confined to one asset or render thread. Buffers
 * grow only for a larger heap-backed upload; ordinary rendering reuses all storage.
 */
final class AndroidNativeScratch {
    final IntBuffer words = ByteBuffer.allocateDirect(128).order(ByteOrder.nativeOrder()).asIntBuffer(); // Command arguments and results.
    final FloatBuffer values = ByteBuffer.allocateDirect(65536).order(ByteOrder.nativeOrder()).asFloatBuffer(); // Scalars and matrices.
    final long wordsAddress = AndroidNative.address(words); // Stable address retained by the thread-local direct buffer.
    final long valuesAddress = AndroidNative.address(values); // Stable scalar address retained for this thread's lifetime.
    ByteBuffer upload = ByteBuffer.allocateDirect(256); // Staging for heap-backed image and shader inputs.

    /**
     * Creates isolated scratch for the calling thread.
     */
    AndroidNativeScratch() {
    }
}
