package valthorne.graphics.texture;

import org.lwjgl.BufferUtils;
import valthorne.graphics.Color;
import valthorne.graphics.Sprite;
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
 * <p>
 * {@code TextureBatch} is Valthorne's instanced textured-quad renderer. It is designed
 * to batch many sprite draw operations into as few OpenGL draw calls as possible while
 * still supporting several advanced rendering features such as color tinting, sprite
 * scaling, sprite rotation, nine-patch rendering, nested clipping, translation stacks,
 * shader overrides, and rendering into a {@link FrameBuffer}.
 * </p>
 *
 * <p>
 * Instead of immediately sending every individual sprite to the GPU, this class stores
 * per-instance data inside an internal {@link FloatBuffer}. Once the batch is full or
 * the caller explicitly ends the batch, that instance data is uploaded to the GPU and
 * rendered with {@code glDrawArraysInstanced}. This greatly reduces driver overhead and
 * improves rendering performance when drawing large numbers of textured objects.
 * </p>
 *
 * <p>
 * The batch uses a static quad as its base geometry. Every sprite draw call writes a
 * small set of instance attributes describing where that quad should appear, which
 * texture unit it should sample from, what UV region should be used, what color should
 * tint it, whether clipping is active, and how rotation should be applied. The shader
 * then reconstructs the final sprite from this data for each instance.
 * </p>
 *
 * <p>
 * This class supports multiple textures in a single batch, up to the configured texture
 * unit limit. When a draw call references a texture that does not fit in the currently
 * active set of bound textures, the batch is flushed automatically and starts filling
 * again with a fresh texture unit set.
 * </p>
 *
 * <p>
 * Typical usage follows the standard batching lifecycle:
 * </p>
 *
 * <pre>{@code
 * TextureBatch batch = new TextureBatch(2000);
 * Texture texture = new Texture(...);
 * TextureRegion region = new TextureRegion(texture);
 * Sprite sprite = new Sprite(region);
 *
 * sprite.setX(100);
 * sprite.setY(50);
 * sprite.setRotation(25f);
 *
 * batch.begin();
 * batch.draw(sprite);
 * batch.draw(texture, 300, 100, 64, 64);
 *
 * batch.pushTranslation(20, 10);
 * batch.draw(sprite, 400, 200, 96, 96);
 * batch.popTranslation();
 *
 * batch.beginScissor(0, 0, 800, 600);
 * batch.drawRegion(region, 50, 50, 128, 128);
 * batch.endScissor();
 *
 * batch.end();
 * }</pre>
 *
 * <p>
 * This class is intended to be reused every frame rather than recreated constantly.
 * Create it once, use it for the lifetime of the renderer, and dispose it when the
 * graphics context is being shut down.
 * </p>
 *
 * @author Albert Beaupre
 * @since February 8th, 2026
 */
public final class TextureBatch {

    private final FloatBuffer instanceBuffer; // Buffer holding queued per-instance sprite data before upload
    private final Color color = new Color(1f, 1f, 1f, 1f); // Default batch tint used when a draw call does not provide one
    private final Shader defaultShader; // Built-in shader used when no custom shader is active
    private Shader customShader; // Optional user-supplied shader override
    private Shader activeShader; // Shader currently bound and used for rendering
    private final int maxSprites; // Maximum number of sprite instances that can be queued before a flush
    private final int maxTextureUnits; // Maximum number of texture units this batch may use
    private final int quadVBO; // VBO containing the shared quad geometry used by all instances
    private final int instanceVBO; // VBO containing uploaded instance data for the current flush
    private int[] textureIDs; // Cached texture IDs currently bound to the batch's texture units
    private final int[] vp = new int[4]; // Temporary viewport storage used when switching framebuffer targets
    private final int[] previousScissorBox = new int[4]; // Previous OpenGL scissor rectangle restored after batching
    private final float[] clipStack = new float[256]; // Stack storing nested clip rectangles as x, y, width, height groups
    private final float[] translationStack = new float[256]; // Stack storing nested translation states as x and y pairs
    private int translationDepth; // Current depth of the translation stack
    private float translationX; // Current accumulated translation offset on the X axis
    private float translationY; // Current accumulated translation offset on the Y axis

    private int textureUnitCapacity; // Current active texture-unit cache capacity
    private int activeTextureCount; // Number of texture units currently populated in this batch
    private int lastTextureID = -1; // Cached last texture ID for fast consecutive reuse
    private int lastTextureUnit = -1; // Cached texture unit index for the last bound texture

    private int instanceCount; // Number of queued sprite instances currently waiting to be drawn
    private boolean drawing; // Whether begin() has been called and the batch is currently active

    private boolean usingFBO; // Whether the current batch is rendering to a framebuffer object
    private int previousFBO; // Previously bound framebuffer restored after end()
    private int prevViewportX; // Previous viewport X restored after rendering to an FBO
    private int prevViewportY; // Previous viewport Y restored after rendering to an FBO
    private int prevViewportW; // Previous viewport width restored after rendering to an FBO
    private int prevViewportH; // Previous viewport height restored after rendering to an FBO

    private int clipDepth; // Current number of active nested clip rectangles
    private boolean clipEnabled; // Whether clipping is active for newly queued instances
    private float clipX; // Active clip rectangle X
    private float clipY; // Active clip rectangle Y
    private float clipW; // Active clip rectangle width
    private float clipH; // Active clip rectangle height

    private boolean scissorEnabledBeforeBegin; // Whether OpenGL scissor testing was enabled before begin() modified state

    /**
     * <p>
     * Creates a new {@code TextureBatch} with the requested sprite capacity and an
     * automatically detected texture unit limit clamped to sixteen.
     * </p>
     *
     * <p>
     * This constructor is convenient for most normal usage. It queries the OpenGL
     * context for supported texture image units and keeps the batch within a safe
     * upper bound.
     * </p>
     *
     * @param maxSprites the maximum number of sprite instances that may be queued
     *                   before the batch must flush
     */
    public TextureBatch(int maxSprites) {
        this(maxSprites, detectTextureUnitsClamped(16));
    }

    /**
     * <p>
     * Creates a new {@code TextureBatch} with an explicit sprite capacity and explicit
     * texture unit limit.
     * </p>
     *
     * <p>
     * This constructor allocates the CPU-side instance buffer, creates the default
     * shader, binds its expected attributes and samplers, builds the base quad vertex
     * data, and creates the OpenGL buffer objects required for batched instanced
     * rendering.
     * </p>
     *
     * <p>
     * The texture unit count is clamped to sixteen to keep the generated fragment shader
     * and sampler setup within the intended supported range for this batch.
     * </p>
     *
     * @param maxSprites      the maximum number of sprite instances that may be queued
     *                        before the batch flushes
     * @param maxTextureUnits the maximum number of texture units this batch should use
     * @throws IllegalArgumentException if {@code maxSprites <= 0}
     * @throws IllegalArgumentException if {@code maxTextureUnits < 2}
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
     * <p>
     * Returns the shader that is currently considered active for external callers.
     * </p>
     *
     * <p>
     * If a custom shader has been assigned with {@link #setShader(Shader)}, that shader
     * is returned. Otherwise the internally managed default shader is returned.
     * </p>
     *
     * @return the currently selected shader for this batch
     */
    public Shader getShader() {
        return customShader != null ? customShader : defaultShader;
    }

    /**
     * <p>
     * Returns the default shader owned by this batch.
     * </p>
     *
     * <p>
     * This can be useful when the caller wants to inspect, configure, or compare the
     * built-in shader without affecting the custom shader state.
     * </p>
     *
     * @return the default batch shader
     */
    public Shader getDefaultShader() {
        return defaultShader;
    }

    /**
     * <p>
     * Sets a custom shader for the batch.
     * </p>
     *
     * <p>
     * Passing the same instance as the default shader clears the custom override and
     * reverts the batch back to its internal shader. If the batch is currently drawing,
     * it flushes queued data first, unbinds the old shader, switches the active shader,
     * and then rebinds the required vertex attribute state.
     * </p>
     *
     * @param shader the shader to use, or {@code null} to revert to the default shader
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
     * <p>
     * Sets the batch's default tint color using a {@link Color} instance.
     * </p>
     *
     * <p>
     * This default tint is used for draw calls that do not provide their own tint
     * argument. The supplied color is copied into the internal batch color rather
     * than storing the reference directly.
     * </p>
     *
     * @param tint the new default tint color
     * @throws NullPointerException if {@code tint} is {@code null}
     */
    public void setColor(Color tint) {
        Objects.requireNonNull(tint, "Color cannot be null");
        color.set(tint);
    }

    /**
     * <p>
     * Sets the batch's default tint color using explicit RGBA components.
     * </p>
     *
     * <p>
     * This affects future draw calls that do not pass a per-draw tint.
     * </p>
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
     * <p>
     * Pushes the current translation state onto the translation stack and applies an
     * additional translation offset.
     * </p>
     *
     * <p>
     * This is useful for hierarchical rendering where a parent container wants child
     * draw calls to inherit an offset without manually adjusting every coordinate.
     * The translation affects queued draw coordinates only and remains active until
     * {@link #popTranslation()} is called.
     * </p>
     *
     * @param x the translation offset to add on the X axis
     * @param y the translation offset to add on the Y axis
     * @throws IllegalStateException if the batch is not currently drawing
     * @throws IllegalStateException if the translation stack overflows
     */
    public void pushTranslation(float x, float y) {
        if (!drawing) {
            throw new IllegalStateException("Call begin() before pushTranslation().");
        }
        if ((translationDepth + 1) * 2 > translationStack.length) {
            throw new IllegalStateException("Translation stack overflow.");
        }

        int index = translationDepth * 2;
        translationStack[index] = translationX;
        translationStack[index + 1] = translationY;
        translationDepth++;

        translationX += x;
        translationY += y;
    }

    /**
     * <p>
     * Pops the most recently pushed translation state from the translation stack.
     * </p>
     *
     * <p>
     * After this call, future draw operations use the previously stored translation
     * values. This method must be paired with an earlier call to
     * {@link #pushTranslation(float, float)}.
     * </p>
     *
     * @throws IllegalStateException if the batch is not currently drawing
     * @throws IllegalStateException if there is no translation state to pop
     */
    public void popTranslation() {
        if (!drawing) {
            throw new IllegalStateException("Call begin() before popTranslation().");
        }
        if (translationDepth <= 0) {
            throw new IllegalStateException("No active translation to pop.");
        }

        translationDepth--;
        int index = translationDepth * 2;
        translationX = translationStack[index];
        translationY = translationStack[index + 1];
    }

    /**
     * <p>
     * Returns the currently accumulated X translation offset.
     * </p>
     *
     * <p>
     * This value reflects the active translation stack state and is added to draw
     * positions when sprites are queued.
     * </p>
     *
     * @return the current X translation offset
     */
    public float getTranslationX() {
        return translationX;
    }

    /**
     * <p>
     * Returns the currently accumulated Y translation offset.
     * </p>
     *
     * <p>
     * This value reflects the active translation stack state and is added to draw
     * positions when sprites are queued.
     * </p>
     *
     * @return the current Y translation offset
     */
    public float getTranslationY() {
        return translationY;
    }

    /**
     * <p>
     * Begins a new scissor clip region for subsequently queued draw operations.
     * </p>
     *
     * <p>
     * The clip state is stored on an internal stack, allowing nested clipping. When a
     * new clip is added while another clip is already active, the resulting effective
     * region becomes the intersection of the existing clip and the new one. This
     * guarantees that nested clips cannot expand outside of their parent region.
     * </p>
     *
     * <p>
     * The clip rectangle is stored as instance data and later interpreted by the shader.
     * It does not directly call {@code glScissor} for every nested region.
     * </p>
     *
     * @param x      the clip region X coordinate
     * @param y      the clip region Y coordinate
     * @param width  the clip region width
     * @param height the clip region height
     * @throws IllegalStateException if the batch is not currently drawing
     * @throws IllegalStateException if the clip stack overflows
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
     * <p>
     * Ends the most recently begun scissor clip region.
     * </p>
     *
     * <p>
     * If a parent clip still exists after the pop, that parent becomes the newly active
     * clip. If no clip remains, clipping is disabled entirely for future queued draws.
     * </p>
     *
     * @throws IllegalStateException if the batch is not currently drawing
     * @throws IllegalStateException if there is no active scissor region to end
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
     * <p>
     * Begins a new rendering batch using the default framebuffer.
     * </p>
     *
     * <p>
     * This is the standard entry point for normal rendering when no off-screen target
     * is needed.
     * </p>
     */
    public void begin() {
        begin(null);
    }

    /**
     * <p>
     * Begins a new rendering batch, optionally rendering into a {@link FrameBuffer}.
     * </p>
     *
     * <p>
     * This method initializes batch state, resets clip and translation stacks, saves
     * and temporarily modifies relevant OpenGL state, clears instance bookkeeping, and
     * binds the currently active shader and vertex attributes. If an FBO is provided,
     * the current framebuffer and viewport are captured and restored later in
     * {@link #end()}.
     * </p>
     *
     * <p>
     * If the provided framebuffer size does not match the current viewport size, the
     * framebuffer is resized to match the viewport before rendering begins.
     * </p>
     *
     * @param fbo the framebuffer target to render into, or {@code null} to render to
     *            the currently bound default framebuffer
     * @throws IllegalStateException if the batch is already active
     * @throws IllegalStateException if the current viewport is invalid while rendering
     *                               into a framebuffer
     */
    public void begin(FrameBuffer fbo) {
        if (drawing) throw new IllegalStateException("Already drawing.");
        translationDepth = 0;
        translationX = 0f;
        translationY = 0f;
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
     * <p>
     * Queues a {@link Sprite} for rendering using the sprite's own transform, region,
     * size, flip state, rotation state, and color.
     * </p>
     *
     * <p>
     * This overload simply delegates to {@link #draw(Sprite, Color)} with a
     * {@code null} tint, allowing the sprite's own color to be used.
     * </p>
     *
     * @param sprite the sprite to draw
     * @throws NullPointerException if {@code sprite} is {@code null}
     */
    public void draw(Sprite sprite) {
        Objects.requireNonNull(sprite, "Sprite cannot be null");
        draw(sprite, null);
    }

    /**
     * <p>
     * Queues a {@link Sprite} for rendering using the sprite's current transform data
     * but with an optional tint override.
     * </p>
     *
     * <p>
     * The sprite's region UVs are resolved, its flip flags are applied by swapping UV
     * bounds, scaled width and height are computed, and the sprite's rotation is
     * converted into sine and cosine values used by the shader. The result is then
     * forwarded to {@link #drawUV(Texture, float, float, float, float, float, float, float, float, float, float, float, float, Color)}.
     * </p>
     *
     * @param sprite the sprite to draw
     * @param tint   an optional tint override, or {@code null} to use the sprite's own color
     * @throws NullPointerException if {@code sprite} is {@code null}
     */
    public void draw(Sprite sprite, Color tint) {
        Objects.requireNonNull(sprite, "Sprite cannot be null");

        TextureRegion region = sprite.getRegion();

        float u0 = region.getU();
        float v0 = region.getV();
        float u1 = region.getU2();
        float v1 = region.getV2();

        if (sprite.isFlippedX()) {
            float temp = u0;
            u0 = u1;
            u1 = temp;
        }

        if (sprite.isFlippedY()) {
            float temp = v0;
            v0 = v1;
            v1 = temp;
        }

        float scaleX = sprite.getScaleX();
        float scaleY = sprite.getScaleY();
        float w = sprite.getWidth() * scaleX;
        float h = sprite.getHeight() * scaleY;

        float rad = (float) Math.toRadians(-sprite.getRotation());
        float sin = (float) Math.sin(rad);
        float cos = (float) Math.cos(rad);

        Color drawColor = tint != null ? tint : sprite.getColor();

        drawUV(region.getTexture(), sprite.getX(), sprite.getY(), w, h, u0, v0, u1, v1, sprite.getRotationOrigin().getX(), sprite.getRotationOrigin().getY(), sin, cos, drawColor);
    }

    /**
     * <p>
     * Queues a {@link Sprite} for rendering at a custom position using the sprite's
     * original width and height.
     * </p>
     *
     * @param sprite the sprite to draw
     * @param x      the destination X position
     * @param y      the destination Y position
     * @throws NullPointerException if {@code sprite} is {@code null}
     */
    public void draw(Sprite sprite, float x, float y) {
        Objects.requireNonNull(sprite, "Sprite cannot be null");
        draw(sprite, x, y, sprite.getWidth(), sprite.getHeight(), null);
    }

    /**
     * <p>
     * Queues a {@link Sprite} for rendering at a custom position using the sprite's
     * original width and height and an optional tint override.
     * </p>
     *
     * @param sprite the sprite to draw
     * @param x      the destination X position
     * @param y      the destination Y position
     * @param tint   an optional tint override
     * @throws NullPointerException if {@code sprite} is {@code null}
     */
    public void draw(Sprite sprite, float x, float y, Color tint) {
        Objects.requireNonNull(sprite, "Sprite cannot be null");
        draw(sprite, x, y, sprite.getWidth(), sprite.getHeight(), tint);
    }

    /**
     * <p>
     * Queues a {@link Sprite} for rendering at a custom position and custom size.
     * </p>
     *
     * @param sprite the sprite to draw
     * @param x      the destination X position
     * @param y      the destination Y position
     * @param width  the destination width
     * @param height the destination height
     * @throws NullPointerException if {@code sprite} is {@code null}
     */
    public void draw(Sprite sprite, float x, float y, float width, float height) {
        Objects.requireNonNull(sprite, "Sprite cannot be null");
        draw(sprite, x, y, width, height, null);
    }

    /**
     * <p>
     * Queues a {@link Sprite} for rendering at a custom position and size with an
     * optional tint override.
     * </p>
     *
     * <p>
     * This overload recalculates the effective rotation origin based on how the
     * destination size compares to the sprite's original size. That allows rotated
     * rendering to remain visually correct even when the sprite is stretched or shrunk.
     * </p>
     *
     * @param sprite the sprite to draw
     * @param x      the destination X position
     * @param y      the destination Y position
     * @param width  the destination width
     * @param height the destination height
     * @param tint   an optional tint override, or {@code null} to use the sprite's color
     * @throws NullPointerException if {@code sprite} is {@code null}
     */
    public void draw(Sprite sprite, float x, float y, float width, float height, Color tint) {
        Objects.requireNonNull(sprite, "Sprite cannot be null");

        TextureRegion region = sprite.getRegion();

        float u0 = region.getU();
        float v0 = region.getV();
        float u1 = region.getU2();
        float v1 = region.getV2();

        if (sprite.isFlippedX()) {
            float temp = u0;
            u0 = u1;
            u1 = temp;
        }

        if (sprite.isFlippedY()) {
            float temp = v0;
            v0 = v1;
            v1 = temp;
        }

        float baseWidth = sprite.getWidth();
        float baseHeight = sprite.getHeight();

        float scaleX = baseWidth == 0f ? 1f : width / baseWidth;
        float scaleY = baseHeight == 0f ? 1f : height / baseHeight;

        float originX = sprite.getRotationOrigin().getX() * scaleX;
        float originY = sprite.getRotationOrigin().getY() * scaleY;

        float rad = (float) Math.toRadians(-sprite.getRotation());
        float sin = (float) Math.sin(rad);
        float cos = (float) Math.cos(rad);

        Color drawColor = tint != null ? tint : sprite.getColor();

        drawUV(region.getTexture(), x, y, width, height, u0, v0, u1, v1, originX, originY, sin, cos, drawColor);
    }

    /**
     * <p>
     * Queues a {@link NinePatchTexture} using the texture's own position and size.
     * </p>
     *
     * <p>
     * This overload delegates to
     * {@link #draw(NinePatchTexture, float, float, float, float)}.
     * </p>
     *
     * @param texture the nine-patch texture to draw
     */
    public void draw(NinePatchTexture texture) {
        draw(texture, texture.getX(), texture.getY(), texture.getWidth(), texture.getHeight());
    }

    /**
     * <p>
     * Queues a {@link NinePatchTexture} for rendering at the requested destination
     * rectangle.
     * </p>
     *
     * <p>
     * The source region is divided into a 3x3 grid representing fixed-size corners,
     * stretchable edges, and a stretchable center. Each visible patch is translated
     * into its own draw call with correct UVs, optional flips, rotation origin
     * adjustment, and tint. Empty patches caused by zero size are skipped.
     * </p>
     *
     * @param texture the nine-patch texture to draw
     * @param x       the destination X position
     * @param y       the destination Y position
     * @param width   the destination width
     * @param height  the destination height
     * @throws NullPointerException if {@code texture} is {@code null}
     */
    public void draw(NinePatchTexture texture, float x, float y, float width, float height) {
        Objects.requireNonNull(texture, "NinePatchTexture cannot be null");

        TextureData data = texture.getData();
        if (data == null) {
            return;
        }

        float texW = data.width();
        float texH = data.height();

        if (texW <= 0f || texH <= 0f) {
            return;
        }

        TextureRegion region = texture.getRegion();

        float baseU0 = Math.min(region.getU(), region.getU2());
        float baseU1 = Math.max(region.getU(), region.getU2());
        float baseV0 = Math.min(region.getV(), region.getV2());
        float baseV1 = Math.max(region.getV(), region.getV2());

        boolean flipX = (region.getU() > region.getU2()) ^ texture.isFlippedX();
        boolean flipY = (region.getV() > region.getV2()) ^ texture.isFlippedY();

        float left = texture.getLeft();
        float right = texture.getRight();
        float top = texture.getTop();
        float bottom = texture.getBottom();

        float centerW = Math.max(0f, width - left - right);
        float centerH = Math.max(0f, height - top - bottom);

        float[] dstX = {0f, left, left + centerW};
        float[] dstY = {0f, bottom, bottom + centerH};
        float[] dstW = {left, centerW, right};
        float[] dstH = {bottom, centerH, top};

        float srcX0 = 0f;
        float srcX2 = texW - right;

        float srcY0 = 0f;
        float srcY2 = texH - top;

        float[] srcXs = {srcX0, left, srcX2, texW};
        float[] srcYs = {srcY0, bottom, srcY2, texH};

        float originX = texture.getRotationOrigin().getX();
        float originY = texture.getRotationOrigin().getY();

        float rad = (float) Math.toRadians(-texture.getRotation());
        float sin = (float) Math.sin(rad);
        float cos = (float) Math.cos(rad);

        Color tint = texture.getColor();
        Texture base = texture.getTexture();

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                float patchW = dstW[col];
                float patchH = dstH[row];

                if (patchW <= 0f || patchH <= 0f) {
                    continue;
                }

                float localX = dstX[col];
                float localY = dstY[row];

                float rx0 = srcXs[col] / texW;
                float rx1 = srcXs[col + 1] / texW;
                float ry0 = srcYs[row] / texH;
                float ry1 = srcYs[row + 1] / texH;

                float u0 = baseU0 + (baseU1 - baseU0) * rx0;
                float u1 = baseU0 + (baseU1 - baseU0) * rx1;
                float v0 = baseV0 + (baseV1 - baseV0) * ry0;
                float v1 = baseV0 + (baseV1 - baseV0) * ry1;

                if (flipX) {
                    float t = u0;
                    u0 = u1;
                    u1 = t;
                }

                if (flipY) {
                    float t = v0;
                    v0 = v1;
                    v1 = t;
                }

                drawUV(base, x + localX, y + localY, patchW, patchH, u0, v0, u1, v1, originX - localX, originY - localY, sin, cos, tint);
            }
        }
    }

    /**
     * <p>
     * Queues a full {@link Texture} for rendering with normalized UVs covering the
     * entire image and no additional tint override.
     * </p>
     *
     * @param tex the texture to draw
     * @param x   the destination X position
     * @param y   the destination Y position
     * @param w   the destination width
     * @param h   the destination height
     */
    public void draw(Texture tex, float x, float y, float w, float h) {
        drawUV(tex, x, y, w, h, 0f, 0f, 1f, 1f, 0f, 0f, 0f, 1f, null);
    }

    /**
     * <p>
     * Queues a full {@link Texture} for rendering with normalized UVs covering the
     * entire image and an optional tint override.
     * </p>
     *
     * @param tex  the texture to draw
     * @param x    the destination X position
     * @param y    the destination Y position
     * @param w    the destination width
     * @param h    the destination height
     * @param tint the tint override to apply
     */
    public void draw(Texture tex, float x, float y, float w, float h, Color tint) {
        drawUV(tex, x, y, w, h, 0f, 0f, 1f, 1f, 0f, 0f, 0f, 1f, tint);
    }

    /**
     * <p>
     * Queues a {@link Texture} for rendering using explicit normalized UV bounds.
     * </p>
     *
     * @param tex the texture to draw
     * @param x   the destination X position
     * @param y   the destination Y position
     * @param w   the destination width
     * @param h   the destination height
     * @param u0  the starting U coordinate
     * @param v0  the starting V coordinate
     * @param u1  the ending U coordinate
     * @param v1  the ending V coordinate
     */
    public void draw(Texture tex, float x, float y, float w, float h, float u0, float v0, float u1, float v1) {
        drawUV(tex, x, y, w, h, u0, v0, u1, v1, 0f, 0f, 0f, 1f, null);
    }

    /**
     * <p>
     * Queues a pixel-space source region from a {@link Texture} for rendering without
     * a tint override.
     * </p>
     *
     * <p>
     * The supplied source rectangle is converted into normalized UV coordinates using
     * the texture's metadata.
     * </p>
     *
     * @param tex          the texture to sample from
     * @param x            the destination X position
     * @param y            the destination Y position
     * @param w            the destination width
     * @param h            the destination height
     * @param regionX      the source region X in pixels
     * @param regionY      the source region Y in pixels
     * @param regionWidth  the source region width in pixels
     * @param regionHeight the source region height in pixels
     */
    public void drawRegion(Texture tex, float x, float y, float w, float h, float regionX, float regionY, float regionWidth, float regionHeight) {
        drawRegion(tex, x, y, w, h, regionX, regionY, regionWidth, regionHeight, null);
    }

    /**
     * <p>
     * Queues a pixel-space source region from a {@link Texture} for rendering with an
     * optional tint override.
     * </p>
     *
     * <p>
     * If the texture does not expose valid metadata, the method falls back to drawing
     * the full texture rather than failing. If the texture width or height is zero, it
     * also falls back to full-texture normalized UVs.
     * </p>
     *
     * @param tex          the texture to sample from
     * @param x            the destination X position
     * @param y            the destination Y position
     * @param w            the destination width
     * @param h            the destination height
     * @param regionX      the source region X in pixels
     * @param regionY      the source region Y in pixels
     * @param regionWidth  the source region width in pixels
     * @param regionHeight the source region height in pixels
     * @param tint         the tint override to apply
     * @throws NullPointerException if {@code tex} is {@code null}
     */
    public void drawRegion(Texture tex, float x, float y, float w, float h, float regionX, float regionY, float regionWidth, float regionHeight, Color tint) {
        Objects.requireNonNull(tex, "Texture cannot be null");

        TextureData d = tex.getData();
        if (d == null) {
            drawUV(tex, x, y, w, h, 0f, 0f, 1f, 1f, 0f, 0f, 0f, 1f, tint);
            return;
        }

        float texW = d.width();
        float texH = d.height();

        if (texW == 0f || texH == 0f) {
            drawUV(tex, x, y, w, h, 0f, 0f, 1f, 1f, 0f, 0f, 0f, 1f, tint);
            return;
        }

        float u0 = regionX / texW;
        float v0 = regionY / texH;
        float u1 = (regionX + regionWidth) / texW;
        float v1 = (regionY + regionHeight) / texH;

        drawUV(tex, x, y, w, h, u0, v0, u1, v1, 0f, 0f, 0f, 1f, tint);
    }

    /**
     * <p>
     * Queues a {@link TextureRegion} for rendering without a tint override.
     * </p>
     *
     * <p>
     * This overload resolves the region's source rectangle and delegates to the
     * texture-based {@code drawRegion(...)} overload.
     * </p>
     *
     * @param region the texture region to draw
     * @param x      the destination X position
     * @param y      the destination Y position
     * @param w      the destination width
     * @param h      the destination height
     * @throws NullPointerException if {@code region} is {@code null}
     */
    public void drawRegion(TextureRegion region, float x, float y, float w, float h) {
        Objects.requireNonNull(region, "TextureRegion cannot be null");
        drawRegion(region.getTexture(), x, y, w, h, region.getRegionX(), region.getRegionY(), region.getRegionWidth(), region.getRegionHeight(), null);
    }

    /**
     * <p>
     * Queues a {@link TextureRegion} for rendering with an optional tint override.
     * </p>
     *
     * @param region the texture region to draw
     * @param x      the destination X position
     * @param y      the destination Y position
     * @param w      the destination width
     * @param h      the destination height
     * @param tint   the tint override to apply
     * @throws NullPointerException if {@code region} is {@code null}
     */
    public void drawRegion(TextureRegion region, float x, float y, float w, float h, Color tint) {
        Objects.requireNonNull(region, "TextureRegion cannot be null");
        drawRegion(region.getTexture(), x, y, w, h, region.getRegionX(), region.getRegionY(), region.getRegionWidth(), region.getRegionHeight(), tint);
    }

    /**
     * <p>
     * Queues a texture draw using explicit normalized UV bounds without rotation data.
     * </p>
     *
     * <p>
     * This overload is a convenience wrapper that forwards to the more complete
     * rotation-aware UV draw method with a zero origin and identity rotation.
     * </p>
     *
     * @param tex  the texture to draw
     * @param x    the destination X position
     * @param y    the destination Y position
     * @param w    the destination width
     * @param h    the destination height
     * @param u0   the starting U coordinate
     * @param v0   the starting V coordinate
     * @param u1   the ending U coordinate
     * @param v1   the ending V coordinate
     * @param tint the tint override to apply
     */
    public void drawUV(Texture tex, float x, float y, float w, float h, float u0, float v0, float u1, float v1, Color tint) {
        drawUV(tex, x, y, w, h, u0, v0, u1, v1, 0f, 0f, 0f, 1f, tint);
    }

    /**
     * <p>
     * Queues a texture draw using explicit normalized UV bounds, an arbitrary rotation
     * origin, precomputed rotation sine and cosine values, and an optional tint.
     * </p>
     *
     * <p>
     * This is the core draw path used by most other draw overloads. It validates batch
     * state, flushes if instance or texture-unit limits are reached, resolves the
     * correct texture unit for the texture, selects the final tint color, applies the
     * current translation stack, writes all instance attributes into the internal buffer,
     * and increments the queued instance count.
     * </p>
     *
     * @param tex     the texture to draw
     * @param x       the destination X position
     * @param y       the destination Y position
     * @param w       the destination width
     * @param h       the destination height
     * @param u0      the starting U coordinate
     * @param v0      the starting V coordinate
     * @param u1      the ending U coordinate
     * @param v1      the ending V coordinate
     * @param originX the rotation origin X relative to the destination quad
     * @param originY the rotation origin Y relative to the destination quad
     * @param sinRot  the sine of the desired rotation angle
     * @param cosRot  the cosine of the desired rotation angle
     * @param tint    the tint override to apply, or {@code null} to use the batch color
     * @throws IllegalStateException if the batch is not currently drawing
     * @throws NullPointerException  if {@code tex} is {@code null}
     */
    public void drawUV(Texture tex, float x, float y, float w, float h, float u0, float v0, float u1, float v1, float originX, float originY, float sinRot, float cosRot, Color tint) {
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

        x += translationX;
        y += translationY;

        instanceBuffer.put(x).put(y).put(w).put(h);
        instanceBuffer.put(r).put(g).put(b).put(a);
        instanceBuffer.put((float) texUnit);
        instanceBuffer.put(u0).put(v0).put(u1).put(v1);
        instanceBuffer.put(originX).put(originY);
        instanceBuffer.put(sinRot).put(cosRot);

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
     * <p>
     * Ends the current batch, flushes any remaining queued instances, restores OpenGL
     * state modified by {@link #begin()} and unbinds textures and shaders used by the
     * batch.
     * </p>
     *
     * <p>
     * This method also resets clip and translation state, restores the previous
     * framebuffer and viewport when rendering to an FBO, and clears texture cache state
     * so the next batch begins cleanly.
     * </p>
     *
     * @throws IllegalStateException if the batch is not currently active
     */
    public void end() {
        if (!drawing) throw new IllegalStateException("Call begin() before end().");

        flush();

        glVertexAttribDivisor(TextureBatchContract.ATTR_XYWH, 0);
        glVertexAttribDivisor(TextureBatchContract.ATTR_COL, 0);
        glVertexAttribDivisor(TextureBatchContract.ATTR_TEX, 0);
        glVertexAttribDivisor(TextureBatchContract.ATTR_UVRECT, 0);
        glVertexAttribDivisor(TextureBatchContract.ATTR_ORIGIN, 0);
        glVertexAttribDivisor(TextureBatchContract.ATTR_ROT, 0);
        glVertexAttribDivisor(TextureBatchContract.ATTR_CLIPRECT, 0);
        glVertexAttribDivisor(TextureBatchContract.ATTR_CLIPENABLED, 0);

        glDisableVertexAttribArray(TextureBatchContract.ATTR_LOCAL);
        glDisableVertexAttribArray(TextureBatchContract.ATTR_UV);
        glDisableVertexAttribArray(TextureBatchContract.ATTR_XYWH);
        glDisableVertexAttribArray(TextureBatchContract.ATTR_COL);
        glDisableVertexAttribArray(TextureBatchContract.ATTR_TEX);
        glDisableVertexAttribArray(TextureBatchContract.ATTR_UVRECT);
        glDisableVertexAttribArray(TextureBatchContract.ATTR_ORIGIN);
        glDisableVertexAttribArray(TextureBatchContract.ATTR_ROT);
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
        translationDepth = 0;
        translationX = 0f;
        translationY = 0f;

        if (usingFBO) {
            glBindFramebuffer(GL_FRAMEBUFFER, previousFBO);
            glViewport(prevViewportX, prevViewportY, prevViewportW, prevViewportH);
            usingFBO = false;
        }
    }

    /**
     * <p>
     * Flushes queued sprite instances to the GPU if any are currently buffered.
     * </p>
     *
     * <p>
     * The instance buffer is flipped, uploaded into the instance VBO, and then rendered
     * with a single instanced draw call. After rendering, the CPU-side buffer and all
     * texture cache bookkeeping are reset so new instances can be queued.
     * </p>
     *
     * <p>
     * Calling this method while no instances are queued is safe and returns immediately.
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
     * <p>
     * Releases native and OpenGL resources owned by this batch.
     * </p>
     *
     * <p>
     * This disposes the internally owned default shader and deletes the quad and
     * instance VBOs. After this method is called, the batch should no longer be used.
     * </p>
     */
    public void dispose() {
        defaultShader.dispose();
        glDeleteBuffers(quadVBO);
        glDeleteBuffers(instanceVBO);
    }

    /**
     * <p>
     * Binds the active shader and configures all required vertex attribute pointers for
     * both the shared quad geometry and the per-instance attribute stream.
     * </p>
     *
     * <p>
     * This method is used when a batch begins and whenever the active shader changes
     * while drawing. It assumes the active shader already exposes the attribute layout
     * defined by {@link TextureBatchContract}.
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

        glEnableVertexAttribArray(TextureBatchContract.ATTR_ORIGIN);
        glVertexAttribPointer(TextureBatchContract.ATTR_ORIGIN, 2, GL_FLOAT, false, TextureBatchContract.INST_STRIDE_BYTES, 13L * TextureBatchContract.BYTES_PER_FLOAT);
        glVertexAttribDivisor(TextureBatchContract.ATTR_ORIGIN, 1);

        glEnableVertexAttribArray(TextureBatchContract.ATTR_ROT);
        glVertexAttribPointer(TextureBatchContract.ATTR_ROT, 2, GL_FLOAT, false, TextureBatchContract.INST_STRIDE_BYTES, 15L * TextureBatchContract.BYTES_PER_FLOAT);
        glVertexAttribDivisor(TextureBatchContract.ATTR_ROT, 1);

        glEnableVertexAttribArray(TextureBatchContract.ATTR_CLIPRECT);
        glVertexAttribPointer(TextureBatchContract.ATTR_CLIPRECT, 4, GL_FLOAT, false, TextureBatchContract.INST_STRIDE_BYTES, 17L * TextureBatchContract.BYTES_PER_FLOAT);
        glVertexAttribDivisor(TextureBatchContract.ATTR_CLIPRECT, 1);

        glEnableVertexAttribArray(TextureBatchContract.ATTR_CLIPENABLED);
        glVertexAttribPointer(TextureBatchContract.ATTR_CLIPENABLED, 1, GL_FLOAT, false, TextureBatchContract.INST_STRIDE_BYTES, 21L * TextureBatchContract.BYTES_PER_FLOAT);
        glVertexAttribDivisor(TextureBatchContract.ATTR_CLIPENABLED, 1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * <p>
     * Returns the texture unit index for the given texture ID, binding the texture into
     * the current batch if necessary.
     * </p>
     *
     * <p>
     * The method first checks the last bound texture cache for fast repeated access,
     * then scans the currently active texture unit array. If the texture is not present
     * and capacity is available, it binds the texture into the next free texture unit.
     * If no further texture units are available, {@code -1} is returned so the caller
     * can flush and retry.
     * </p>
     *
     * @param textureID the OpenGL texture ID to resolve
     * @return the texture unit index, or {@code -1} if the batch must flush first
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
     * <p>
     * Expands the internal texture unit cache capacity.
     * </p>
     *
     * <p>
     * The capacity grows by doubling when possible, while still respecting the maximum
     * allowed texture unit count configured for this batch.
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
     * <p>
     * Detects the number of supported texture image units from the current OpenGL
     * context and clamps the result to a caller-provided maximum.
     * </p>
     *
     * <p>
     * If the driver reports an invalid value less than or equal to zero, the method
     * falls back to eight texture units as a conservative default.
     * </p>
     *
     * @param clampTo the maximum allowed texture unit count
     * @return the detected and clamped texture unit count
     */
    private static int detectTextureUnitsClamped(int clampTo) {
        int units = glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS);
        if (units <= 0) {
            units = 8;
        }
        return Math.min(units, clampTo);
    }
}