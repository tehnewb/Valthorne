package valthorne.graphics.model;

import org.lwjgl.BufferUtils;
import valthorne.camera.Camera3D;
import valthorne.graphics.Color;
import valthorne.graphics.shader.Billboard3DShader;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureRegion;
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
 * Accumulates camera-facing textured quads as world-space triangles and streams
 * them through one owned OpenGL vertex buffer. A batch uses one borrowed texture
 * and one render-state material; texture regions may vary as long as they refer
 * to that same texture object. Each vertex contains XYZ position, UV, and RGBA.
 * <p>
 * Call begin with the texture, append sprites with draw using the current camera,
 * then render with a matching camera state. Spherical billboards use camera right
 * and up; cylindrical billboards remain aligned with world Z. Geometry is computed
 * when appended, so rebuild it after camera changes. This class does not cull or
 * sort sprites; submit translucent sprites in the desired draw order.
 * </p>
 * <p>
 * Construction, rendering, and disposal require a current OpenGL context.
 * Rendering restores captured GL state; construction leaves array bindings at zero.
 * The shader, vertex array, and GPU buffer are owned; textures, sprites, cameras,
 * and materials remain borrowed. Shared scratch vectors preclude concurrent use.
 * </p>
 * <pre>{@code
 * BillboardBatch3D batch = new BillboardBatch3D();
 * try {
 *     batch.begin(sprite.getTextureRegion().getTexture());
 *     batch.draw(sprite, camera);
 *     batch.render(new MeshRenderState3D().setCamera(camera));
 * } finally {
 *     batch.dispose();
 * }
 * }</pre>
 * @author Albert Beaupre
 */
public final class BillboardBatch3D {
    private final float[] matrixUpload = new float[16]; // Reusable column-major camera matrix upload array.


    /**
     * Interleaved vertex width: XYZ position, UV coordinates, and RGBA color.
     */
    private static final int FLOATS_PER_VERTEX = 9;
    /**
     * Default initial CPU and GPU capacity in floats.
     */
    private static final int DEFAULT_INITIAL_FLOATS = 98_304;
    /**
     * Shared material fallback when the render state omits a material.
     */
    private static final Material3D DEFAULT_MATERIAL = new Material3D();
    private final Billboard3DShader shader; // Owned billboard shader program wrapper.
    private final int vaoId; // Owned OpenGL vertex-array name.
    private final int vboId; // Owned OpenGL streaming vertex-buffer name.
    private final Vector3f scratchRight = new Vector3f(); // Reusable normalized horizontal billboard axis.
    private final Vector3f scratchUp = new Vector3f(); // Reusable normalized vertical billboard axis.
    private final Vector3f scratchA = new Vector3f(); // Reusable lower-left world corner.
    private final Vector3f scratchB = new Vector3f(); // Reusable lower-right world corner.
    private final Vector3f scratchC = new Vector3f(); // Reusable upper-right world corner.
    private final Vector3f scratchD = new Vector3f(); // Reusable upper-left world corner.
    private FloatBuffer vertexBuffer; // Growable direct CPU vertex buffer positioned after appended data.
    private Texture texture; // Borrowed texture selected for the current batch.
    private int vertexCount; // Number of accumulated CPU vertices.
    private boolean disposed; // Whether GPU resource disposal has been requested.
    private int bufferCapacityFloats; // Current CPU storage capacity in floats.
    private int gpuCapacityFloats; // Current GPU storage capacity in floats.

    /**
     * Creates an empty batch with an initial capacity of 98,304 floats in CPU and
     * GPU storage, compiling the billboard shader on the current GL context.
     */
    public BillboardBatch3D() {
        this(DEFAULT_INITIAL_FLOATS);
    }

    /**
     * Allocates CPU/GPU storage and configures the nine-float vertex layout.
     * Capacity grows as sprites are appended and is measured in floats, not bytes
     * or sprite count.
     *
     * @param initialCapacityFloats capacity of at least 54 floats for one billboard
     * @throws IllegalArgumentException if capacity cannot hold one billboard
     */
    public BillboardBatch3D(int initialCapacityFloats) {
        if (initialCapacityFloats < FLOATS_PER_VERTEX * 6) {
            throw new IllegalArgumentException("initialCapacityFloats must hold at least one billboard");
        }

        this.bufferCapacityFloats = initialCapacityFloats;
        this.gpuCapacityFloats = initialCapacityFloats;
        this.vertexBuffer = BufferUtils.createFloatBuffer(initialCapacityFloats);
        this.shader = new Billboard3DShader();

        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, (long) gpuCapacityFloats * Float.BYTES, GL_STREAM_DRAW);

        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        glEnableVertexAttribArray(Billboard3DShader.ATTR_POSITION);
        glVertexAttribPointer(Billboard3DShader.ATTR_POSITION, 3, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(Billboard3DShader.ATTR_UV);
        glVertexAttribPointer(Billboard3DShader.ATTR_UV, 2, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(Billboard3DShader.ATTR_COLOR);
        glVertexAttribPointer(Billboard3DShader.ATTR_COLOR, 4, GL_FLOAT, false, stride, 5L * Float.BYTES);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    /**
     * Selects a borrowed texture and clears previously accumulated CPU vertices.
     * Does not draw or dispose the previous texture. Call again when changing
     * texture groups or rebuilding camera-dependent geometry.
     *
     * @param texture nonnull shared texture for subsequent sprites
     * @throws NullPointerException if texture is null
     * @throws IllegalStateException if this batch is disposed
     */
    public void begin(Texture texture) {
        if (disposed) throw new IllegalStateException("BillboardBatch3D is disposed");
        if (texture == null) throw new NullPointerException("texture");
        this.texture = texture;
        vertexBuffer.clear();
        vertexCount = 0;
    }

    /**
     * Appends two triangles using the sprite's size, anchor, color, region UVs,
     * position, and billboard mode. Copies values immediately without retaining
     * the sprite. Null sprite/camera/region values emit nothing; valid sprites
     * require begin and an identical texture reference. Does not check visibility.
     *
     * @param billboard sprite to append
     * @param camera camera whose current orientation determines the quad basis
     * @throws IllegalStateException if no texture has been selected
     * @throws IllegalArgumentException if the sprite region uses a different texture object
     */
    public void draw(BillboardSprite3D billboard, Camera3D camera) {
        if (billboard == null || camera == null || billboard.getTextureRegion() == null) {
            return;
        }
        if (texture == null) {
            throw new IllegalStateException("BillboardBatch3D.begin must be called before draw");
        }
        if (billboard.getTextureRegion().getTexture() != texture) {
            throw new IllegalArgumentException("Billboard texture does not match current batch texture");
        }

        computeBasis(camera, billboard.getMode());

        float width = billboard.getWidth();
        float height = billboard.getHeight();
        float left = -billboard.getAnchorX() * width;
        float right = left + width;
        float bottom = -billboard.getAnchorY() * height;
        float top = bottom + height;

        Vector3f position = billboard.getPosition();
        setCorner(scratchA, position, left, bottom);
        setCorner(scratchB, position, right, bottom);
        setCorner(scratchC, position, right, top);
        setCorner(scratchD, position, left, top);

        TextureRegion region = billboard.getTextureRegion();
        Color color = billboard.getColor();
        float u0 = region.getU();
        float v0 = region.getV();
        float u1 = region.getU2();
        float v1 = region.getV2();

        putVertex(scratchA, u0, v1, color);
        putVertex(scratchB, u1, v1, color);
        putVertex(scratchC, u1, v0, color);
        putVertex(scratchA, u0, v1, color);
        putVertex(scratchC, u1, v0, color);
        putVertex(scratchD, u0, v0, color);
    }

    /**
     * Streams and draws all accumulated vertices without clearing them. A batch
     * with no vertices or no selected texture returns without using the state.
     * Restores the bindings and capabilities covered by the render snapshot on
     * success or failure.
     *
     * @param state material, camera, fog, and radiance configuration
     * @throws IllegalStateException if disposed or a nonempty draw has no camera
     * @throws NullPointerException if state is null for a nonempty draw
     */
    public void render(MeshRenderState3D state) {
        if (disposed) throw new IllegalStateException("BillboardBatch3D is disposed");
        try (RenderStateSnapshot3D ignored = new RenderStateSnapshot3D()) {
            if (vertexCount == 0 || texture == null) {
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
    }

    /**
     * Marks the batch disposed and deletes its shader, vertex buffer, and vertex
     * array. Repeated calls have no effect. Call with the appropriate context current;
     * the borrowed texture is not disposed.
     */
    public void dispose() {
        if (disposed) return;
        disposed = true;
        shader.dispose();
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }

    /**
     * Writes center plus horizontal/right and vertical/up offsets using the
     * basis computed for the current sprite.
     *
     * @param out destination corner
     * @param center world-space sprite anchor position
     * @param horizontal horizontal offset in world units
     * @param vertical vertical offset in world units
     */
    private void setCorner(Vector3f out, Vector3f center, float horizontal, float vertical) {
        out.set(center)
                .add(scratchRight.x() * horizontal, scratchRight.y() * horizontal, scratchRight.z() * horizontal)
                .add(scratchUp.x() * vertical, scratchUp.y() * vertical, scratchUp.z() * vertical);
    }

    /**
     * Updates reusable quad axes. Spherical mode normalizes camera right and up.
     * Other modes project camera right onto XY, fall back to a perpendicular camera
     * direction and then positive X when degenerate, and use positive Z as up.
     *
     * @param camera current camera orientation
     * @param mode spherical or world-Z-aligned orientation mode
     */
    private void computeBasis(Camera3D camera, BillboardMode3D mode) {
        if (mode == BillboardMode3D.SPHERICAL) {
            scratchRight.set(camera.getRight()).normalize();
            scratchUp.set(camera.getUp()).normalize();
            return;
        }

        scratchRight.set(camera.getRight().x(), camera.getRight().y(), 0f);
        if (scratchRight.lengthSquared() < 1e-8f) {
            scratchRight.set(-camera.getDirection().y(), camera.getDirection().x(), 0f);
        }
        if (scratchRight.lengthSquared() < 1e-8f) {
            scratchRight.set(1f, 0f, 0f);
        }
        scratchRight.normalize();
        scratchUp.set(0f, 0f, 1f);
    }

    /**
     * Appends one nine-float vertex and increments the CPU vertex count, growing
     * storage as needed. Position and color components are copied immediately.
     *
     * @param position world-space vertex
     * @param u horizontal texture coordinate
     * @param v vertical texture coordinate
     * @param color vertex color
     */
    private void putVertex(Vector3f position, float u, float v, Color color) {
        ensureVertexCapacity(FLOATS_PER_VERTEX);
        vertexBuffer.put(position.x()).put(position.y()).put(position.z());
        vertexBuffer.put(u).put(v);
        vertexBuffer.put(color.r()).put(color.g()).put(color.b()).put(color.a());
        vertexCount++;
    }

    /**
     * Doubles CPU capacity until the requested append fits, copying existing data
     * and preserving the write position. GPU allocation is deferred until rendering.
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
     * Applies material depth, culling, and blending policy, then uploads camera,
     * tint, emission, fog, and radiance uniforms. Binds the sprite texture on unit
     * zero and the world light texture on unit one. Uses the shared default material
     * when none is supplied and relies on the caller's snapshot for restoration.
     *
     * @param state nonnull rendering configuration with a current camera
     * @throws NullPointerException if state is null
     * @throws IllegalStateException if the camera is missing
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
        shader.bind();
        shader.setUniform3f("u_fog", state.getFogStart(), state.getFogEnd(), state.getFogAmount());
        shader.setUniform1f("u_alphaCutoff", material.getAlphaCutoff());
        shader.setUniformMatrix4(Billboard3DShader.UNIFORM_MVP, camera.getCombined().get(matrixUpload));
        shader.setUniform3f(Billboard3DShader.UNIFORM_CAMERA_POS,
                camera.getPosition().x(),
                camera.getPosition().y(),
                camera.getPosition().z()
        );
        shader.setUniform4f(Billboard3DShader.UNIFORM_FOG_COLOR,
                state.getFogColor().r(),
                state.getFogColor().g(),
                state.getFogColor().b(),
                state.getFogColor().a()
        );
        shader.setUniform4f(Billboard3DShader.UNIFORM_MATERIAL_TINT,
                material.getTint().r(),
                material.getTint().g(),
                material.getTint().b(),
                material.getTint().a()
        );
        shader.setUniform4f(Billboard3DShader.UNIFORM_MATERIAL_EMISSIVE,
                material.getEmissive().r(),
                material.getEmissive().g(),
                material.getEmissive().b(),
                material.getEmissive().a()
        );
        shader.setUniform1f(Billboard3DShader.UNIFORM_MATERIAL_FOG_MIX, material.getFogMix());
        shader.setUniform1f(Billboard3DShader.UNIFORM_MATERIAL_RADIANCE_MIX, material.getRadianceMix());

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture.getTextureID());

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, state.getLightTextureId());
        shader.setUniform2f(Billboard3DShader.UNIFORM_LIGHT_WORLD_MIN, state.getLightWorldMinX(), state.getLightWorldMinY());
        shader.setUniform2f(Billboard3DShader.UNIFORM_LIGHT_WORLD_SIZE, state.getLightWorldSizeX(), state.getLightWorldSizeY());
        shader.setUniform1f(Billboard3DShader.UNIFORM_RADIANCE_STRENGTH, state.getRadianceStrength());
        shader.setUniform1i(Billboard3DShader.UNIFORM_APPLY_RADIANCE, state.isApplyRadiance() ? 1 : 0);
        glActiveTexture(GL_TEXTURE0);
    }

    /**
     * Doubles GPU storage when the required float count exceeds capacity. Reallocation
     * discards previous GPU data; rendering immediately uploads replacement vertices.
     *
     * @param requiredFloats minimum capacity in floats
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
        glBufferData(GL_ARRAY_BUFFER, (long) newCapacity * Float.BYTES, GL_STREAM_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        gpuCapacityFloats = newCapacity;
    }
}
