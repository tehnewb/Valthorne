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
 * Batches textured quad rendering into a small number of instanced draw calls while supporting
 * per-sprite tinting, sub-region UVs, optional framebuffer rendering, and shader-side clipping.
 *
 * <p>
 * This class is built for a 2D rendering pipeline where many sprites, UI pieces, font glyphs,
 * and texture regions are submitted between {@link #begin()} and {@link #end()}. Each submitted
 * sprite is written into one reusable CPU-side instance buffer. When the batch fills up or runs
 * out of available texture units for the current flush window, the queued instances are uploaded
 * and rendered in one instanced draw call.
 * </p>
 *
 * <h2>How batching works</h2>
 * <p>
 * A single static quad is stored in GPU memory. Every sprite submission writes one instance entry
 * that contains destination bounds, color, texture unit index, UV rectangle, and clip information.
 * The shader expands the shared quad into final world-space geometry for each sprite instance.
 * </p>
 *
 * <h2>Texture unit strategy</h2>
 * <p>
 * This implementation uses a dynamic texture-unit cache rather than always scanning the full maximum
 * number of units. It starts with a small active capacity and grows only when a frame actually needs
 * more distinct textures in the current flush window. That reduces needless iteration in common cases
 * where only a few textures are active at once.
 * </p>
 *
 * <h2>Clipping model</h2>
 * <p>
 * Scissor requests are tracked as a float clip stack and passed per instance into the shader.
 * This avoids forcing a flush every time clipping changes. Nested clip calls intersect with the
 * current top clip rectangle so child UI regions stay constrained to parent bounds.
 * </p>
 *
 * <h2>Framebuffer behavior</h2>
 * <p>
 * When drawing into a {@link FrameBuffer}, the batch captures the previous framebuffer and viewport,
 * optionally resizes the target buffer to match the current viewport, binds the target framebuffer,
 * and restores the original state during {@link #end()}.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * TextureBatch batch = new TextureBatch(8192);
 * Texture ui = new Texture("assets/ui/panel.png");
 * Texture icon = new Texture("assets/ui/icon.png");
 *
 * batch.begin();
 * batch.setColor(1f, 1f, 1f, 1f);
 *
 * batch.draw(ui, 32f, 32f, 256f, 128f);
 *
 * batch.beginScissor(40f, 40f, 180f, 80f);
 * batch.draw(icon, 48f, 48f, 32f, 32f);
 * batch.endScissor();
 *
 * batch.end();
 * }</pre>
 *
 * @author Albert Beaupre
 * @since February 8th, 2026
 */
public final class TextureBatch {

    /**
     * Number of vertices used by the shared quad mesh.
     */
    private static final int QUAD_VERTS = 6;

    /**
     * Number of floats stored per shared quad vertex.
     */
    private static final int QUAD_FLOATS_PER_VERT = 4;

    /**
     * Number of bytes in one float.
     */
    private static final int BYTES_PER_FLOAT = 4;

    /**
     * Number of floats stored per sprite instance.
     */
    private static final int INST_FLOATS = 18;

    /**
     * Byte stride of one sprite instance.
     */
    private static final int INST_STRIDE_BYTES = INST_FLOATS * BYTES_PER_FLOAT;

    /**
     * Attribute location for shared local quad positions.
     */
    private static final int ATTR_LOCAL = 0;

    /**
     * Attribute location for shared local quad UVs.
     */
    private static final int ATTR_UV = 1;

    /**
     * Attribute location for instance destination rectangle data.
     */
    private static final int ATTR_XYWH = 2;

    /**
     * Attribute location for instance tint color.
     */
    private static final int ATTR_COL = 3;

    /**
     * Attribute location for instance texture unit selector.
     */
    private static final int ATTR_TEX = 4;

    /**
     * Attribute location for instance UV rectangle.
     */
    private static final int ATTR_UVRECT = 5;

    /**
     * Attribute location for instance clip rectangle.
     */
    private static final int ATTR_CLIPRECT = 6;

    /**
     * Attribute location for instance clip enable flag.
     */
    private static final int ATTR_CLIPENABLED = 7;

    private final FloatBuffer instanceBuffer; // CPU-side instance staging buffer reused across flushes.
    private final Color color = new Color(1f, 1f, 1f, 1f); // Default tint applied when per-draw tint is not provided.
    private final Shader shader; // Instanced sprite shader used by the batch.
    private final int maxSprites; // Maximum number of queued sprite instances before an automatic flush.
    private final int maxTextureUnits; // Maximum number of hardware texture units this batch may use.
    private final int quadVBO; // GPU buffer containing the shared quad geometry.
    private final int instanceVBO; // GPU buffer containing uploaded instance data for the current flush.
    private final String[] samplerUniformNames; // Cached sampler uniform names so they are not rebuilt repeatedly.
    private int[] textureIDs; // Active texture IDs cached by texture unit index for the current flush window.
    private final int[] vp = new int[4]; // Temporary viewport buffer used when capturing GL viewport state.
    private final int[] previousScissorBox = new int[4]; // Saved GL scissor box restored after batch drawing ends.
    private final float[] clipStack = new float[256 * 4]; // Nested clip stack storing x, y, width, and height per level.

    private int textureUnitCapacity; // Current allocated texture cache capacity.
    private int activeTextureCount; // Number of active cached textures in the current flush window.
    private int lastTextureID = -1; // Last texture ID requested, used as a fast lookup shortcut.
    private int lastTextureUnit = -1; // Texture unit that the last requested texture ID was bound to.

    private int instanceCount; // Number of currently queued instances awaiting a flush.
    private boolean drawing; // True while the batch is between begin and end.

    private boolean usingFBO; // True when drawing into a framebuffer target.
    private int previousFBO; // Previously bound framebuffer captured before binding a target framebuffer.
    private int prevViewportX; // Previous viewport x restored after framebuffer rendering ends.
    private int prevViewportY; // Previous viewport y restored after framebuffer rendering ends.
    private int prevViewportW; // Previous viewport width restored after framebuffer rendering ends.
    private int prevViewportH; // Previous viewport height restored after framebuffer rendering ends.

    private int clipDepth; // Current nested clip depth.
    private boolean clipEnabled; // True when at least one active clip region is in effect.
    private float clipX; // Current effective clip x used for new instances.
    private float clipY; // Current effective clip y used for new instances.
    private float clipW; // Current effective clip width used for new instances.
    private float clipH; // Current effective clip height used for new instances.

    private boolean scissorEnabledBeforeBegin; // Original GL scissor-test enabled state captured before batching starts.

    /**
     * Creates a batch using the detected texture-unit limit, clamped to a safe upper bound.
     *
     * <p>
     * This is the convenience constructor most callers should use. It detects the hardware-supported
     * maximum texture units, clamps that to a conservative upper cap, and then delegates to the full
     * constructor.
     * </p>
     *
     * @param maxSprites maximum number of sprite instances queued before a flush
     */
    public TextureBatch(int maxSprites) {
        this(maxSprites, detectTextureUnitsClamped(16));
    }

    /**
     * Creates a batch with explicit sprite and texture-unit limits.
     *
     * <p>
     * Construction allocates the shared quad buffer, the instance buffer, the initial dynamic texture
     * cache, and the shader program. Sampler uniforms are cached and initialized once up front so they
     * do not need to be rebound every frame.
     * </p>
     *
     * <p>
     * The texture cache starts smaller than the hardware maximum and grows on demand. That keeps
     * ordinary frames faster when they only use a small number of textures.
     * </p>
     *
     * @param maxSprites      maximum number of sprite instances queued before a flush
     * @param maxTextureUnits maximum number of texture units this batch may use
     * @throws IllegalArgumentException if {@code maxSprites <= 0} or {@code maxTextureUnits < 2}
     */
    public TextureBatch(int maxSprites, int maxTextureUnits) {
        if (maxSprites <= 0) throw new IllegalArgumentException("maxSprites must be > 0");
        if (maxTextureUnits < 2) throw new IllegalArgumentException("maxTextureUnits must be >= 2");

        this.maxSprites = maxSprites;
        this.maxTextureUnits = Math.min(maxTextureUnits, 16);

        this.textureUnitCapacity = Math.min(4, this.maxTextureUnits);
        this.textureIDs = new int[this.textureUnitCapacity];
        Arrays.fill(this.textureIDs, -1);

        this.instanceBuffer = BufferUtils.createFloatBuffer(this.maxSprites * INST_FLOATS);

        this.samplerUniformNames = new String[this.maxTextureUnits];
        for (int i = 0; i < this.maxTextureUnits; i++) {
            this.samplerUniformNames[i] = "u_tex" + i;
        }

        this.shader = new Shader(VERTEX_SHADER, buildFragmentShader(this.maxTextureUnits));
        this.shader.bindAttribLocation(ATTR_LOCAL, "a_local");
        this.shader.bindAttribLocation(ATTR_UV, "a_uv");
        this.shader.bindAttribLocation(ATTR_XYWH, "i_xywh");
        this.shader.bindAttribLocation(ATTR_COL, "i_col");
        this.shader.bindAttribLocation(ATTR_TEX, "i_tex");
        this.shader.bindAttribLocation(ATTR_UVRECT, "i_uvRect");
        this.shader.bindAttribLocation(ATTR_CLIPRECT, "i_clipRect");
        this.shader.bindAttribLocation(ATTR_CLIPENABLED, "i_clipEnabled");
        this.shader.reload();

        this.shader.bind();
        for (int i = 0; i < this.maxTextureUnits; i++) {
            this.shader.setUniform1i(this.samplerUniformNames[i], i);
        }
        this.shader.unbind();

        FloatBuffer quad = BufferUtils.createFloatBuffer(QUAD_VERTS * QUAD_FLOATS_PER_VERT);

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
        glBufferData(GL_ARRAY_BUFFER, (long) this.maxSprites * INST_STRIDE_BYTES, GL_STREAM_DRAW);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * Replaces the batch default tint color.
     *
     * <p>
     * This color is used by draw calls that do not provide an explicit tint override.
     * The provided color is copied into the internal mutable color object.
     * </p>
     *
     * @param tint new default tint
     * @throws NullPointerException if {@code tint} is null
     */
    public void setColor(Color tint) {
        Objects.requireNonNull(tint, "Color cannot be null");
        color.set(tint);
    }

    /**
     * Replaces the batch default tint color from individual channel values.
     *
     * @param r red channel
     * @param g green channel
     * @param b blue channel
     * @param a alpha channel
     */
    public void setColor(float r, float g, float b, float a) {
        color.r(r);
        color.g(g);
        color.b(b);
        color.a(a);
    }

    /**
     * Begins a new nested clip region.
     *
     * <p>
     * The new clip rectangle is intersected with the currently active parent clip region, if one exists.
     * The resulting effective clip rectangle is pushed onto the clip stack and becomes the clip rectangle
     * written into newly queued instances.
     * </p>
     *
     * <p>
     * This method does not force a flush. Clipping is handled in the shader through per-instance data,
     * which is why this path preserves batching performance.
     * </p>
     *
     * @param x      clip x
     * @param y      clip y
     * @param width  clip width
     * @param height clip height
     * @throws IllegalStateException if the batch is not drawing or the clip stack overflows
     */
    public void beginScissor(float x, float y, float width, float height) {
        if (!drawing) throw new IllegalStateException("Call begin() before beginScissor().");
        if ((clipDepth + 1) * 4 > clipStack.length) throw new IllegalStateException("Scissor stack overflow.");

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
     * Ends the current nested clip region.
     *
     * <p>
     * If a parent clip remains on the stack, that parent becomes active again. If no clip regions remain,
     * clipping is disabled for subsequently queued instances.
     * </p>
     *
     * @throws IllegalStateException if the batch is not drawing or no clip region is active
     */
    public void endScissor() {
        if (!drawing) throw new IllegalStateException("Call begin() before endScissor().");
        if (clipDepth <= 0) throw new IllegalStateException("No active scissor to end.");

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
     * Begins drawing to the currently bound framebuffer.
     *
     * <p>
     * This is the standard entry point for a normal frame when no custom framebuffer target is needed.
     * </p>
     */
    public void begin() {
        begin(null);
    }

    /**
     * Begins drawing, optionally into the supplied framebuffer.
     *
     * <p>
     * This method prepares all batch state for a new draw session. It captures framebuffer and viewport
     * state when needed, resets instance submission state, resets dynamic texture-unit tracking, disables
     * hardware scissoring while the batch is active, binds the shader, and configures all shared and
     * per-instance vertex attributes.
     * </p>
     *
     * @param fbo optional framebuffer target, or null to draw to the current framebuffer
     * @throws IllegalStateException if the batch is already drawing or the current viewport is invalid
     */
    public void begin(FrameBuffer fbo) {
        if (drawing) throw new IllegalStateException("Already drawing.");
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

        shader.bind();

        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);

        int quadStride = QUAD_FLOATS_PER_VERT * BYTES_PER_FLOAT;

        glEnableVertexAttribArray(ATTR_LOCAL);
        glVertexAttribPointer(ATTR_LOCAL, 2, GL_FLOAT, false, quadStride, 0L);

        glEnableVertexAttribArray(ATTR_UV);
        glVertexAttribPointer(ATTR_UV, 2, GL_FLOAT, false, quadStride, 2L * BYTES_PER_FLOAT);

        glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);

        glEnableVertexAttribArray(ATTR_XYWH);
        glVertexAttribPointer(ATTR_XYWH, 4, GL_FLOAT, false, INST_STRIDE_BYTES, 0L);
        glVertexAttribDivisor(ATTR_XYWH, 1);

        glEnableVertexAttribArray(ATTR_COL);
        glVertexAttribPointer(ATTR_COL, 4, GL_FLOAT, false, INST_STRIDE_BYTES, 4L * BYTES_PER_FLOAT);
        glVertexAttribDivisor(ATTR_COL, 1);

        glEnableVertexAttribArray(ATTR_TEX);
        glVertexAttribPointer(ATTR_TEX, 1, GL_FLOAT, false, INST_STRIDE_BYTES, 8L * BYTES_PER_FLOAT);
        glVertexAttribDivisor(ATTR_TEX, 1);

        glEnableVertexAttribArray(ATTR_UVRECT);
        glVertexAttribPointer(ATTR_UVRECT, 4, GL_FLOAT, false, INST_STRIDE_BYTES, 9L * BYTES_PER_FLOAT);
        glVertexAttribDivisor(ATTR_UVRECT, 1);

        glEnableVertexAttribArray(ATTR_CLIPRECT);
        glVertexAttribPointer(ATTR_CLIPRECT, 4, GL_FLOAT, false, INST_STRIDE_BYTES, 13L * BYTES_PER_FLOAT);
        glVertexAttribDivisor(ATTR_CLIPRECT, 1);

        glEnableVertexAttribArray(ATTR_CLIPENABLED);
        glVertexAttribPointer(ATTR_CLIPENABLED, 1, GL_FLOAT, false, INST_STRIDE_BYTES, 17L * BYTES_PER_FLOAT);
        glVertexAttribDivisor(ATTR_CLIPENABLED, 1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * Draws a texture using the texture's own stored destination rectangle and the current batch tint.
     *
     * @param tex texture to draw
     */
    public void draw(Texture tex) {
        draw(tex, null);
    }

    /**
     * Draws a texture using the texture's own stored destination rectangle and an optional tint.
     *
     * @param tex  texture to draw
     * @param tint optional tint override, or null to use the batch default tint
     * @throws NullPointerException if {@code tex} is null
     */
    public void draw(Texture tex, Color tint) {
        Objects.requireNonNull(tex, "Texture cannot be null");
        drawUV(tex, tex.getX(), tex.getY(), tex.getWidth(), tex.getHeight(), tex.leftRegion, tex.topRegion, tex.rightRegion, tex.bottomRegion, tint);
    }

    /**
     * Draws a texture over the supplied destination rectangle using full UV coverage.
     *
     * @param tex texture to draw
     * @param x   destination x
     * @param y   destination y
     * @param w   destination width
     * @param h   destination height
     */
    public void draw(Texture tex, float x, float y, float w, float h) {
        drawUV(tex, x, y, w, h, 0f, 0f, 1f, 1f, null);
    }

    /**
     * Draws a texture over the supplied destination rectangle using full UV coverage and an optional tint.
     *
     * @param tex  texture to draw
     * @param x    destination x
     * @param y    destination y
     * @param w    destination width
     * @param h    destination height
     * @param tint optional tint override, or null to use the batch default tint
     */
    public void draw(Texture tex, float x, float y, float w, float h, Color tint) {
        drawUV(tex, x, y, w, h, 0f, 0f, 1f, 1f, tint);
    }

    /**
     * Draws a texture over the supplied destination rectangle using explicit UV coordinates.
     *
     * @param tex texture to draw
     * @param x   destination x
     * @param y   destination y
     * @param w   destination width
     * @param h   destination height
     * @param u0  left UV
     * @param v0  top UV
     * @param u1  right UV
     * @param v1  bottom UV
     */
    public void draw(Texture tex, float x, float y, float w, float h, float u0, float v0, float u1, float v1) {
        drawUV(tex, x, y, w, h, u0, v0, u1, v1, null);
    }

    /**
     * Draws a texture sub-region defined in source pixel space.
     *
     * @param tex          texture to draw
     * @param x            destination x
     * @param y            destination y
     * @param w            destination width
     * @param h            destination height
     * @param regionX      source pixel x
     * @param regionY      source pixel y
     * @param regionWidth  source pixel width
     * @param regionHeight source pixel height
     */
    public void drawRegion(Texture tex, float x, float y, float w, float h, float regionX, float regionY, float regionWidth, float regionHeight) {
        drawRegion(tex, x, y, w, h, regionX, regionY, regionWidth, regionHeight, null);
    }

    /**
     * Draws a texture sub-region defined in source pixel space with an optional tint override.
     *
     * <p>
     * This method converts the requested source pixel rectangle into normalized UV coordinates
     * using the texture's decoded data dimensions.
     * </p>
     *
     * @param tex          texture to draw
     * @param x            destination x
     * @param y            destination y
     * @param w            destination width
     * @param h            destination height
     * @param regionX      source pixel x
     * @param regionY      source pixel y
     * @param regionWidth  source pixel width
     * @param regionHeight source pixel height
     * @param tint         optional tint override, or null to use the batch default tint
     * @throws NullPointerException if {@code tex} is null
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
     * Draws a {@link TextureRegion} using the region's own stored destination rectangle.
     *
     * @param region region to draw
     * @throws NullPointerException if {@code region} is null
     */
    public void drawRegion(TextureRegion region) {
        Objects.requireNonNull(region, "TextureRegion cannot be null");
        drawRegion(region, region.getX(), region.getY(), region.getWidth(), region.getHeight(), region.getRegionX(), region.getRegionY(), region.getRegionWidth(), region.getRegionHeight(), null);
    }

    /**
     * Draws a {@link TextureRegion} over a custom destination rectangle.
     *
     * @param region region to draw
     * @param x      destination x
     * @param y      destination y
     * @param w      destination width
     * @param h      destination height
     * @throws NullPointerException if {@code region} is null
     */
    public void drawRegion(TextureRegion region, float x, float y, float w, float h) {
        Objects.requireNonNull(region, "TextureRegion cannot be null");
        drawRegion(region, x, y, w, h, region.getRegionX(), region.getRegionY(), region.getRegionWidth(), region.getRegionHeight(), null);
    }

    /**
     * Draws a {@link TextureRegion} over a custom destination rectangle with an optional tint override.
     *
     * @param region region to draw
     * @param x      destination x
     * @param y      destination y
     * @param w      destination width
     * @param h      destination height
     * @param tint   optional tint override, or null to use the batch default tint
     * @throws NullPointerException if {@code region} is null
     */
    public void drawRegion(TextureRegion region, float x, float y, float w, float h, Color tint) {
        Objects.requireNonNull(region, "TextureRegion cannot be null");
        drawRegion(region, x, y, w, h, region.getRegionX(), region.getRegionY(), region.getRegionWidth(), region.getRegionHeight(), tint);
    }

    /**
     * Queues one sprite instance using explicit UV coordinates.
     *
     * <p>
     * This is the core draw path used by the public convenience methods. It resolves the texture
     * unit for the sprite's texture, flushes if necessary, writes one instance entry into the
     * CPU-side instance buffer, and stores clip data if clipping is currently active.
     * </p>
     *
     * @param tex  texture to draw
     * @param x    destination x
     * @param y    destination y
     * @param w    destination width
     * @param h    destination height
     * @param u0   left UV
     * @param v0   top UV
     * @param u1   right UV
     * @param v1   bottom UV
     * @param tint optional tint override, or null to use the batch default tint
     * @throws IllegalStateException if called outside begin/end
     * @throws NullPointerException  if {@code tex} is null
     */
    public void drawUV(Texture tex, float x, float y, float w, float h, float u0, float v0, float u1, float v1, Color tint) {
        if (!drawing) throw new IllegalStateException("Call begin() before draw().");
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
     * Ends the current draw session and restores modified GL state.
     *
     * <p>
     * This flushes any remaining queued instances, disables the configured instanced attributes,
     * unbinds textures used during the frame, restores previous scissor state, restores the previous
     * framebuffer and viewport if needed, and clears current clip state.
     * </p>
     *
     * @throws IllegalStateException if called outside begin/end
     */
    public void end() {
        if (!drawing) throw new IllegalStateException("Call begin() before end().");

        flush();

        glVertexAttribDivisor(ATTR_XYWH, 0);
        glVertexAttribDivisor(ATTR_COL, 0);
        glVertexAttribDivisor(ATTR_TEX, 0);
        glVertexAttribDivisor(ATTR_UVRECT, 0);
        glVertexAttribDivisor(ATTR_CLIPRECT, 0);
        glVertexAttribDivisor(ATTR_CLIPENABLED, 0);

        glDisableVertexAttribArray(ATTR_LOCAL);
        glDisableVertexAttribArray(ATTR_UV);
        glDisableVertexAttribArray(ATTR_XYWH);
        glDisableVertexAttribArray(ATTR_COL);
        glDisableVertexAttribArray(ATTR_TEX);
        glDisableVertexAttribArray(ATTR_UVRECT);
        glDisableVertexAttribArray(ATTR_CLIPRECT);
        glDisableVertexAttribArray(ATTR_CLIPENABLED);

        shader.unbind();
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
     * Uploads queued instance data and issues one instanced draw call.
     *
     * <p>
     * If no instances are queued, this method returns immediately. After drawing, the instance buffer
     * is cleared, the queued instance count is reset, and active texture-unit tracking is reset for the
     * next flush window.
     * </p>
     */
    public void flush() {
        if (instanceCount == 0) return;

        instanceBuffer.flip();

        glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
        glBufferData(GL_ARRAY_BUFFER, (long) maxSprites * INST_STRIDE_BYTES, GL_STREAM_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0, instanceBuffer);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        glDrawArraysInstanced(GL_TRIANGLES, 0, QUAD_VERTS, instanceCount);

        instanceBuffer.clear();
        instanceCount = 0;

        activeTextureCount = 0;
        lastTextureID = -1;
        lastTextureUnit = -1;
        Arrays.fill(textureIDs, 0, textureUnitCapacity, -1);
    }

    /**
     * Disposes the shader and GPU buffers owned by this batch.
     */
    public void dispose() {
        shader.dispose();
        glDeleteBuffers(quadVBO);
        glDeleteBuffers(instanceVBO);
    }

    /**
     * Resolves or assigns a texture unit for the supplied texture ID.
     *
     * <p>
     * Resolution order is:
     * </p>
     * <ol>
     *     <li>check the last-texture fast path</li>
     *     <li>scan only the active texture range</li>
     *     <li>grow capacity if needed and allowed</li>
     *     <li>bind the texture into a newly assigned unit</li>
     * </ol>
     *
     * <p>
     * If no more texture units can be assigned, the method returns {@code -1}, signaling the caller
     * that a flush is required.
     * </p>
     *
     * @param textureID texture ID to resolve
     * @return resolved texture unit index, or {@code -1} if no slot is available
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
     * Grows the dynamic texture-unit cache array.
     *
     * <p>
     * Growth doubles the current capacity when possible while never exceeding the maximum allowed
     * texture-unit count for this batch. Newly added slots are initialized to {@code -1}.
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
     * Detects the available texture-unit count and clamps it to the requested upper bound.
     *
     * <p>
     * If OpenGL reports an invalid result, a conservative fallback value is used.
     * </p>
     *
     * @param clampTo maximum allowed returned value
     * @return detected texture-unit count clamped to the supplied upper bound
     */
    private static int detectTextureUnitsClamped(int clampTo) {
        int units = glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS);
        if (units <= 0) units = 8;
        return Math.min(units, clampTo);
    }

    /**
     * Vertex shader used for instanced sprite expansion and clip propagation.
     */
    private static final String VERTEX_SHADER = """
            #version 120
            
            attribute vec2 a_local;
            attribute vec2 a_uv;
            attribute vec4 i_xywh;
            attribute vec4 i_col;
            attribute float i_tex;
            attribute vec4 i_uvRect;
            attribute vec4 i_clipRect;
            attribute float i_clipEnabled;
            
            varying vec2 v_uv;
            varying vec4 v_col;
            varying float v_tex;
            varying vec2 v_world;
            varying vec4 v_clipRect;
            varying float v_clipEnabled;
            
            void main() {
                vec2 world = i_xywh.xy + (a_local * i_xywh.zw);
                gl_Position = gl_ModelViewProjectionMatrix * vec4(world.xy, 0.0, 1.0);
                v_uv = mix(i_uvRect.xy, i_uvRect.zw, a_uv);
                v_col = i_col;
                v_tex = i_tex;
                v_world = world;
                v_clipRect = i_clipRect;
                v_clipEnabled = i_clipEnabled;
            }
            """;

    /**
     * Builds the fragment shader source for the configured number of texture samplers.
     *
     * <p>
     * GLSL 120 does not provide sampler arrays in the way newer shader versions do, so this method
     * generates an if-chain to choose the correct sampler from the per-instance texture index.
     * The shader also performs clip rejection before sampling the texture.
     * </p>
     *
     * @param units number of sampler uniforms to generate
     * @return complete fragment shader source
     */
    private static String buildFragmentShader(int units) {
        StringBuilder sb = new StringBuilder();
        sb.append("#version 120\n");
        for (int i = 0; i < units; i++) {
            sb.append("uniform sampler2D u_tex").append(i).append(";\n");
        }
        sb.append("""
                varying vec2 v_uv;
                varying vec4 v_col;
                varying float v_tex;
                varying vec2 v_world;
                varying vec4 v_clipRect;
                varying float v_clipEnabled;
                
                void main() {
                    if (v_clipEnabled > 0.5) {
                        if (v_world.x < v_clipRect.x || v_world.y < v_clipRect.y ||
                            v_world.x > v_clipRect.x + v_clipRect.z ||
                            v_world.y > v_clipRect.y + v_clipRect.w) {
                            discard;
                        }
                    }
                
                    vec4 c;
                """);

        sb.append("    float t = v_tex;\n");
        sb.append("    if (t < 0.5) c = texture2D(u_tex0, v_uv);\n");
        for (int i = 1; i < units; i++) {
            sb.append("    else if (t < ").append(i).append(".5) c = texture2D(u_tex").append(i).append(", v_uv);\n");
        }
        sb.append("""
                    else c = texture2D(u_tex0, v_uv);
                    gl_FragColor = c * v_col;
                }
                """);
        return sb.toString();
    }
}