package valthorne.graphics.bgfx;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.lwjgl.bgfx.BGFXVertexLayout;
import valthorne.Window;
import valthorne.graphics.Color;
import valthorne.math.geometry.Shape;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import static org.lwjgl.bgfx.BGFX.*;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.opengl.GL33.*;

/**
 * Draws live 2D shapes with compact bgfx instances and ordered triangle streams.
 * Owns a graphics-thread runtime, reusable staging buffers and cached quad mesh.
 * Create on the current OpenGL context, submit between begin/end, and close on
 * the same thread. Generic matrix and mesh submission support specialized local
 * renderers without loading any private model or camera classes.
 *
 * <pre>{@code
 * try (BgfxShapeRenderer renderer = new BgfxShapeRenderer()) {
 *     renderer.begin();
 *     renderer.draw(shape);
 *     renderer.end();
 * }
 * }</pre>
 */
public class BgfxShapeRenderer implements AutoCloseable {
    /**
     * Solid shapes use vertex colors and conventional source-alpha compositing.
     * Alpha channels use source-over rather than squaring the source alpha.
     */
    private static final long COLOR_STATE = BGFX_STATE_WRITE_RGB | BGFX_STATE_WRITE_A | BGFX_STATE_MSAA | BGFX_STATE_BLEND_FUNC_SEPARATE(BGFX_STATE_BLEND_SRC_ALPHA, BGFX_STATE_BLEND_INV_SRC_ALPHA, BGFX_STATE_BLEND_ONE, BGFX_STATE_BLEND_INV_SRC_ALPHA);

    protected final BgfxDevice device; // Owned single-threaded bgfx runtime.
    private final BgfxPolygonTessellator polygons = new BgfxPolygonTessellator(); // Reused live-boundary triangulation and stroke storage.
    private final Matrix4f transform = new Matrix4f(); // Reused ordinary window projection transform.
    private final Matrix4f projectionMatrix = new Matrix4f(); // Reused projection conversion storage.
    private final float[] view = new float[16]; // Copied column-major view for the current batch.
    private final float[] projection = new float[16]; // Copied column-major projection for the current batch.
    private final int[] viewport = new int[4]; // OpenGL viewport selected when begin is called.
    private final int[] scissor = new int[4]; // OpenGL clip rectangle selected when begin is called.
    private final int[] framebufferWidth = new int[1]; // Reused GLFW framebuffer width query destination.
    private final int[] framebufferHeight = new int[1]; // Reused GLFW framebuffer height query destination.
    private BgfxVertexBuffer vertices; // Owned sixteen-byte streamed triangle records.
    private BgfxVertexBuffer instances; // Owned eighty-byte matrix/tint instance records.
    private BgfxPrimitives2D primitives; // Owned shared-quad rendering and compact 2D instances.
    private BgfxMesh[] commandMeshes = new BgfxMesh[64]; // Null selects triangle streaming; other entries select cached instance meshes.
    private int[] commandStarts = new int[64]; // First vertex or instance for each command.
    private int[] commandCounts = new int[64]; // Number of vertices or instances for each command.
    private long[] commandStates = new long[64]; // Copied pipeline state for each command.
    private int commands; // Number of distinct ordered ranges queued this batch.
    private boolean drawing; // Whether a begin/end batch is currently open.
    private boolean depth; // Whether this batch tests and writes opaque shape depth.
    private boolean clipped; // Whether the caller had scissor testing enabled at begin.
    private boolean compactCircles; // Whether the framebuffer has no MSAA sample coverage to preserve.
    private boolean closed; // Whether owned buffers and runtime have been released.
    private int lastDrawCalls; // Number of submitted bgfx draws in the completed batch.
    private int lastInstances; // Number of submitted cached mesh instances.
    private long lastTriangles; // Number of submitted triangles including instanced geometry.
    private long lastUploadedBytes; // Staging bytes uploaded during the completed batch.

    /**
     * Initializes bgfx on Valthorne's current desktop OpenGL context and creates
     * reusable stream storage. Initial capacity is 16,384 vertices and 4,096 instances.
     *
     * @throws IllegalStateException if the context is absent or another renderer is live
     * @throws UnsupportedOperationException if the platform or instancing is unsupported
     */
    public BgfxShapeRenderer() {
        device = new BgfxDevice();
        try {
            vertices = new BgfxVertexBuffer(device.layout(), 16_384);
            try (BGFXVertexLayout layout = BGFXVertexLayout.calloc()) {
                bgfx_vertex_layout_begin(layout, BGFX_RENDERER_TYPE_OPENGL);
                for (int i = 0; i < 5; i++)
                    bgfx_vertex_layout_add(layout, BGFX_ATTRIB_TEXCOORD0 + i, (byte) 4, BGFX_ATTRIB_TYPE_FLOAT, false, false);
                bgfx_vertex_layout_end(layout);
                instances = new BgfxVertexBuffer(layout, 4_096);
            }
            primitives = new BgfxPrimitives2D(device);
        } catch (RuntimeException | Error failure) {
            if (primitives != null) primitives.close();
            if (instances != null) instances.close();
            if (vertices != null) vertices.close();
            device.close();
            throw failure;
        }
    }

    /**
     * Begins ordered 2D drawing under the current Window projection. Depth testing
     * is disabled; the existing window pixels are preserved and shapes blend over them.
     *
     * @throws IllegalStateException if another batch is open or the renderer is closed
     */
    public void begin() {
        beginBatch(false);
        transform.identity()
                .get(view);
        Window.copyProjectionMatrix(projection);
    }



    /**
     * Begins a batch with explicit column-major matrices, allowing custom 2D/3D cameras.
     *
     * @param viewMatrix borrowed world-to-view transform
     * @param projection borrowed projection using OpenGL minus-one-to-one clip depth
     * @param depthTest whether opaque shapes test and write depth
     */
    public void begin(Matrix4fc viewMatrix, Matrix4fc projection, boolean depthTest) {
        Objects.requireNonNull(viewMatrix, "viewMatrix");
        Objects.requireNonNull(projection, "projection");
        if (!viewMatrix.isFinite() || !projection.isFinite())
            throw new IllegalArgumentException("Shape camera matrices must be finite");
        beginBatch(depthTest);
        viewMatrix.get(view);
        projection.get(this.projection);
    }

    /**
     * Validates the rendering boundary and resets staging without reallocating it.
     *
     * @param depthTest whether the batch uses 3D depth state
     */
    private void beginBatch(boolean depthTest) {
        device.checkOwner();
        if (drawing) throw new IllegalStateException("End or cancel the current bgfx batch first");
        if (device.state.integer(GL_DRAW_FRAMEBUFFER_BINDING) != 0)
            throw new UnsupportedOperationException("BgfxShapeRenderer currently draws to the window framebuffer");
        glfwGetFramebufferSize(Window.getAddress(), framebufferWidth, framebufferHeight);
        glGetIntegerv(GL_VIEWPORT, viewport);
        if (framebufferWidth[0] <= 0 || framebufferHeight[0] <= 0 || framebufferWidth[0] > 65_535 || framebufferHeight[0] > 65_535 || viewport[0] < 0 || viewport[1] < 0 || viewport[2] <= 0 || viewport[3] <= 0 || (long) viewport[0] + viewport[2] > framebufferWidth[0] || (long) viewport[1] + viewport[3] > framebufferHeight[0])
            throw new IllegalArgumentException("bgfx requires a positive viewport inside a framebuffer of at most 65535 pixels per axis");
        clipped = glIsEnabled(GL_SCISSOR_TEST);
        glGetIntegerv(GL_SCISSOR_BOX, scissor);
        vertices.clear();
        instances.clear();
        primitives.instances().clear();
        compactCircles = device.state.integer(GL_SAMPLES) == 0;
        Arrays.fill(commandMeshes, 0, commands, null);
        commands = 0;
        depth = depthTest;
        drawing = true;
    }

    /**
     * Appends the current shape boundary, fill and optional centered border.
     * Null shapes and boundaries with fewer than three usable points are skipped.
     * Geometry and colors are copied now; later caller edits do not change this batch.
     *
     * @param shape borrowed current shape geometry
     * @throws IllegalArgumentException for non-finite or unsupported polygon geometry
     */
    public void draw(Shape shape) {
        requireDrawing();
        if (shape != null && !primitives.draw(shape, this, compactCircles)) polygons.draw(shape, this);
    }

    /**
     * Appends shapes in collection iteration order into the current batch.
     *
     * @param shapes borrowed collection; null is treated as empty
     */
    public void drawAll(Collection<? extends Shape> shapes) {
        requireDrawing();
        if (shapes == null) return;
        for (Shape shape : shapes)
            if (shape != null && !primitives.draw(shape, this, compactCircles)) polygons.draw(shape, this);
    }

    /**
     * Appends an unlit world-space triangle using three borrowed positions.
     *
     * @param a first finite world position
     * @param b second finite world position
     * @param c third finite world position
     * @param color copied triangle tint
     */
    public void triangle(Vector3fc a, Vector3fc b, Vector3fc c, Color color) {
        requireDrawing();
        Objects.requireNonNull(color, "color");
        if (!a.isFinite() || !b.isFinite() || !c.isFinite()) throw new IllegalArgumentException("Triangle positions must be finite");
        range(null, state(color.getAlpha() < 255), vertices.count(), 3);
        ByteBuffer bytes = vertices.reserve(48);
        int tint = BgfxVertexFormat.pack(color);
        BgfxVertexFormat.vertex(bytes, a.x(), a.y(), a.z(), tint);
        BgfxVertexFormat.vertex(bytes, b.x(), b.y(), b.z(), tint);
        BgfxVertexFormat.vertex(bytes, c.x(), c.y(), c.z(), tint);
    }

















    /**
     * Copies one finite transform and color into contiguous instance staging.
     *
     * @param mesh retained immutable geometry
     * @param matrix finite instance matrix
     * @param color copied tint
     */
    protected final void instance(BgfxMesh mesh, Matrix4fc matrix, Color color) {
        Objects.requireNonNull(color, "color");
        if (!matrix.isFinite()) throw new IllegalArgumentException("Shape transforms and positions must be finite");
        range(mesh, state(mesh.transparent() || color.getAlpha() < 255), instances.count(), 1);
        ByteBuffer data = instances.reserve(80);
        int offset = data.position();
        matrix.get(offset, data);
        data.position(offset + 64);
        data.putFloat(color.r())
                .putFloat(color.g())
                .putFloat(color.b())
                .putFloat(color.a());
    }

    /**
     * Copies compact primitive parameters and tint into one ordered quad instance.
     *
     * @param mesh shared immutable unit quad
     * @param x lower-left X
     * @param y lower-left Y
     * @param width positive rectangle width or negative circle diameter
     * @param height rectangle height or circle perimeter segment count
     * @param color copied fill tint
     */
    void appendPrimitive(BgfxMesh mesh, float x, float y, float width, float height, Color color) {
        BgfxVertexBuffer stream = primitives.instances();
        range(mesh, state(color.getAlpha() < 255), stream.count(), 1);
        stream.reserve(32)
                .putFloat(x)
                .putFloat(y)
                .putFloat(width)
                .putFloat(height)
                .putFloat(color.r())
                .putFloat(color.g())
                .putFloat(color.b())
                .putFloat(color.a());
    }

    /**
     * Appends prepared polygon fill indices into the shared triangle range.
     *
     * @param x boundary X coordinates
     * @param y boundary Y coordinates
     * @param indices complete triangulation indices
     * @param count number of indices to append
     * @param tint packed RGBA tint
     */
    void appendPolygon(float[] x, float[] y, int[] indices, int count, int tint) {
        if (count == 0 || tint >>> 24 == 0) return;
        range(null, state((tint >>> 24) < 255), vertices.count(), count);
        ByteBuffer data = vertices.reserve(Math.multiplyExact(count, 16));
        for (int i = 0; i < count; i++) {
            int index = indices[i];
            BgfxVertexFormat.vertex(data, x[index], y[index], 0, tint);
        }
    }

    /**
     * Appends prepared centered border segment quads into the triangle range.
     *
     * @param leftX first border side X
     * @param leftY first border side Y
     * @param rightX opposite border side X
     * @param rightY opposite border side Y
     * @param count number of shared join positions
     * @param tint packed border RGBA tint
     */
    void appendBorder(float[] leftX, float[] leftY, float[] rightX, float[] rightY, int count, int tint) {
        if (tint >>> 24 == 0) return;
        range(null, state((tint >>> 24) < 255), vertices.count(), Math.multiplyExact(count, 6));
        ByteBuffer data = vertices.reserve(Math.multiplyExact(count, 96));
        for (int i = 0; i < count; i++) {
            int j = i + 1 == count ? 0 : i + 1;
            BgfxVertexFormat.vertex(data, leftX[i], leftY[i], 0, tint);
            BgfxVertexFormat.vertex(data, rightX[i], rightY[i], 0, tint);
            BgfxVertexFormat.vertex(data, rightX[j], rightY[j], 0, tint);
            BgfxVertexFormat.vertex(data, leftX[i], leftY[i], 0, tint);
            BgfxVertexFormat.vertex(data, rightX[j], rightY[j], 0, tint);
            BgfxVertexFormat.vertex(data, leftX[j], leftY[j], 0, tint);
        }
    }

    /**
     * Selects depth and blending behavior for the copied shape alpha.
     *
     * @param transparent whether source alpha is less than one
     * @return complete pipeline state
     */
    private long state(boolean transparent) {
        return COLOR_STATE | (depth ? BGFX_STATE_DEPTH_TEST_LEQUAL | (transparent ? 0 : BGFX_STATE_WRITE_Z) : 0);
    }

    /**
     * Merges an adjacent compatible range or records one new ordered draw command.
     *
     * @param mesh null for streamed triangles, otherwise an instance mesh
     * @param state complete pipeline state
     * @param start first record in the selected staging buffer
     * @param count appended record count
     */
    private void range(BgfxMesh mesh, long state, int start, int count) {
        int previous = commands - 1;
        if (previous >= 0 && commandMeshes[previous] == mesh && commandStates[previous] == state) {
            commandCounts[previous] = Math.addExact(commandCounts[previous], count);
            return;
        }
        if (commands == commandMeshes.length) {
            int capacity = Math.multiplyExact(commands, 2);
            commandMeshes = Arrays.copyOf(commandMeshes, capacity);
            commandStarts = Arrays.copyOf(commandStarts, capacity);
            commandCounts = Arrays.copyOf(commandCounts, capacity);
            commandStates = Arrays.copyOf(commandStates, capacity);
        }
        commandMeshes[commands] = mesh;
        commandStarts[commands] = start;
        commandCounts[commands] = count;
        commandStates[commands++] = state;
    }

    /**
     * Uploads and executes the batch immediately, preserving the window's pixels
     * and covered OpenGL state. Does not present or wait for GPU completion.
     * Queued shape draws finish in order before later OpenGL calls on this context.
     */
    public void end() {
        requireDrawing();
        device.checkOwner();
        lastDrawCalls = 0;
        lastInstances = 0;
        lastTriangles = 0;
        lastUploadedBytes = 0;
        drawing = false;
        if (commands == 0 || (clipped && (scissor[2] <= 0 || scissor[3] <= 0))) return;
        device.state.capture();
        try {
            device.resize(framebufferWidth[0], framebufferHeight[0]);
            bgfx_set_view_rect(0, viewport[0], framebufferHeight[0] - viewport[1] - viewport[3], viewport[2], viewport[3]);
            if (clipped) {
                int left = Math.max(0, scissor[0]);
                int bottom = Math.max(0, scissor[1]);
                int right = Math.min(framebufferWidth[0], scissor[0] + scissor[2]);
                int top = Math.min(framebufferHeight[0], scissor[1] + scissor[3]);
                if (right <= left || top <= bottom) return;
                bgfx_set_view_scissor(0, left, framebufferHeight[0] - top, right - left, top - bottom);
            } else {
                bgfx_set_view_scissor(0, 0, 0, 0, 0);
            }
            projectionMatrix.set(projection);
            if (!device.homogeneousDepth()) {
                projectionMatrix.m02((projectionMatrix.m02() + projectionMatrix.m03()) * .5f);
                projectionMatrix.m12((projectionMatrix.m12() + projectionMatrix.m13()) * .5f);
                projectionMatrix.m22((projectionMatrix.m22() + projectionMatrix.m23()) * .5f);
                projectionMatrix.m32((projectionMatrix.m32() + projectionMatrix.m33()) * .5f);
            }
            projectionMatrix.get(projection);
            bgfx_set_view_transform(0, view, projection);
            vertices.upload();
            instances.upload();
            primitives.instances().upload();
            lastUploadedBytes = (long) vertices.count() * 16 + (long) instances.count() * 80 + (long) primitives.instances().count() * 32;
            for (int i = 0; i < commands; i++) {
                BgfxMesh mesh = commandMeshes[i];
                int count = commandCounts[i];
                boolean primitive = mesh != null && mesh == primitives.mesh();
                if (mesh == null) {
                    bgfx_set_dynamic_vertex_buffer(0, vertices.handle(), commandStarts[i], count);
                    lastTriangles += count / 3;
                } else {
                    mesh.bind();
                    bgfx_set_instance_data_from_dynamic_vertex_buffer(primitive ? primitives.instances().handle() : instances.handle(), commandStarts[i], count);
                    lastInstances += count;
                    lastTriangles += (long) mesh.vertices() / 3 * count;
                }
                bgfx_set_state(commandStates[i], 0);
                bgfx_submit(0, primitive ? device.primitiveProgram() : device.program(mesh != null), 0, BGFX_DISCARD_ALL);
            }
            device.execute();
            lastDrawCalls = commands;
        } finally {
            device.state.restore();
        }
    }

    /**
     * Discards an open CPU batch without submitting its geometry. Resource uploads
     * already queued by newly cached meshes remain owned by the renderer.
     */
    public void cancel() {
        requireDrawing();
        drawing = false;
        vertices.clear();
        instances.clear();
        primitives.instances().clear();
        Arrays.fill(commandMeshes, 0, commands, null);
        commands = 0;
    }

    /**
     * Releases all retained primitive and model meshes outside an open batch.
     * The next use of each geometry combination uploads a new immutable mesh.
     */
    public void clearMeshCache() {
        device.checkOwner();
        if (drawing) throw new IllegalStateException("End the batch before clearing cached meshes");
        primitives.clearMesh();
    }

    /**
     * Returns the number of draws submitted by the most recently completed batch.
     *
     * @return draw count, zero for an empty or fully clipped batch
     */
    public int getLastDrawCalls() {
        return lastDrawCalls;
    }

    /**
     * Returns the number of cached mesh instances in the last completed batch.
     *
     * @return submitted instance count
     */
    public int getLastInstances() {
        return lastInstances;
    }

    /**
     * Returns the triangle count including expansion of instanced meshes.
     *
     * @return submitted triangle count
     */
    public long getLastTriangles() {
        return lastTriangles;
    }

    /**
     * Returns streaming upload bytes, excluding first-use immutable mesh uploads.
     *
     * @return uploaded staging bytes in the last completed batch
     */
    public long getLastUploadedBytes() {
        return lastUploadedBytes;
    }

    /**
     * Returns the number of retained primitive and immutable model meshes.
     *
     * @return owned GPU mesh count
     */
    public int getCachedMeshCount() {
        return primitives.mesh() == null ? 0 : 1;
    }

    /**
     * Checks lifetime and thread confinement before accepting a draw submission.
     */
    protected final void requireDrawing() {
        device.checkThread();
        if (!drawing) throw new IllegalStateException("Call begin before submitting bgfx shapes");
    }





    @Override
    public void close() {
        if (closed) return;
        device.checkOwner();
        drawing = false;
        vertices.close();
        instances.close();
        primitives.close();
        device.close();
        closed = true;
    }
}
