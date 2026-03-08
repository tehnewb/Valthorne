package valthorne.graphics.texture;

import org.lwjgl.BufferUtils;
import valthorne.graphics.Color;
import valthorne.graphics.shader.Shader;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.GL_MAX_TEXTURE_IMAGE_UNITS;
import static org.lwjgl.opengl.GL20.glDisableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL31.glDrawArraysInstanced;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;

/**
 * <h1>TextureBatch</h1>
 *
 * <p>
 * {@code TextureBatch} is a high-performance 2D sprite batcher built around instanced rendering.
 * It allows many textured quads to be queued on the CPU and submitted to the GPU in large grouped
 * draw calls instead of issuing one draw call per sprite. This is especially useful for UI systems,
 * bitmap font rendering, tile maps, sprites, and other 2D render workloads where large numbers of
 * quads are drawn every frame.
 * </p>
 *
 * <p>
 * The batch stores per-instance data for each queued quad in a reusable {@link FloatBuffer}. Each
 * instance contains world-space position and size, tint color, texture unit selection, UV rectangle,
 * and optional clip rectangle data. A single static quad mesh is reused for every sprite, while
 * instancing attributes provide the per-sprite variation. This keeps the CPU-side draw path small
 * and efficient.
 * </p>
 *
 * <h2>How this batch works</h2>
 *
 * <ul>
 *     <li>A static quad VBO stores the six vertices needed for two triangles.</li>
 *     <li>A dynamic instance VBO stores one packed record per sprite.</li>
 *     <li>{@link #drawUV(Texture, float, float, float, float, float, float, float, float, Color)}
 *     writes one sprite instance into the reusable CPU buffer.</li>
 *     <li>{@link #flush()} uploads queued instances and submits them in one instanced draw call.</li>
 *     <li>Textures are assigned to texture units dynamically and reused within the batch until a flush is needed.</li>
 * </ul>
 *
 * <h2>Shader model</h2>
 *
 * <p>
 * This batch owns a default shader but also supports temporarily switching to a custom shader.
 * The active shader must respect the same attribute layout used by the batch contract because the
 * batch binds its vertex attributes using {@link TextureBatchContract}. If a custom shader is used
 * while drawing, the batch is flushed before the shader switch to preserve correctness.
 * </p>
 *
 * <h2>Clipping model</h2>
 *
 * <p>
 * Instead of relying on OpenGL scissor changes for every nested UI clip, this batch stores clip
 * rectangles per sprite instance. The active shader discards fragments outside the assigned clip
 * rectangle when clipping is enabled. This allows nested clipping without repeatedly forcing flushes.
 * </p>
 *
 * <h2>Framebuffer support</h2>
 *
 * <p>
 * The batch may render directly to the current framebuffer or to a supplied {@link FrameBuffer}.
 * When rendering into an FBO, the previous framebuffer binding and viewport are captured and
 * restored automatically at {@link #end()}.
 * </p>
 *
 * <h2>Usage example</h2>
 *
 * <pre>{@code
 * TextureBatch batch = new TextureBatch(4096, 16);
 *
 * batch.begin();
 * batch.setColor(1f, 1f, 1f, 1f);
 *
 * batch.draw(playerTexture, 100f, 80f, 64f, 64f);
 * batch.drawRegion(atlasRegion, 200f, 120f, 32f, 32f);
 *
 * batch.beginScissor(50f, 50f, 300f, 200f);
 * batch.draw(uiTexture, 60f, 60f, 128f, 48f);
 * batch.endScissor();
 *
 * batch.end();
 *
 * batch.dispose();
 * }</pre>
 *
 * <h2>Custom shader example</h2>
 *
 * <pre>{@code
 * Shader glowShader = new Shader(customVertexSource, customFragmentSource);
 * TextureBatchContract.bindAttributes(glowShader);
 * glowShader.reload();
 * TextureBatchContract.bindSamplers(glowShader, 16);
 *
 * batch.begin();
 * batch.setShader(glowShader);
 * batch.draw(texture, 10f, 10f, 64f, 64f);
 * batch.setShader(null);
 * batch.end();
 * }</pre>
 *
 * @author Albert Beaupre
 * @since February 8th, 2026
 */
public final class TextureBatch {

    /**
     * The CPU-side instance buffer that stores packed per-sprite data before upload.
     */
    private final FloatBuffer instanceBuffer; // Reusable CPU buffer used to stage per-instance sprite data.

    private final Color color = new Color(1f, 1f, 1f, 1f); // The default tint color used when a draw call does not provide one.
    private final Shader defaultShader; // The built-in shader used when no custom shader is assigned.
    private Shader customShader; // The optional user-supplied shader that overrides the default shader.
    private Shader activeShader; // The shader currently bound for rendering during an active batch.
    private final int maxSprites; // The maximum number of sprite instances that may be queued before forcing a flush.
    private final int maxTextureUnits; // The hard upper bound on simultaneous texture units supported by this batch.
    private final int quadVBO; // The OpenGL buffer id that stores the static shared quad geometry.
    private final int instanceVBO; // The OpenGL buffer id that stores uploaded per-instance data.
    private int[] textureIDs; // The currently assigned texture id for each active texture unit slot.
    private final int[] vp = new int[4]; // Temporary viewport storage used when capturing and restoring framebuffer state.
    private final int[] previousScissorBox = new int[4]; // The previously active OpenGL scissor rectangle captured before batch rendering.
    private final float[] clipStack = new float[256 * 4]; // The nested clip stack storing x, y, width, and height for each clip level.

    private int textureUnitCapacity; // The currently allocated size of the texture unit id array.
    private int activeTextureCount; // The number of texture units currently used by the active batch contents.
    private int lastTextureID = -1; // The last texture id looked up or bound, used as a small cache.
    private int lastTextureUnit = -1; // The texture unit index associated with the cached last texture id.

    private int instanceCount; // The number of queued sprite instances currently waiting to be flushed.
    private boolean drawing; // Whether begin() has been called and the batch is currently active.

    private boolean usingFBO; // Whether the current begin/end block is rendering into a framebuffer.
    private int previousFBO; // The framebuffer binding that was active before begin(FrameBuffer) changed it.
    private int prevViewportX; // The previous viewport x coordinate captured before framebuffer rendering began.
    private int prevViewportY; // The previous viewport y coordinate captured before framebuffer rendering began.
    private int prevViewportW; // The previous viewport width captured before framebuffer rendering began.
    private int prevViewportH; // The previous viewport height captured before framebuffer rendering began.

    private int clipDepth; // The current depth of nested clip rectangles.
    private boolean clipEnabled; // Whether clipping is currently active for queued draw calls.
    private float clipX; // The current effective clip rectangle x coordinate.
    private float clipY; // The current effective clip rectangle y coordinate.
    private float clipW; // The current effective clip rectangle width.
    private float clipH; // The current effective clip rectangle height.

    private boolean scissorEnabledBeforeBegin; // Whether OpenGL scissor testing was enabled before this batch began rendering.

    /**
     * Creates a batch with the given maximum sprite count and an automatically detected texture unit limit.
     *
     * <p>
     * This constructor queries {@code GL_MAX_TEXTURE_IMAGE_UNITS}, clamps it to a safe upper bound,
     * and uses that value as the maximum simultaneous texture unit count for the batch.
     * </p>
     *
     * @param maxSprites the maximum number of sprites that may be queued before a flush is required
     */
    public TextureBatch(int maxSprites) {
        this(maxSprites, detectTextureUnitsClamped(16));
    }

    /**
     * Creates a batch with an explicit sprite capacity and texture unit limit.
     *
     * <p>
     * This constructor allocates the reusable CPU-side instance buffer, creates the static quad VBO,
     * creates the dynamic instance VBO, builds and initializes the default shader, and prepares the
     * texture unit bookkeeping array.
     * </p>
     *
     * <p>
     * The texture unit array begins at a smaller working size and grows on demand up to
     * {@code maxTextureUnits}. This avoids scanning a large fixed array every time when only a few
     * textures are actually used in a given batch.
     * </p>
     *
     * @param maxSprites      the maximum number of sprite instances that may be queued
     * @param maxTextureUnits the maximum number of texture units this batch may use at once
     * @throws IllegalArgumentException if {@code maxSprites <= 0} or {@code maxTextureUnits < 2}
     */
    public TextureBatch(int maxSprites, int maxTextureUnits) {
        if (maxSprites <= 0) {
            throw new IllegalArgumentException("maxSprites must be > 0");
        }
        if (maxTextureUnits < 2) {
            throw new IllegalArgumentException("maxTextureUnits must be >= 2");
        }

        this.maxSprites = maxSprites;
        this.maxTextureUnits = Math.min(maxTextureUnits, 16);

        this.textureUnitCapacity = Math.min(4, this.maxTextureUnits);
        this.textureIDs = new int[this.textureUnitCapacity];
        Arrays.fill(this.textureIDs, -1);

        this.instanceBuffer = BufferUtils.createFloatBuffer(this.maxSprites * TextureBatchContract.INST_FLOATS);

        this.defaultShader = new Shader(TextureBatchContract.defaultVertexShader(), TextureBatchContract.buildDefaultFragmentShader(this.maxTextureUnits));
        TextureBatchContract.bindAttributes(this.defaultShader);
        this.defaultShader.reload();
        TextureBatchContract.bindSamplers(this.defaultShader, this.maxTextureUnits);

        this.activeShader = this.defaultShader;

        FloatBuffer quad = BufferUtils.createFloatBuffer(TextureBatchContract.QUAD_VERTS * TextureBatchContract.QUAD_FLOATS_PER_VERT);

        quad.put(0f).put(0f).put(0f).put(0f);
        quad.put(1f).put(0f).put(1f).put(0f);
        quad.put(1f).put(1f).put(1f).put(1f);

        quad.put(1f).put(1f).put(1f).put(1f);
        quad.put(0f).put(1f).put(0f).put(1f);
        quad.put(0f).put(0f).put(0f).put(0f);

        quad.flip();

        quadVBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);
        glBufferData(GL_ARRAY_BUFFER, quad, GL_STATIC_DRAW);

        instanceVBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
        glBufferData(GL_ARRAY_BUFFER, (long) this.maxSprites * TextureBatchContract.INST_STRIDE_BYTES, GL_STREAM_DRAW);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * Returns the currently selected shader.
     *
     * <p>
     * If a custom shader has been assigned, it is returned. Otherwise the internal default shader is
     * returned.
     * </p>
     *
     * @return the shader that the batch will currently use
     */
    public Shader getShader() {
        return customShader != null ? customShader : defaultShader;
    }

    /**
     * Returns the built-in default shader owned by this batch.
     *
     * @return the default shader instance
     */
    public Shader getDefaultShader() {
        return defaultShader;
    }

    /**
     * Sets the shader used by this batch.
     *
     * <p>
     * Passing {@code null} restores the batch to its default shader. Passing the same instance as the
     * default shader is also treated as restoring the default shader. If the batch is currently
     * drawing, it is flushed before the shader switch so that already queued instances render using
     * the old shader and later instances render using the new shader.
     * </p>
     *
     * @param shader the custom shader to use, or null to restore the default shader
     */
    public void setShader(Shader shader) {
        if (shader == defaultShader) {
            shader = null;
        }

        if (drawing) {
            flush();
            if (activeShader != null) {
                activeShader.unbind();
            }
        }

        customShader = shader;
        activeShader = customShader != null ? customShader : defaultShader;

        if (drawing) {
            bindActiveShaderState();
        }
    }

    /**
     * Sets the default batch tint using the provided color object.
     *
     * <p>
     * This color is used for draw calls that do not explicitly pass their own tint.
     * </p>
     *
     * @param tint the new default tint color
     * @throws NullPointerException if {@code tint} is null
     */
    public void setColor(Color tint) {
        Objects.requireNonNull(tint, "Color cannot be null");
        color.set(tint);
    }

    /**
     * Sets the default batch tint using raw color components.
     *
     * @param r the red component
     * @param g the green component
     * @param b the blue component
     * @param a the alpha component
     */
    public void setColor(float r, float g, float b, float a) {
        color.r(r);
        color.g(g);
        color.b(b);
        color.a(a);
    }

    /**
     * Pushes a new scissor-like clip rectangle onto the batch clip stack.
     *
     * <p>
     * Unlike traditional OpenGL scissor changes, this method does not immediately alter GPU scissor
     * state. Instead it computes the effective clip rectangle by intersecting with the current clip
     * if needed, stores the result on the clip stack, and causes subsequent queued instances to carry
     * that clip rectangle in their per-instance data.
     * </p>
     *
     * @param x      the clip x coordinate
     * @param y      the clip y coordinate
     * @param width  the clip width
     * @param height the clip height
     * @throws IllegalStateException if the batch is not drawing or the clip stack overflows
     */
    public void beginScissor(float x, float y, float width, float height) {
        if (!drawing) {
            throw new IllegalStateException("Call begin() before beginScissor().");
        }
        if ((clipDepth + 1) * 4 > clipStack.length) {
            throw new IllegalStateException("Scissor stack overflow.");
        }

        float fx = x;
        float fy = y;
        float fw = Math.max(0f, width);
        float fh = Math.max(0f, height);

        if (clipDepth > 0) {
            int i = (clipDepth - 1) * 4;

            float px = clipStack[i];
            float py = clipStack[i + 1];
            float pw = clipStack[i + 2];
            float ph = clipStack[i + 3];

            float pr = px + pw;
            float pt = py + ph;
            float nr = fx + fw;
            float nt = fy + fh;

            float ix0 = Math.max(px, fx);
            float iy0 = Math.max(py, fy);
            float ix1 = Math.min(pr, nr);
            float iy1 = Math.min(pt, nt);

            fx = ix0;
            fy = iy0;
            fw = Math.max(0f, ix1 - ix0);
            fh = Math.max(0f, iy1 - iy0);
        }

        int index = clipDepth * 4;
        clipStack[index] = fx;
        clipStack[index + 1] = fy;
        clipStack[index + 2] = fw;
        clipStack[index + 3] = fh;
        clipDepth++;

        clipEnabled = true;
        clipX = fx;
        clipY = fy;
        clipW = fw;
        clipH = fh;
    }

    /**
     * Pops the most recently applied clip rectangle from the clip stack.
     *
     * <p>
     * If another clip rectangle remains below it, that rectangle becomes the active clip. Otherwise
     * clipping is disabled for future queued instances.
     * </p>
     *
     * @throws IllegalStateException if the batch is not drawing or no clip is active
     */
    public void endScissor() {
        if (!drawing) {
            throw new IllegalStateException("Call begin() before endScissor().");
        }
        if (clipDepth <= 0) {
            throw new IllegalStateException("No active scissor to end.");
        }

        clipDepth--;

        if (clipDepth > 0) {
            int index = (clipDepth - 1) * 4;
            clipEnabled = true;
            clipX = clipStack[index];
            clipY = clipStack[index + 1];
            clipW = clipStack[index + 2];
            clipH = clipStack[index + 3];
        } else {
            clipEnabled = false;
            clipX = 0f;
            clipY = 0f;
            clipW = 0f;
            clipH = 0f;
        }
    }

    /**
     * Begins rendering to the currently bound framebuffer.
     *
     * <p>
     * This is a convenience overload for {@link #begin(FrameBuffer)} using a null framebuffer.
     * </p>
     */
    public void begin() {
        begin(null);
    }

    /**
     * Begins a new batch rendering session.
     *
     * <p>
     * This method prepares the batch for rendering by optionally switching to the supplied
     * framebuffer, capturing the prior framebuffer and viewport state, resetting clip and texture
     * state, disabling OpenGL scissor testing if it was active, clearing the CPU instance buffer,
     * and binding the active shader plus vertex attribute state.
     * </p>
     *
     * @param fbo the framebuffer to render into, or null to render into the currently bound target
     * @throws IllegalStateException if the batch is already drawing
     */
    public void begin(FrameBuffer fbo) {
        if (drawing) {
            throw new IllegalStateException("Already drawing.");
        }
        drawing = true;

        usingFBO = fbo != null;

        if (usingFBO) {
            previousFBO = glGetInteger(GL_FRAMEBUFFER_BINDING);

            glGetIntegerv(GL_VIEWPORT, vp);
            prevViewportX = vp[0];
            prevViewportY = vp[1];
            prevViewportW = vp[2];
            prevViewportH = vp[3];

            if (prevViewportW <= 0 || prevViewportH <= 0) {
                throw new IllegalStateException("GL_VIEWPORT returned invalid size: " + prevViewportW + "x" + prevViewportH);
            }

            if (fbo.getWidth() != prevViewportW || fbo.getHeight() != prevViewportH) {
                fbo.resize(prevViewportW, prevViewportH);
            }

            glBindFramebuffer(GL_FRAMEBUFFER, fbo.getFrameBufferId());
            glViewport(0, 0, fbo.getWidth(), fbo.getHeight());
        }

        clipDepth = 0;
        clipEnabled = false;
        clipX = 0f;
        clipY = 0f;
        clipW = 0f;
        clipH = 0f;

        scissorEnabledBeforeBegin = glIsEnabled(GL_SCISSOR_TEST);
        if (scissorEnabledBeforeBegin) {
            glGetIntegerv(GL_SCISSOR_BOX, previousScissorBox);
            glDisable(GL_SCISSOR_TEST);
        }

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        instanceCount = 0;
        instanceBuffer.clear();

        activeTextureCount = 0;
        lastTextureID = -1;
        lastTextureUnit = -1;

        Arrays.fill(textureIDs, 0, textureUnitCapacity, -1);

        activeShader = customShader != null ? customShader : defaultShader;
        bindActiveShaderState();
    }

    /**
     * Queues a texture using its own stored transform and the current batch color.
     *
     * @param tex the texture to draw
     */
    public void draw(Texture tex) {
        draw(tex, null);
    }

    /**
     * Queues a texture using its own stored transform and an optional tint.
     *
     * @param tex  the texture to draw
     * @param tint the tint to use, or null to use the current batch tint
     */
    public void draw(Texture tex, Color tint) {
        Objects.requireNonNull(tex, "Texture cannot be null");
        drawUV(tex, tex.getX(), tex.getY(), tex.getWidth(), tex.getHeight(), tex.leftRegion, tex.topRegion, tex.rightRegion, tex.bottomRegion, tint);
    }

    /**
     * Queues a full-texture quad using the current batch tint.
     *
     * @param tex the texture to draw
     * @param x   the destination x coordinate
     * @param y   the destination y coordinate
     * @param w   the destination width
     * @param h   the destination height
     */
    public void draw(Texture tex, float x, float y, float w, float h) {
        drawUV(tex, x, y, w, h, 0f, 0f, 1f, 1f, null);
    }

    /**
     * Queues a full-texture quad using an optional tint.
     *
     * @param tex  the texture to draw
     * @param x    the destination x coordinate
     * @param y    the destination y coordinate
     * @param w    the destination width
     * @param h    the destination height
     * @param tint the tint to use, or null to use the current batch tint
     */
    public void draw(Texture tex, float x, float y, float w, float h, Color tint) {
        drawUV(tex, x, y, w, h, 0f, 0f, 1f, 1f, tint);
    }

    /**
     * Queues a textured quad using explicit normalized UV coordinates and the current batch tint.
     *
     * @param tex the texture to draw
     * @param x   the destination x coordinate
     * @param y   the destination y coordinate
     * @param w   the destination width
     * @param h   the destination height
     * @param u0  the left u coordinate
     * @param v0  the bottom or top v coordinate depending on the texture convention
     * @param u1  the right u coordinate
     * @param v1  the opposite v coordinate
     */
    public void draw(Texture tex, float x, float y, float w, float h, float u0, float v0, float u1, float v1) {
        drawUV(tex, x, y, w, h, u0, v0, u1, v1, null);
    }

    /**
     * Queues a rectangular pixel region from a texture using the current batch tint.
     *
     * @param tex          the texture to sample
     * @param x            the destination x coordinate
     * @param y            the destination y coordinate
     * @param w            the destination width
     * @param h            the destination height
     * @param regionX      the source region x coordinate in pixels
     * @param regionY      the source region y coordinate in pixels
     * @param regionWidth  the source region width in pixels
     * @param regionHeight the source region height in pixels
     */
    public void drawRegion(Texture tex, float x, float y, float w, float h, float regionX, float regionY, float regionWidth, float regionHeight) {
        drawRegion(tex, x, y, w, h, regionX, regionY, regionWidth, regionHeight, null);
    }

    /**
     * Queues a rectangular pixel region from a texture using an optional tint.
     *
     * <p>
     * The supplied pixel region is converted into normalized UV coordinates using the texture's
     * underlying {@link TextureData}. If texture data is unavailable or reports zero size, the
     * method falls back to drawing the full texture.
     * </p>
     *
     * @param tex          the texture to sample
     * @param x            the destination x coordinate
     * @param y            the destination y coordinate
     * @param w            the destination width
     * @param h            the destination height
     * @param regionX      the source region x coordinate in pixels
     * @param regionY      the source region y coordinate in pixels
     * @param regionWidth  the source region width in pixels
     * @param regionHeight the source region height in pixels
     * @param tint         the tint to use, or null to use the current batch tint
     */
    public void drawRegion(Texture tex, float x, float y, float w, float h, float regionX, float regionY, float regionWidth, float regionHeight, Color tint) {
        Objects.requireNonNull(tex, "Texture cannot be null");

        TextureData d = tex.getData();
        if (d == null) {
            drawUV(tex, x, y, w, h, 0f, 0f, 1f, 1f, tint);
            return;
        }

        float texW = d.width();
        float texH = d.height();

        if (texW == 0f || texH == 0f) {
            drawUV(tex, x, y, w, h, 0f, 0f, 1f, 1f, tint);
            return;
        }

        float u0 = regionX / texW;
        float v0 = regionY / texH;
        float u1 = (regionX + regionWidth) / texW;
        float v1 = (regionY + regionHeight) / texH;

        drawUV(tex, x, y, w, h, u0, v0, u1, v1, tint);
    }

    /**
     * Queues a {@link TextureRegion} using its own stored destination transform.
     *
     * @param region the texture region to draw
     */
    public void drawRegion(TextureRegion region) {
        Objects.requireNonNull(region, "TextureRegion cannot be null");
        drawRegion(region, region.getX(), region.getY(), region.getWidth(), region.getHeight(), region.getRegionX(), region.getRegionY(), region.getRegionWidth(), region.getRegionHeight(), null);
    }

    /**
     * Queues a {@link TextureRegion} using a custom destination rectangle and the current batch tint.
     *
     * @param region the texture region to draw
     * @param x      the destination x coordinate
     * @param y      the destination y coordinate
     * @param w      the destination width
     * @param h      the destination height
     */
    public void drawRegion(TextureRegion region, float x, float y, float w, float h) {
        Objects.requireNonNull(region, "TextureRegion cannot be null");
        drawRegion(region, x, y, w, h, region.getRegionX(), region.getRegionY(), region.getRegionWidth(), region.getRegionHeight(), null);
    }

    /**
     * Queues a {@link TextureRegion} using a custom destination rectangle and an optional tint.
     *
     * @param region the texture region to draw
     * @param x      the destination x coordinate
     * @param y      the destination y coordinate
     * @param w      the destination width
     * @param h      the destination height
     * @param tint   the tint to use, or null to use the current batch tint
     */
    public void drawRegion(TextureRegion region, float x, float y, float w, float h, Color tint) {
        Objects.requireNonNull(region, "TextureRegion cannot be null");
        drawRegion(region, x, y, w, h, region.getRegionX(), region.getRegionY(), region.getRegionWidth(), region.getRegionHeight(), tint);
    }

    /**
     * Queues one sprite instance using explicit UV coordinates and an optional tint.
     *
     * <p>
     * This is the main low-level draw path used by the rest of the batch helpers. It ensures that
     * the batch is active, resolves or binds a texture unit for the texture, writes packed instance
     * data into the CPU buffer, and stores the current clip state if clipping is enabled.
     * </p>
     *
     * @param tex  the texture to draw
     * @param x    the destination x coordinate
     * @param y    the destination y coordinate
     * @param w    the destination width
     * @param h    the destination height
     * @param u0   the left u coordinate
     * @param v0   the first v coordinate
     * @param u1   the right u coordinate
     * @param v1   the second v coordinate
     * @param tint the tint to use, or null to use the current batch tint
     * @throws IllegalStateException if the batch is not currently drawing
     * @throws NullPointerException  if {@code tex} is null
     */
    public void drawUV(Texture tex, float x, float y, float w, float h, float u0, float v0, float u1, float v1, Color tint) {
        if (!drawing) {
            throw new IllegalStateException("Call begin() before draw().");
        }
        Objects.requireNonNull(tex, "Texture cannot be null");

        if (instanceCount >= maxSprites) {
            flush();
        }

        int texUnit = getOrBindTextureUnit(tex.getTextureID());
        if (texUnit < 0) {
            flush();
            texUnit = getOrBindTextureUnit(tex.getTextureID());
            if (texUnit < 0) {
                return;
            }
        }

        float r = color.r();
        float g = color.g();
        float b = color.b();
        float a = color.a();

        if (tint != null) {
            r = tint.r();
            g = tint.g();
            b = tint.b();
            a = tint.a();
        }

        instanceBuffer.put(x).put(y).put(w).put(h);
        instanceBuffer.put(r).put(g).put(b).put(a);
        instanceBuffer.put((float) texUnit);
        instanceBuffer.put(u0).put(v0).put(u1).put(v1);

        if (clipEnabled) {
            instanceBuffer.put(clipX).put(clipY).put(clipW).put(clipH);
            instanceBuffer.put(1f);
        } else {
            instanceBuffer.put(0f).put(0f).put(0f).put(0f);
            instanceBuffer.put(0f);
        }

        instanceCount++;
    }

    /**
     * Ends the active batch and restores previously captured GL state.
     *
     * <p>
     * This flushes any remaining instances, resets divisors and enabled vertex attributes,
     * unbinds textures, restores scissor state, restores the previous framebuffer and viewport if
     * rendering into an FBO, and marks the batch as no longer drawing.
     * </p>
     *
     * @throws IllegalStateException if the batch is not currently drawing
     */
    public void end() {
        if (!drawing) {
            throw new IllegalStateException("Call begin() before end().");
        }

        flush();

        glVertexAttribDivisor(TextureBatchContract.ATTR_XYWH, 0);
        glVertexAttribDivisor(TextureBatchContract.ATTR_COL, 0);
        glVertexAttribDivisor(TextureBatchContract.ATTR_TEX, 0);
        glVertexAttribDivisor(TextureBatchContract.ATTR_UVRECT, 0);
        glVertexAttribDivisor(TextureBatchContract.ATTR_CLIPRECT, 0);
        glVertexAttribDivisor(TextureBatchContract.ATTR_CLIPENABLED, 0);

        glDisableVertexAttribArray(TextureBatchContract.ATTR_LOCAL);
        glDisableVertexAttribArray(TextureBatchContract.ATTR_UV);
        glDisableVertexAttribArray(TextureBatchContract.ATTR_XYWH);
        glDisableVertexAttribArray(TextureBatchContract.ATTR_COL);
        glDisableVertexAttribArray(TextureBatchContract.ATTR_TEX);
        glDisableVertexAttribArray(TextureBatchContract.ATTR_UVRECT);
        glDisableVertexAttribArray(TextureBatchContract.ATTR_CLIPRECT);
        glDisableVertexAttribArray(TextureBatchContract.ATTR_CLIPENABLED);

        activeShader.unbind();
        drawing = false;

        for (int i = 0; i < activeTextureCount; i++) {
            glActiveTexture(GL_TEXTURE0 + i);
            glBindTexture(GL_TEXTURE_2D, 0);
        }
        glActiveTexture(GL_TEXTURE0);

        activeTextureCount = 0;
        lastTextureID = -1;
        lastTextureUnit = -1;

        if (scissorEnabledBeforeBegin) {
            glEnable(GL_SCISSOR_TEST);
            glScissor(previousScissorBox[0], previousScissorBox[1], previousScissorBox[2], previousScissorBox[3]);
        } else {
            glDisable(GL_SCISSOR_TEST);
        }

        clipDepth = 0;
        clipEnabled = false;
        clipX = 0f;
        clipY = 0f;
        clipW = 0f;
        clipH = 0f;

        if (usingFBO) {
            glBindFramebuffer(GL_FRAMEBUFFER, previousFBO);
            glViewport(prevViewportX, prevViewportY, prevViewportW, prevViewportH);
            usingFBO = false;
        }
    }

    /**
     * Uploads queued instances to the GPU and draws them in one instanced draw call.
     *
     * <p>
     * If no instances are queued, the method returns immediately. After drawing, the CPU instance
     * buffer is cleared and texture unit bookkeeping is reset so the next batch segment starts fresh.
     * </p>
     */
    public void flush() {
        if (instanceCount == 0) {
            return;
        }

        instanceBuffer.flip();

        glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
        glBufferData(GL_ARRAY_BUFFER, (long) maxSprites * TextureBatchContract.INST_STRIDE_BYTES, GL_STREAM_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0, instanceBuffer);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        glDrawArraysInstanced(GL_TRIANGLES, 0, TextureBatchContract.QUAD_VERTS, instanceCount);

        instanceBuffer.clear();
        instanceCount = 0;

        activeTextureCount = 0;
        lastTextureID = -1;
        lastTextureUnit = -1;
        Arrays.fill(textureIDs, 0, textureUnitCapacity, -1);
    }

    /**
     * Disposes GPU resources owned by this batch.
     *
     * <p>
     * This disposes the default shader and deletes the quad and instance VBOs. Custom shaders are
     * not disposed here because they are not owned by the batch.
     * </p>
     */
    public void dispose() {
        defaultShader.dispose();
        glDeleteBuffers(quadVBO);
        glDeleteBuffers(instanceVBO);
    }

    /**
     * Binds the currently active shader and configures all vertex attribute pointers and divisors.
     *
     * <p>
     * This method is used at begin time and again when switching shaders during an active batch.
     * The shader is expected to use the attribute layout defined by {@link TextureBatchContract}.
     * </p>
     */
    private void bindActiveShaderState() {
        activeShader.bind();

        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);

        int quadStride = TextureBatchContract.QUAD_FLOATS_PER_VERT * TextureBatchContract.BYTES_PER_FLOAT;

        glEnableVertexAttribArray(TextureBatchContract.ATTR_LOCAL);
        glVertexAttribPointer(TextureBatchContract.ATTR_LOCAL, 2, GL_FLOAT, false, quadStride, 0L);

        glEnableVertexAttribArray(TextureBatchContract.ATTR_UV);
        glVertexAttribPointer(TextureBatchContract.ATTR_UV, 2, GL_FLOAT, false, quadStride, 2L * TextureBatchContract.BYTES_PER_FLOAT);

        glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);

        glEnableVertexAttribArray(TextureBatchContract.ATTR_XYWH);
        glVertexAttribPointer(TextureBatchContract.ATTR_XYWH, 4, GL_FLOAT, false, TextureBatchContract.INST_STRIDE_BYTES, 0L);
        glVertexAttribDivisor(TextureBatchContract.ATTR_XYWH, 1);

        glEnableVertexAttribArray(TextureBatchContract.ATTR_COL);
        glVertexAttribPointer(TextureBatchContract.ATTR_COL, 4, GL_FLOAT, false, TextureBatchContract.INST_STRIDE_BYTES, 4L * TextureBatchContract.BYTES_PER_FLOAT);
        glVertexAttribDivisor(TextureBatchContract.ATTR_COL, 1);

        glEnableVertexAttribArray(TextureBatchContract.ATTR_TEX);
        glVertexAttribPointer(TextureBatchContract.ATTR_TEX, 1, GL_FLOAT, false, TextureBatchContract.INST_STRIDE_BYTES, 8L * TextureBatchContract.BYTES_PER_FLOAT);
        glVertexAttribDivisor(TextureBatchContract.ATTR_TEX, 1);

        glEnableVertexAttribArray(TextureBatchContract.ATTR_UVRECT);
        glVertexAttribPointer(TextureBatchContract.ATTR_UVRECT, 4, GL_FLOAT, false, TextureBatchContract.INST_STRIDE_BYTES, 9L * TextureBatchContract.BYTES_PER_FLOAT);
        glVertexAttribDivisor(TextureBatchContract.ATTR_UVRECT, 1);

        glEnableVertexAttribArray(TextureBatchContract.ATTR_CLIPRECT);
        glVertexAttribPointer(TextureBatchContract.ATTR_CLIPRECT, 4, GL_FLOAT, false, TextureBatchContract.INST_STRIDE_BYTES, 13L * TextureBatchContract.BYTES_PER_FLOAT);
        glVertexAttribDivisor(TextureBatchContract.ATTR_CLIPRECT, 1);

        glEnableVertexAttribArray(TextureBatchContract.ATTR_CLIPENABLED);
        glVertexAttribPointer(TextureBatchContract.ATTR_CLIPENABLED, 1, GL_FLOAT, false, TextureBatchContract.INST_STRIDE_BYTES, 17L * TextureBatchContract.BYTES_PER_FLOAT);
        glVertexAttribDivisor(TextureBatchContract.ATTR_CLIPENABLED, 1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * Returns an already assigned texture unit for the given texture id, or binds the texture to a
     * newly allocated unit if needed.
     *
     * <p>
     * This method first checks the small last-texture cache, then scans the active texture units,
     * then grows the texture unit bookkeeping array if necessary, and finally binds the texture into
     * a new unit slot if one is available. If no more units may be used, {@code -1} is returned so
     * the caller can flush and retry.
     * </p>
     *
     * @param textureID the OpenGL texture id to resolve
     * @return the texture unit index, or -1 if no more units are available without flushing
     */
    private int getOrBindTextureUnit(int textureID) {
        if (lastTextureID == textureID && lastTextureUnit >= 0 && lastTextureUnit < activeTextureCount) {
            return lastTextureUnit;
        }

        for (int i = 0; i < activeTextureCount; i++) {
            if (textureIDs[i] == textureID) {
                lastTextureID = textureID;
                lastTextureUnit = i;
                return i;
            }
        }

        if (activeTextureCount >= textureUnitCapacity) {
            if (textureUnitCapacity >= maxTextureUnits) {
                return -1;
            }
            growTextureUnitCapacity();
        }

        int unit = activeTextureCount++;
        textureIDs[unit] = textureID;

        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureID);
        glActiveTexture(GL_TEXTURE0);

        lastTextureID = textureID;
        lastTextureUnit = unit;
        return unit;
    }

    /**
     * Grows the texture unit bookkeeping array up to the configured maximum texture unit limit.
     *
     * <p>
     * The new capacity is either doubled or increased by one, whichever is larger, but it never
     * exceeds {@link #maxTextureUnits}.
     * </p>
     */
    private void growTextureUnitCapacity() {
        int oldCapacity = textureUnitCapacity;
        int newCapacity = Math.min(maxTextureUnits, Math.max(oldCapacity + 1, oldCapacity * 2));
        textureIDs = Arrays.copyOf(textureIDs, newCapacity);
        Arrays.fill(textureIDs, oldCapacity, newCapacity, -1);
        textureUnitCapacity = newCapacity;
    }

    /**
     * Detects the OpenGL texture unit count and clamps it to a chosen maximum.
     *
     * <p>
     * If OpenGL reports an invalid value, a fallback of eight units is used.
     * </p>
     *
     * @param clampTo the maximum allowed return value
     * @return the detected unit count clamped to the supplied maximum
     */
    private static int detectTextureUnitsClamped(int clampTo) {
        int units = glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS);
        if (units <= 0) {
            units = 8;
        }
        return Math.min(units, clampTo);
    }
}