package valthorne.graphics.model;

import org.lwjgl.BufferUtils;
import valthorne.camera.Camera3D;
import valthorne.graphics.Color;
import valthorne.graphics.shader.DepthShader3D;
import valthorne.graphics.shader.LightingShader3D;
import valthorne.graphics.shader.Mesh3DShader;
import valthorne.graphics.shader.Shader;
import valthorne.math.MathUtils;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

/**
 * Accumulates world-space triangles in a growable CPU buffer and submits them
 * through an owned OpenGL vertex array and buffer. Each vertex stores position,
 * normal, RGBA color, and UV coordinates as twelve floats. Primitive helpers use
 * flat face normals and zero UVs; model emission preserves source normals and UVs
 * while applying the instance transform.
 * <p>
 * Use {@link #begin()} to start CPU geometry, then {@link #render(MeshRenderState3D)}
 * to stream it immediately, or {@link #upload()} followed by repeated
 * {@link #renderUploaded(MeshRenderState3D)} calls for retained geometry. Both
 * paths share the same GPU buffer: streaming overwrites retained data, so upload
 * again before resuming retained rendering. A material applies to the entire draw;
 * use separate batches or the model batch to group incompatible materials.
 * </p>
 * <p>
 * Construction, upload, rendering, and disposal require a current OpenGL context.
 * Public rendering and upload methods restore the state covered by
 * {@link RenderStateSnapshot3D}; construction leaves array bindings at zero.
 * The batch owns its shaders and GPU objects, but borrows models, textures,
 * materials, cameras, and lighting. Mutable scratch storage makes it unsuitable
 * for concurrent use.
 * </p>
 * <pre>{@code
 * MeshBatch3D mesh = new MeshBatch3D();
 * try {
 *     mesh.begin();
 *     mesh.box(0, 0, 1, 2, 2, 2, 0, color);
 *     mesh.upload();
 *     mesh.renderUploaded(new MeshRenderState3D().setCamera(camera));
 * } finally {
 *     mesh.dispose();
 * }
 * }</pre>
 * @author Albert Beaupre
 */
public final class MeshBatch3D {
    private final float[] matrixUpload = new float[16]; // Reusable column-major matrix array for shader uploads.

    private final Matrix4f modelTransform = new Matrix4f(); // Scratch model-to-world matrix for instance emission.
    private final org.joml.Matrix3f normalTransform = new org.joml.Matrix3f(); // Scratch inverse-transpose matrix for model normals.


    /**
     * Interleaved vertex width: XYZ position, XYZ normal, RGBA color, and UV.
     */
    private static final int FLOATS_PER_VERTEX = 12;
    /**
     * Default initial CPU and GPU capacity, measured in floats.
     */
    private static final int DEFAULT_INITIAL_FLOATS = 1_048_576;
    /**
     * Shared fallback material when a render state supplies none.
     */
    private static final Material3D DEFAULT_MATERIAL = new Material3D();
    private final Mesh3DShader compatibilityShader; // Owned compatibility shader created with the batch.
    private final int vaoId; // Owned OpenGL vertex-array name.
    private final int vboId; // Owned OpenGL vertex-buffer name.
    private final Vector3f scratchA = new Vector3f(); // Reusable geometry scratch vector A; also shared by model transformation.
    private final Vector3f scratchB = new Vector3f(); // Reusable geometry scratch vector B; also shared by model transformation.
    private final Vector3f scratchC = new Vector3f(); // Reusable geometry scratch vector C; also shared by model transformation.
    private final Vector3f scratchD = new Vector3f(); // Reusable geometry scratch vector D; also shared by model transformation.
    private final Vector3f scratchE = new Vector3f(); // Reusable geometry scratch vector E; also shared by model transformation.
    private final Vector3f scratchF = new Vector3f(); // Reusable geometry scratch vector F; also shared by model transformation.
    private final Vector3f scratchG = new Vector3f(); // Reusable geometry scratch vector G; also shared by model transformation.
    private final Vector3f scratchH = new Vector3f(); // Reusable geometry scratch vector H; also shared by model transformation.
    private final ModelInstance3D scratchInstance = new ModelInstance3D(); // Reusable instance for model/translation/scale convenience emission.
    private Shader shader, lightingShader, depthShader; // Active shader and lazily allocated lighting and depth shader references.
    private FloatBuffer vertexBuffer; // Growable direct CPU buffer positioned after the accumulated vertex data.
    private int vertexCount; // Number of CPU vertices accumulated since begin.
    private boolean disposed; // Whether GPU resource disposal has been requested.
    private int uploadedVertexCount; // Draw count recorded by upload; streaming draws do not update it.
    private int bufferCapacityFloats; // Current CPU buffer capacity in floats.
    private int gpuCapacityFloats; // Current GPU buffer capacity in floats.

    /**
     * Creates an empty batch with storage for 1,048,576 floats on both CPU and GPU.
     * Compiles the compatibility shader immediately; lighting and depth shaders are
     * created lazily when their rendering paths are first selected.
     */
    public MeshBatch3D() {
        this(DEFAULT_INITIAL_FLOATS);
    }

    /**
     * Allocates CPU and GPU storage and configures position, normal, color, and UV
     * vertex attributes. Capacity is expressed in floats, not vertices or bytes,
     * and grows automatically as geometry is appended.
     *
     * @param initialCapacityFloats initial capacity of at least 36 floats
     * @throws IllegalArgumentException if capacity cannot hold one triangle
     */
    public MeshBatch3D(int initialCapacityFloats) {
        if (initialCapacityFloats < FLOATS_PER_VERTEX * 3) {
            throw new IllegalArgumentException("initialCapacityFloats must hold at least one triangle");
        }

        this.bufferCapacityFloats = initialCapacityFloats;
        this.gpuCapacityFloats = initialCapacityFloats;
        this.vertexBuffer = BufferUtils.createFloatBuffer(initialCapacityFloats);
        this.shader = compatibilityShader = new Mesh3DShader();

        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, (long) gpuCapacityFloats * Float.BYTES, GL_DYNAMIC_DRAW);

        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        glEnableVertexAttribArray(Mesh3DShader.ATTR_POSITION);
        glVertexAttribPointer(Mesh3DShader.ATTR_POSITION, 3, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(Mesh3DShader.ATTR_NORMAL);
        glVertexAttribPointer(Mesh3DShader.ATTR_NORMAL, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(Mesh3DShader.ATTR_COLOR);
        glVertexAttribPointer(Mesh3DShader.ATTR_COLOR, 4, GL_FLOAT, false, stride, 6L * Float.BYTES);

        glEnableVertexAttribArray(Mesh3DShader.ATTR_UV);
        glVertexAttribPointer(Mesh3DShader.ATTR_UV, 2, GL_FLOAT, false, stride, 10L * Float.BYTES);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    /**
     * Clears accumulated CPU geometry and resets its vertex count. Preserves GPU
     * data and the retained uploaded count, allowing previously uploaded geometry
     * to remain available until a subsequent upload or streaming render overwrites it.
     *
     * @throws IllegalStateException if this batch is disposed
     */
    public void begin() {
        if (disposed) throw new IllegalStateException("MeshBatch3D is disposed");
        vertexBuffer.clear();
        vertexCount = 0;
    }

    /**
     * Clears CPU geometry and sets the retained draw count to zero. GPU storage is
     * kept allocated but will no longer be drawn by renderUploaded until another upload.
     *
     * @throws IllegalStateException if this batch is disposed
     */
    public void clear() {
        begin();
        uploadedVertexCount = 0;
    }

    /**
     * Returns the number of vertices currently accumulated on the CPU. Rendering
     * and uploading do not reset this count.
     *
     * @return CPU vertex count
     */
    public int getVertexCount() {
        return vertexCount;
    }

    /**
     * Returns the retained draw count recorded by the last upload, or zero after
     * clear. Streaming rendering does not update this count even though it overwrites
     * the same GPU buffer.
     *
     * @return retained vertex count
     */
    public int getUploadedVertexCount() {
        return uploadedVertexCount;
    }

    /**
     * Appends a flat-shaded triangle with normal {@code (b-a) cross (c-a)} and
     * the same copied color at all vertices. Skips triangles whose cross-product
     * length is at most 0.00001. UV coordinates default to zero.
     *
     * @param a first world-space vertex
     * @param b second world-space vertex
     * @param c third world-space vertex
     * @param color nonnull vertex color copied into the buffer
     * @throws NullPointerException if a vertex or color is null
     */
    public void triangle(Vector3f a, Vector3f b, Vector3f c, Color color) {
        if (a == null) throw new NullPointerException("a");
        if (b == null) throw new NullPointerException("b");
        if (c == null) throw new NullPointerException("c");
        if (color == null) throw new NullPointerException("color");

        float ux = b.x() - a.x();
        float uy = b.y() - a.y();
        float uz = b.z() - a.z();
        float vx = c.x() - a.x();
        float vy = c.y() - a.y();
        float vz = c.z() - a.z();

        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length <= 0.00001f) {
            return;
        }

        float invLength = 1f / length;
        nx *= invLength;
        ny *= invLength;
        nz *= invLength;

        putVertex(a.x(), a.y(), a.z(), nx, ny, nz, color);
        putVertex(b.x(), b.y(), b.z(), nx, ny, nz, color);
        putVertex(c.x(), c.y(), c.z(), nx, ny, nz, color);
    }

    /**
     * Appends triangles (a,b,c) and (a,c,d), preserving the supplied winding.
     * Each triangle independently computes a face normal and may be skipped if
     * degenerate; nonplanar quads therefore have two flat-shaded faces.
     *
     * @param a first world-space corner
     * @param b second world-space corner
     * @param c third world-space corner
     * @param d fourth world-space corner
     * @param color color copied to each emitted vertex
     * @throws NullPointerException if a corner or color is null
     */
    public void quad(Vector3f a, Vector3f b, Vector3f c, Vector3f d, Color color) {
        triangle(a, b, c, color);
        triangle(a, c, d, color);
    }

    /**
     * Appends the six flat-shaded faces of a centered box, rotated about the Y axis.
     * Dimensions are full extents; this helper does not validate their sign or
     * finiteness. Negative sizes can reverse winding.
     *
     * @param centerX world center X
     * @param centerY world center Y
     * @param centerZ world center Z
     * @param sizeX full local X extent
     * @param sizeY full local Y extent
     * @param sizeZ full local Z extent
     * @param rotationY Y-axis rotation in radians
     * @param color copied face color
     */
    public void box(float centerX, float centerY, float centerZ,
                    float sizeX, float sizeY, float sizeZ,
                    float rotationY, Color color) {
        float halfX = sizeX * 0.5f;
        float halfY = sizeY * 0.5f;
        float halfZ = sizeZ * 0.5f;

        rotatedPoint(centerX, centerY, centerZ, -halfX, -halfY, -halfZ, rotationY, scratchA);
        rotatedPoint(centerX, centerY, centerZ, halfX, -halfY, -halfZ, rotationY, scratchB);
        rotatedPoint(centerX, centerY, centerZ, halfX, halfY, -halfZ, rotationY, scratchC);
        rotatedPoint(centerX, centerY, centerZ, -halfX, halfY, -halfZ, rotationY, scratchD);
        rotatedPoint(centerX, centerY, centerZ, -halfX, -halfY, halfZ, rotationY, scratchE);
        rotatedPoint(centerX, centerY, centerZ, halfX, -halfY, halfZ, rotationY, scratchF);
        rotatedPoint(centerX, centerY, centerZ, halfX, halfY, halfZ, rotationY, scratchG);
        rotatedPoint(centerX, centerY, centerZ, -halfX, halfY, halfZ, rotationY, scratchH);

        quad(scratchE, scratchF, scratchG, scratchH, color);
        quad(scratchD, scratchC, scratchB, scratchA, color);
        quad(scratchA, scratchE, scratchH, scratchD, color);
        quad(scratchC, scratchG, scratchF, scratchB, color);
        quad(scratchD, scratchH, scratchG, scratchC, color);
        quad(scratchA, scratchB, scratchF, scratchE, color);
    }

    /**
     * Appends a Z-aligned capsule consisting of cylinder walls and two hemispheres.
     * The cylindrical portion has length max(0, height - 2 * radius); requesting a
     * shorter total height still produces a sphere of diameter 2 * radius. Geometry
     * uses flat normals. Supply positive dimensions and suitable subdivision counts;
     * this helper does not validate them.
     *
     * @param centerX capsule axis X
     * @param centerY capsule axis Y
     * @param baseZ bottom of the lower hemisphere
     * @param radius circular radius
     * @param height requested overall height
     * @param segments circumferential divisions, normally at least 3
     * @param stacks latitude divisions per hemisphere, normally at least 1
     * @param color copied vertex color
     */
    public void capsule(float centerX, float centerY, float baseZ,
                        float radius, float height,
                        int segments, int stacks,
                        Color color) {
        float cylinderHeight = Math.max(0f, height - radius * 2f);
        float lowerCenterZ = baseZ + radius;
        float upperCenterZ = lowerCenterZ + cylinderHeight;

        cylinderShell(centerX, centerY, lowerCenterZ, upperCenterZ, radius, segments, color);
        hemisphere(centerX, centerY, upperCenterZ, radius, 0f, (float) (Math.PI * 0.5), segments, stacks, color);
        hemisphere(centerX, centerY, lowerCenterZ, radius, (float) (-Math.PI * 0.5), 0f, segments, stacks, color);
    }

    /**
     * Appends two Z-oriented hemispheres forming a flat-shaded sphere. Degenerate
     * pole triangles are skipped by the triangle helper. Subdivision counts and
     * radius are not validated; nonpositive counts emit no corresponding faces.
     *
     * @param centerX world center X
     * @param centerY world center Y
     * @param centerZ world center Z
     * @param radius sphere radius
     * @param segments circumferential divisions, normally at least 3
     * @param stacks latitude divisions per hemisphere
     * @param color copied vertex color
     */
    public void sphere(float centerX, float centerY, float centerZ,
                       float radius, int segments, int stacks,
                       Color color) {
        hemisphere(centerX, centerY, centerZ, radius, 0f, (float) (Math.PI * 0.5), segments, stacks, color);
        hemisphere(centerX, centerY, centerZ, radius, (float) (-Math.PI * 0.5), 0f, segments, stacks, color);
    }

    /**
     * Appends a Z-aligned cylinder with side walls and oppositely wound end caps.
     * Height extends equally above and below the center. Dimensions and subdivision
     * counts are not validated.
     *
     * @param centerX axis X
     * @param centerY axis Y
     * @param centerZ vertical midpoint
     * @param radius circular radius
     * @param height full cylinder height
     * @param segments circumferential divisions, normally at least 3
     * @param color copied vertex color
     */
    public void cylinder(float centerX, float centerY, float centerZ,
                         float radius, float height, int segments,
                         Color color) {
        float lowerZ = centerZ - height * 0.5f;
        float upperZ = centerZ + height * 0.5f;
        cylinderShell(centerX, centerY, lowerZ, upperZ, radius, segments, color);
        disc(centerX, centerY, lowerZ, radius, false, segments, color);
        disc(centerX, centerY, upperZ, radius, true, segments, color);
    }

    /**
     * Appends only the side walls of a centered Z-aligned cylinder. Leaves both
     * ends open and computes a separate flat normal for each wall segment.
     *
     * @param centerX axis X
     * @param centerY axis Y
     * @param centerZ vertical midpoint
     * @param radius circular radius
     * @param height full wall height
     * @param segments circumferential divisions
     * @param color copied vertex color
     */
    public void cylinderWalls(float centerX, float centerY, float centerZ,
                              float radius, float height, int segments,
                              Color color) {
        float lowerZ = centerZ - height * 0.5f;
        float upperZ = centerZ + height * 0.5f;
        cylinderShell(centerX, centerY, lowerZ, upperZ, radius, segments, color);
    }

    /**
     * Appends an XY-plane triangle fan at the supplied Z coordinate. With positive
     * radius and ordinary subdivision counts, topFace selects a positive-Z normal;
     * false reverses the winding. Nonpositive segment counts emit nothing.
     *
     * @param centerX disc center X
     * @param centerY disc center Y
     * @param z plane height
     * @param radius disc radius
     * @param topFace whether the face points toward positive Z
     * @param segments fan triangle count, normally at least 3
     * @param color copied vertex color
     */
    public void disc(float centerX, float centerY, float z,
                     float radius, boolean topFace, int segments,
                     Color color) {
        scratchA.set(centerX, centerY, z);
        for (int segment = 0; segment < segments; segment++) {
            float angle0 = (float) (segment * Math.PI * 2.0 / segments);
            float angle1 = (float) ((segment + 1) * Math.PI * 2.0 / segments);

            scratchB.set(centerX + (float) Math.cos(angle0) * radius, centerY + (float) Math.sin(angle0) * radius, z);
            scratchC.set(centerX + (float) Math.cos(angle1) * radius, centerY + (float) Math.sin(angle1) * radius, z);
            if (topFace) {
                triangle(scratchA, scratchB, scratchC, color);
            } else {
                triangle(scratchA, scratchC, scratchB, color);
            }
        }
    }

    /**
     * Appends a model through a reusable instance with uniform scale and Z-axis yaw.
     * Copies vertex data into the batch; neither the model nor its resources are
     * retained by the emitted geometry. A null model emits nothing.
     *
     * @param model source model
     * @param worldX world translation X
     * @param worldY world translation Y
     * @param worldZ world translation Z
     * @param scale nonzero uniform scale
     * @param yawRadians Z-axis rotation in radians
     * @throws IllegalArgumentException if scale is zero or transform evaluation rejects nonfinite components
     */
    public void model(Model3D model, float worldX, float worldY, float worldZ, float scale, float yawRadians) {
        scratchInstance
                .setModel(model)
                .setPosition(worldX, worldY, worldZ)
                .setScale(scale)
                .setYawRadians(yawRadians);
        model(scratchInstance);
    }

    /**
     * Appends transformed model triangles without visibility testing. Uses the world
     * matrix for positions and its inverse transpose for normals, preserving source
     * UVs and colors. Reflected transforms swap the second and third vertices to
     * preserve front-face winding. A null instance or missing model emits nothing.
     *
     * @param instance borrowed source instance
     * @throws IllegalArgumentException if the instance transform is invalid
     * @throws IllegalStateException if the linear world transform is effectively singular
     */
    public void model(ModelInstance3D instance) {
        if (instance == null) {
            return;
        }

        Model3D model = instance.getModel();
        if (model == null) {
            return;
        }

        Matrix4f transform = instance.getWorldTransform(modelTransform);
        float determinant = transform.determinant3x3();
        if (Math.abs(determinant) < 1e-20f) throw new IllegalStateException("Singular normal transform");
        transform.normal(normalTransform);
        boolean reflected = determinant < 0f;
        for (Model3D.Triangle triangle : model.triangles()) {
            modelVertex(transform, triangle.a, triangle.normalA, triangle.uvA, triangle.color);
            if (reflected) {
                modelVertex(transform, triangle.c, triangle.normalC, triangle.uvC, triangle.color);
                modelVertex(transform, triangle.b, triangle.normalB, triangle.uvB, triangle.color);
            } else {
                modelVertex(transform, triangle.b, triangle.normalB, triangle.uvB, triangle.color);
                modelVertex(transform, triangle.c, triangle.normalC, triangle.uvC, triangle.color);
            }
        }
    }

    /**
     * Transforms and appends one source vertex, normalizing a nonzero transformed
     * normal and replacing the default UV pair with the source coordinates.
     * Uses the normal matrix prepared for the current model.
     *
     * @param transform model-to-world position matrix
     * @param point source position
     * @param normal source normal
     * @param uv source texture coordinates
     * @param color copied vertex color
     */
    private void modelVertex(Matrix4f transform, Vector3f point, Vector3f normal, Vector2f uv, Color color) {
        transform.transformPosition(point, scratchA);
        normalTransform.transform(normal, scratchB);
        if (scratchB.lengthSquared() != 0f) scratchB.normalize();
        putVertex(scratchA.x(), scratchA.y(), scratchA.z(),
                scratchB.x(), scratchB.y(), scratchB.z(), color);
        vertexBuffer.put(vertexBuffer.position() - 2, uv.x());
        vertexBuffer.put(vertexBuffer.position() - 1, uv.y());
    }

    /**
     * Checks the instance against the camera before appending its geometry. Camera
     * matrices must already be current. A null instance or camera is rejected.
     *
     * @param instance candidate model instance
     * @param camera camera used for visibility testing
     * @return true if visibility passed and model emission was invoked
     */
    public boolean modelIfVisible(ModelInstance3D instance, Camera3D camera) {
        if (instance == null || camera == null || !instance.isVisible(camera)) {
            return false;
        }
        model(instance);
        return true;
    }

    /**
     * Copies current CPU geometry into the shared GPU buffer and records its vertex
     * count for retained rendering. Empty geometry clears the retained count without
     * releasing storage. Preserves CPU data and restores the render snapshot's GL
     * state even on failure.
     *
     * @throws IllegalStateException if this batch is disposed
     */
    public void upload() {
        if (disposed) throw new IllegalStateException("MeshBatch3D is disposed");
        try (RenderStateSnapshot3D ignored = new RenderStateSnapshot3D()) {
            if (vertexCount == 0) {
                uploadedVertexCount = 0;
                return;
            }

            FloatBuffer data = vertexBuffer.duplicate().flip();
            ensureGpuCapacity(data.remaining());
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferSubData(GL_ARRAY_BUFFER, 0, data);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            uploadedVertexCount = vertexCount;
        }
    }

    /**
     * Draws the retained vertex count from the shared GPU buffer without uploading
     * CPU geometry. Prepares configured lighting even for an empty retained draw,
     * then applies the material and selected shader. Restores captured GL state
     * on return or failure.
     *
     * @param state nonnull camera, material, and lighting configuration
     * @throws IllegalStateException if disposed or a nonempty draw has no camera
     * @throws NullPointerException if state is null
     */
    public void renderUploaded(MeshRenderState3D state) {
        if (disposed) throw new IllegalStateException("MeshBatch3D is disposed");
        try (RenderStateSnapshot3D ignored = new RenderStateSnapshot3D()) {
            if (state.getLighting() != null) state.getLighting().prepare(state.getCamera());
            if (uploadedVertexCount == 0) {
                return;
            }

            bindRenderState(state);
            glBindVertexArray(vaoId);
            glDrawArrays(GL_TRIANGLES, 0, uploadedVertexCount);
            glBindVertexArray(0);
            shader.unbind();
        }
    }

    /**
     * Uploads and draws the current CPU triangles without clearing them. Prepares
     * configured lighting and restores captured GL state on return or failure.
     * This overwrites retained GPU data but does not change the retained draw count;
     * call upload again before returning to retained rendering.
     *
     * @param state nonnull camera, material, and lighting configuration
     * @throws IllegalStateException if disposed or a nonempty draw has no camera
     * @throws NullPointerException if state is null
     */
    public void render(MeshRenderState3D state) {
        if (disposed) throw new IllegalStateException("MeshBatch3D is disposed");
        try (RenderStateSnapshot3D ignored = new RenderStateSnapshot3D()) {
            if (state.getLighting() != null) state.getLighting().prepare(state.getCamera());
            renderInBatch(state);
        }
    }

    /**
     * Streams CPU vertices and draws them within a model batch's existing state
     * snapshot. The caller must prepare lighting and restore GL state; this method
     * avoids taking another snapshot for each material group. Empty geometry returns
     * without binding render state. Does not update the retained upload count.
     *
     * @param state rendering configuration for the accumulated material group
     * @throws IllegalStateException if disposed or a nonempty draw has no camera
     * @throws NullPointerException if state is null for a nonempty draw
     */
    void renderInBatch(MeshRenderState3D state) {
        if (disposed) throw new IllegalStateException("MeshBatch3D is disposed");
        if (vertexCount == 0) {
            return;
        }

        FloatBuffer data = vertexBuffer.duplicate().flip();
        ensureGpuCapacity(data.remaining());

        bindRenderState(state);
        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferSubData(GL_ARRAY_BUFFER, 0, data);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        shader.unbind();
    }

    /**
     * Marks the batch disposed and releases its owned shaders, vertex buffer, and
     * vertex array with the OpenGL context current. Repeated calls do nothing.
     * Borrowed render resources are not released; CPU storage remains referenced
     * until the batch itself becomes unreachable.
     */
    public void dispose() {
        if (disposed) return;
        disposed = true;
        compatibilityShader.dispose();
        if (lightingShader != null) lightingShader.dispose();
        if (depthShader != null) depthShader.dispose();
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }

    /**
     * Ensures CPU capacity and appends twelve floats: position, normal, color, and
     * zero UV coordinates. Increments the accumulated vertex count.
     *
     * @param x position X
     * @param y position Y
     * @param z position Z
     * @param nx normal X
     * @param ny normal Y
     * @param nz normal Z
     * @param color color components copied into the buffer
     */
    private void putVertex(float x, float y, float z, float nx, float ny, float nz, Color color) {
        ensureVertexCapacity(FLOATS_PER_VERTEX);

        vertexBuffer.put(x).put(y).put(z);
        vertexBuffer.put(nx).put(ny).put(nz);
        vertexBuffer.put(color.r()).put(color.g()).put(color.b()).put(color.a());
        vertexBuffer.put(0f).put(0f);
        vertexCount++;
    }

    /**
     * Doubles CPU buffer capacity until the requested append fits, preserving all
     * previously accumulated floats and their write position. Does not allocate or
     * resize GPU storage.
     *
     * @param additionalFloats number of floats about to be appended
     */
    private void ensureVertexCapacity(int additionalFloats) {
        if (vertexBuffer.remaining() >= additionalFloats) {
            return;
        }

        int required = vertexBuffer.position() + additionalFloats;
        int newCapacity = bufferCapacityFloats;
        while (newCapacity < required) {
            newCapacity *= 2;
        }

        FloatBuffer expanded = BufferUtils.createFloatBuffer(newCapacity);
        vertexBuffer.flip();
        expanded.put(vertexBuffer);
        vertexBuffer = expanded;
        bufferCapacityFloats = newCapacity;
    }

    /**
     * Applies material depth, culling, blending, and shader settings, choosing the
     * depth shader for shadow passes, tiled lighting when supplied, and compatibility
     * lighting otherwise. Lazily creates specialized shaders. Binds material texture
     * on unit 1, shadow depth on unit 2, and compatibility radiance on unit 0;
     * the lighting service manages its additional units.
     * <p>
     * Uploads current camera matrices without rebuilding the camera. This helper
     * mutates GL state and relies on its caller's snapshot for restoration. A null
     * material selects the shared default material.
     * </p>
     *
     * @param state nonnull render configuration
     * @throws NullPointerException if state is null
     * @throws IllegalStateException if no camera is configured
     */
    private void bindRenderState(MeshRenderState3D state) {
        if (state == null) throw new NullPointerException("state");

        Camera3D camera = state.getCamera();
        if (camera == null) {
            throw new IllegalStateException("MeshRenderState3D camera must be set before rendering");
        }

        Material3D material = state.getMaterial();
        if (material == null) {
            material = DEFAULT_MATERIAL;
        }

        if (material.isDepthTest()) {
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LEQUAL);
        } else {
            glDisable(GL_DEPTH_TEST);
        }
        glDepthMask(material.isDepthWrite());

        if (material.isCullBackFaces()) {
            glEnable(GL_CULL_FACE);
            glCullFace(GL_BACK);
        } else {
            glDisable(GL_CULL_FACE);
        }

        switch (material.getRenderPass()) {
            case OPAQUE -> glDisable(GL_BLEND);
            case TRANSLUCENT -> {
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            }
            case ADDITIVE -> {
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE);
            }
        }

        org.lwjgl.opengl.GL20.glBlendEquationSeparate(org.lwjgl.opengl.GL14.GL_FUNC_ADD, org.lwjgl.opengl.GL14.GL_FUNC_ADD);
        org.lwjgl.opengl.GL11.glFrontFace(org.lwjgl.opengl.GL11.GL_CCW);
        if (state.isShadowPass()) {
            if (depthShader == null) depthShader = new DepthShader3D();
            shader = depthShader;
        } else if (state.getLighting() != null) {
            if (lightingShader == null) lightingShader = new LightingShader3D();
            shader = lightingShader;
        } else shader = compatibilityShader;
        shader.bind();
        shader.setUniform1i("u_texture", 1);
        shader.setUniform1i("u_shadowTexture", 2);
        if (state.isShadowPass()) {
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, material.getTexture() == null ? 0 : material.getTexture().getTextureID());
            shader.setUniform1i("u_hasTexture", material.getTexture() == null ? 0 : 1);
            shader.setUniform1f("u_alphaCutoff", material.getAlphaCutoff());
            shader.setUniform4f("u_materialTint", material.getTint().r(), material.getTint().g(), material.getTint().b(), material.getTint().a());
            shader.setUniformMatrix4("u_mvp", camera.getCombined().get(matrixUpload));
            return;
        }
        if (state.getLighting() != null) {
            glDisable(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_SRGB);
            state.getLighting().bind(shader);
            shader.setUniform1f("u_roughness", material.getRoughness());
            shader.setUniform1f("u_metallic", material.getMetallic());
        }
        ShadowMap3D shadow = state.getShadowMap();
        boolean hasShadow = shadow != null && shadow.isReady() && material.isReceivesShadow() && !state.isShadowPass();
        shader.setUniform1i("u_hasShadow", hasShadow ? 1 : 0);
        org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, hasShadow ? shadow.getTextureId() : 0);
        org.lwjgl.opengl.GL33.glBindSampler(2, hasShadow && state.getLighting() != null ? shadow.getComparisonSampler() : 0);
        if (hasShadow) {
            shader.setUniformMatrix4("u_shadowMatrix", shadow.getMatrix().get(matrixUpload));
            shader.setUniform1f("u_shadowBias", shadow.getBias());
            shader.setUniform1f("u_shadowStrength", shadow.getStrength());
            shader.setUniform1f("u_shadowSoftness", shadow.getSoftness());
        }
        shader.setUniform3f("u_ambient", state.getAmbientLight().r(), state.getAmbientLight().g(), state.getAmbientLight().b());
        shader.setUniform3f("u_directional", state.getDirectionalLight().r(), state.getDirectionalLight().g(), state.getDirectionalLight().b());
        shader.setUniform3f("u_fog", state.getFogStart(), state.getFogEnd(), state.getFogAmount());
        shader.setUniform1f("u_alphaCutoff", material.getAlphaCutoff());
        shader.setUniform1i("u_pointCount", state.getPointLights().size());
        for (int i = 0; i < state.getPointLights().size(); i++) {
            PointLight3D light = state.getPointLights().get(i);
            shader.setUniform4f("u_pointPositionRange[" + i + "]", light.getPosition().x(), light.getPosition().y(), light.getPosition().z(), light.getRange());
            shader.setUniform4f("u_pointColorIntensity[" + i + "]", light.getColor().r(), light.getColor().g(), light.getColor().b(), light.getIntensity());
        }
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, material.getTexture() == null ? 0 : material.getTexture().getTextureID());
        shader.setUniform1i("u_hasTexture", material.getTexture() == null ? 0 : 1);
        shader.setUniformMatrix4(Mesh3DShader.UNIFORM_MVP, camera.getCombined().get(matrixUpload));
        shader.setUniform3f(Mesh3DShader.UNIFORM_LIGHT_DIR,
                state.getLightDirection().x(),
                state.getLightDirection().y(),
                state.getLightDirection().z()
        );
        shader.setUniform3f(Mesh3DShader.UNIFORM_CAMERA_POS,
                camera.getPosition().x(),
                camera.getPosition().y(),
                camera.getPosition().z()
        );
        shader.setUniform4f(Mesh3DShader.UNIFORM_FOG_COLOR,
                state.getFogColor().r(),
                state.getFogColor().g(),
                state.getFogColor().b(),
                state.getFogColor().a()
        );
        shader.setUniform4f(Mesh3DShader.UNIFORM_MATERIAL_TINT,
                material.getTint().r(),
                material.getTint().g(),
                material.getTint().b(),
                material.getTint().a()
        );
        shader.setUniform4f(Mesh3DShader.UNIFORM_MATERIAL_EMISSIVE,
                material.getEmissive().r(),
                material.getEmissive().g(),
                material.getEmissive().b(),
                material.getEmissive().a()
        );
        shader.setUniform1f(Mesh3DShader.UNIFORM_MATERIAL_LIGHTING_MIX, material.getLightingMix());
        shader.setUniform1f(Mesh3DShader.UNIFORM_MATERIAL_FOG_MIX, material.getFogMix());
        shader.setUniform1f(Mesh3DShader.UNIFORM_MATERIAL_RADIANCE_MIX, material.getRadianceMix());
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, state.getLightTextureId());
        shader.setUniform2f(Mesh3DShader.UNIFORM_LIGHT_WORLD_MIN, state.getLightWorldMinX(), state.getLightWorldMinY());
        shader.setUniform2f(Mesh3DShader.UNIFORM_LIGHT_WORLD_SIZE, state.getLightWorldSizeX(), state.getLightWorldSizeY());
        shader.setUniform1f(Mesh3DShader.UNIFORM_RADIANCE_STRENGTH, state.getRadianceStrength());
        shader.setUniform1i(Mesh3DShader.UNIFORM_APPLY_RADIANCE, state.isApplyRadiance() ? 1 : 0);
    }

    /**
     * Doubles GPU capacity until requiredFloats fits. Reallocating discards previous
     * GPU contents, so the caller must upload replacement geometry before drawing.
     * Leaves the array-buffer binding at zero after expansion.
     *
     * @param requiredFloats minimum GPU capacity in floats
     */
    private void ensureGpuCapacity(int requiredFloats) {
        if (requiredFloats <= gpuCapacityFloats) {
            return;
        }

        int newCapacity = gpuCapacityFloats;
        while (newCapacity < requiredFloats) {
            newCapacity *= 2;
        }
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, (long) newCapacity * Float.BYTES, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        gpuCapacityFloats = newCapacity;
    }

    /**
     * Appends flat-shaded side quads around a Z-aligned circular shell, leaving both
     * ends open. Each segment uses two adjacent angular samples at both heights.
     *
     * @param centerX axis X
     * @param centerY axis Y
     * @param lowerZ lower endpoint
     * @param upperZ upper endpoint
     * @param radius circular radius
     * @param segments circumferential subdivisions
     * @param color copied wall color
     */
    private void cylinderShell(float centerX, float centerY, float lowerZ, float upperZ,
                               float radius, int segments, Color color) {
        for (int segment = 0; segment < segments; segment++) {
            float angle0 = (float) (segment * Math.PI * 2.0 / segments);
            float angle1 = (float) ((segment + 1) * Math.PI * 2.0 / segments);

            scratchA.set(centerX + (float) Math.cos(angle0) * radius, centerY + (float) Math.sin(angle0) * radius, lowerZ);
            scratchB.set(centerX + (float) Math.cos(angle1) * radius, centerY + (float) Math.sin(angle1) * radius, lowerZ);
            scratchC.set(centerX + (float) Math.cos(angle1) * radius, centerY + (float) Math.sin(angle1) * radius, upperZ);
            scratchD.set(centerX + (float) Math.cos(angle0) * radius, centerY + (float) Math.sin(angle0) * radius, upperZ);
            quad(scratchA, scratchB, scratchC, scratchD, color);
        }
    }

    /**
     * Tessellates a latitude interval into flat-shaded quads, using the shared
     * triangle helper to discard degenerate pole faces. Nonpositive subdivision
     * counts produce no geometry.
     *
     * @param centerX sphere center X
     * @param centerY sphere center Y
     * @param centerZ sphere center Z
     * @param radius sphere radius
     * @param phiStart initial latitude in radians
     * @param phiEnd final latitude in radians
     * @param segments circumferential subdivisions
     * @param stacks latitude subdivisions
     * @param color copied vertex color
     */
    private void hemisphere(float centerX, float centerY, float centerZ, float radius,
                            float phiStart, float phiEnd, int segments, int stacks, Color color) {
        for (int stack = 0; stack < stacks; stack++) {
            float phi0 = MathUtils.lerp(phiStart, phiEnd, stack / (float) stacks);
            float phi1 = MathUtils.lerp(phiStart, phiEnd, (stack + 1f) / stacks);

            for (int segment = 0; segment < segments; segment++) {
                float angle0 = (float) (segment * Math.PI * 2.0 / segments);
                float angle1 = (float) ((segment + 1) * Math.PI * 2.0 / segments);

                spherePoint(centerX, centerY, centerZ, radius, angle0, phi0, scratchA);
                spherePoint(centerX, centerY, centerZ, radius, angle1, phi0, scratchB);
                spherePoint(centerX, centerY, centerZ, radius, angle1, phi1, scratchC);
                spherePoint(centerX, centerY, centerZ, radius, angle0, phi1, scratchD);
                quad(scratchA, scratchB, scratchC, scratchD, color);
            }
        }
    }

    /**
     * Rotates a local point about Y and adds a world-space center, writing into
     * caller-supplied storage.
     *
     * @param centerX translation X
     * @param centerY translation Y
     * @param centerZ translation Z
     * @param localX local X
     * @param localY local Y
     * @param localZ local Z
     * @param rotationY Y-axis angle in radians
     * @param out destination vector
     * @return out
     */
    private Vector3f rotatedPoint(float centerX, float centerY, float centerZ,
                                  float localX, float localY, float localZ,
                                  float rotationY, Vector3f out) {
        float cos = (float) Math.cos(rotationY);
        float sin = (float) Math.sin(rotationY);
        float rotatedX = localX * cos + localZ * sin;
        float rotatedZ = -localX * sin + localZ * cos;
        return out.set(centerX + rotatedX, centerY + localY, centerZ + rotatedZ);
    }

    /**
     * Converts spherical angles into a Z-up Cartesian point and adds the sphere
     * center. Latitude zero lies in the XY plane; longitude zero points along X.
     *
     * @param centerX center X
     * @param centerY center Y
     * @param centerZ center Z
     * @param radius radial distance
     * @param theta longitude in radians
     * @param phi latitude in radians
     * @param out destination vector
     * @return out
     */
    private Vector3f spherePoint(float centerX, float centerY, float centerZ, float radius, float theta, float phi, Vector3f out) {
        float ringRadius = (float) Math.cos(phi) * radius;
        return out.set(
                centerX + (float) Math.cos(theta) * ringRadius,
                centerY + (float) Math.sin(theta) * ringRadius,
                centerZ + (float) Math.sin(phi) * radius
        );
    }

}
