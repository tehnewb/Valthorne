package valthorne.web.graphics;
import java.nio.FloatBuffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
/** Browser-owned NIO staging memory; no native allocation is required. */
public final class Buffers {
    private Buffers(){}
    public static FloatBuffer createFloatBuffer(int count){return FloatBuffer.allocate(count);}
    public static IntBuffer createIntBuffer(int count){return IntBuffer.allocate(count);}
    public static ByteBuffer createByteBuffer(int count){return ByteBuffer.allocate(count).order(ByteOrder.nativeOrder());}
}
