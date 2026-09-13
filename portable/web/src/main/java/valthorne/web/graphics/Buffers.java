package valthorne.web.graphics;
import java.nio.FloatBuffer;
/** Browser-owned NIO staging memory; no native allocation is required. */
public final class Buffers {
    private Buffers(){}
    public static FloatBuffer createFloatBuffer(int count){return FloatBuffer.allocate(count);}
    public static java.nio.IntBuffer createIntBuffer(int count){return java.nio.IntBuffer.allocate(count);}
    public static java.nio.ByteBuffer createByteBuffer(int count){return java.nio.ByteBuffer.allocate(count).order(java.nio.ByteOrder.nativeOrder());}
}
