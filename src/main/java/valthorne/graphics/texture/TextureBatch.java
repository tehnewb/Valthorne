package valthorne.graphics.texture;

import org.lwjgl.BufferUtils;
import valthorne.graphics.Color;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Batches multiple {@link Texture} draws into fewer OpenGL draw calls using the fixed-function pipeline.
 *
 * <p>This class is designed for legacy OpenGL rendering (client-side arrays + {@code glDrawArrays(GL_QUADS)}).
 * It collects quad vertices, UVs, and per-vertex colors into large CPU-side {@link FloatBuffer}s and issues
 * a single draw call for many sprites at once.</p>
 *
 * <h2>Batching rules</h2>
 * <ul>
 *     <li>All sprites in a single flush must share the same OpenGL texture id.</li>
 *     <li>If you call {@link #draw(Texture)} with a different texture id, the batch will {@link #flush()} automatically.</li>
 *     <li>If the batch reaches {@link #maxSprites}, it will {@link #flush()} automatically.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * TextureBatch batch = new TextureBatch(5000);
 *
 * batch.begin();
 * for (Texture t : sprites) {
 *     // Make sure each Texture has already had setPosition/setSize/setRegion/etc. applied.
 *     batch.draw(t);
 * }
 * batch.end();   // flushes anything remaining
 *
 * batch.dispose();
 * }</pre>
 *
 * <h2>Important notes</h2>
 * <ul>
 *     <li>This class does not enable/disable OpenGL states. You are expected to have the correct
 *         client states enabled (vertex array, texcoord array, color array, texture 2D) and a valid projection.</li>
 *     <li>{@link Texture} is treated as a snapshot source: this reads its current {@link Texture#getVertexBuffer()},
 *         {@link Texture#getUVBuffer()}, and {@link Texture#getColor()} values at the time of {@link #draw(Texture)}.</li>
 *     <li>{@link #dispose()} only clears CPU buffers (and state). It does not delete any OpenGL textures.</li>
 * </ul>
 *
 * @author Albert Beaupre
 * @since February 8th, 2026
 */
public final class TextureBatch {

    private static final int VERTS_PER_SPRITE = 4;                          // Number of vertices used per sprite quad.
    private static final int POS_FLOATS_PER_SPRITE = VERTS_PER_SPRITE * 2;  // Position floats per sprite (x,y) * 4 = 8.
    private static final int UV_FLOATS_PER_SPRITE = VERTS_PER_SPRITE * 2;   // UV floats per sprite (u,v) * 4 = 8.
    private static final int COLOR_FLOATS_PER_SPRITE = VERTS_PER_SPRITE * 4; // Color floats per sprite (r,g,b,a) * 4 = 16.

    private final int maxSprites;                                           // Maximum number of sprites buffered before an automatic flush.

    private FloatBuffer posBuffer;                                          // Interleaved sprite positions: x,y,x,y... for 4 vertices per sprite.
    private FloatBuffer uvBuffer;                                           // Interleaved sprite UVs: u,v,u,v... for 4 vertices per sprite.
    private FloatBuffer colorBuffer;                                        // Per-vertex RGBA colors: r,g,b,a repeated for 4 vertices per sprite.

    private int spriteCount = 0;                                            // Number of sprites currently accumulated in this batch.
    private int currentTextureID = 0;                                       // OpenGL texture id that all currently batched sprites must share.
    private boolean drawing = false;                                        // Whether begin() has been called and the batch is currently accepting draws.

    /**
     * Creates a {@code TextureBatch} with a default capacity of 1,000 sprites.
     *
     * <p>This pre-allocates CPU-side buffers sized to that maximum sprite count.</p>
     */
    public TextureBatch() {
        this(1000);
    }

    /**
     * Creates a {@code TextureBatch} with a specified maximum sprite capacity.
     *
     * <p>This pre-allocates CPU-side buffers large enough to hold:</p>
     * <ul>
     *     <li>{@code maxSprites * 8} floats for position data</li>
     *     <li>{@code maxSprites * 8} floats for UV data</li>
     *     <li>{@code maxSprites * 16} floats for color data</li>
     * </ul>
     *
     * @param maxSprites maximum sprites buffered before an automatic flush
     * @throws IllegalArgumentException if {@code maxSprites <= 0}
     */
    public TextureBatch(int maxSprites) {
        if (maxSprites <= 0) throw new IllegalArgumentException("maxSprites must be > 0");
        this.maxSprites = maxSprites;

        this.posBuffer = BufferUtils.createFloatBuffer(maxSprites * POS_FLOATS_PER_SPRITE);
        this.uvBuffer = BufferUtils.createFloatBuffer(maxSprites * UV_FLOATS_PER_SPRITE);
        this.colorBuffer = BufferUtils.createFloatBuffer(maxSprites * COLOR_FLOATS_PER_SPRITE);
    }

    /**
     * Returns the maximum number of sprites this batch can buffer before an automatic flush.
     *
     * @return maximum buffered sprite capacity
     */
    public int getMaxSprites() {
        return maxSprites;
    }

    /**
     * Begins a new batch.
     *
     * <p>This clears internal buffers and resets counters. After calling this method,
     * you may call {@link #draw(Texture)} repeatedly until {@link #end()}.</p>
     *
     * @throws IllegalStateException if already drawing (missing {@link #end()})
     */
    public void begin() {
        if (drawing) throw new IllegalStateException("TextureBatch is already drawing (did you forget end()?)");
        drawing = true;

        spriteCount = 0;
        currentTextureID = 0;

        posBuffer.clear();
        uvBuffer.clear();
        colorBuffer.clear();
    }

    /**
     * Adds a sprite quad to the current batch using the current state of a {@link Texture}.
     *
     * <p>This reads from:</p>
     * <ul>
     *     <li>{@link Texture#getVertexBuffer()} for world-space quad vertices</li>
     *     <li>{@link Texture#getUVBuffer()} for UV coordinates</li>
     *     <li>{@link Texture#getColor()} for per-vertex tint color</li>
     * </ul>
     *
     * <p>Batching constraints:</p>
     * <ul>
     *     <li>If this is the first sprite in the batch, the batch texture id becomes {@code tex}'s id.</li>
     *     <li>If {@code tex}'s id differs from the current batch texture id, the batch is {@link #flush()}ed automatically.</li>
     *     <li>If the batch reaches {@link #maxSprites}, the batch is {@link #flush()}ed automatically.</li>
     * </ul>
     *
     * @param tex the texture quad to draw (acts as a state snapshot source)
     * @throws IllegalStateException if {@link #begin()} has not been called
     * @throws NullPointerException  if {@code tex} is null
     */
    public void draw(Texture tex) {
        if (!drawing) throw new IllegalStateException("Call begin() before draw()");
        if (tex == null) throw new NullPointerException("Texture cannot be null");

        int texID = tex.getTextureID();

        if (spriteCount == 0) currentTextureID = texID;

        if (texID != currentTextureID) {
            flush();
            currentTextureID = texID;
        }

        if (spriteCount >= maxSprites) {
            flush();
            currentTextureID = texID;
        }

        FloatBuffer vb = tex.getVertexBuffer();
        posBuffer.put(vb.get(0)).put(vb.get(1));
        posBuffer.put(vb.get(2)).put(vb.get(3));
        posBuffer.put(vb.get(4)).put(vb.get(5));
        posBuffer.put(vb.get(6)).put(vb.get(7));

        FloatBuffer ub = tex.getUVBuffer();
        uvBuffer.put(ub.get(0)).put(ub.get(1));
        uvBuffer.put(ub.get(2)).put(ub.get(3));
        uvBuffer.put(ub.get(4)).put(ub.get(5));
        uvBuffer.put(ub.get(6)).put(ub.get(7));

        Color c = tex.getColor();
        float r = c.r(), g = c.g(), b = c.b(), a = c.a();
        for (int i = 0; i < VERTS_PER_SPRITE; i++) {
            colorBuffer.put(r).put(g).put(b).put(a);
        }

        spriteCount++;
    }

    /**
     * Ends the current batch and flushes any remaining sprites.
     *
     * @throws IllegalStateException if {@link #begin()} was not called
     */
    public void end() {
        if (!drawing) throw new IllegalStateException("Call begin() before end()");
        flush();
        drawing = false;
    }

    /**
     * Flushes the batch: uploads the buffered arrays to OpenGL client pointers and issues one draw call.
     *
     * <p>If there are no sprites buffered, this method simply clears the buffers and returns.</p>
     *
     * <p>Draw call performed:</p>
     * <pre>{@code
     * glBindTexture(GL_TEXTURE_2D, currentTextureID);
     * glVertexPointer(2, GL_FLOAT, 0, posBuffer);
     * glTexCoordPointer(2, GL_FLOAT, 0, uvBuffer);
     * glColorPointer(4, GL_FLOAT, 0, colorBuffer);
     * glDrawArrays(GL_QUADS, 0, spriteCount * 4);
     * }</pre>
     *
     * <p>After flushing, internal buffers are cleared and {@link #spriteCount} resets to 0.</p>
     */
    public void flush() {
        if (spriteCount == 0) {
            posBuffer.clear();
            uvBuffer.clear();
            colorBuffer.clear();
            return;
        }

        posBuffer.flip();
        uvBuffer.flip();
        colorBuffer.flip();

        glBindTexture(GL_TEXTURE_2D, currentTextureID);
        glVertexPointer(2, GL_FLOAT, 0, posBuffer);
        glTexCoordPointer(2, GL_FLOAT, 0, uvBuffer);
        glColorPointer(4, GL_FLOAT, 0, colorBuffer);
        glDrawArrays(GL_QUADS, 0, spriteCount * VERTS_PER_SPRITE);

        posBuffer.clear();
        uvBuffer.clear();
        colorBuffer.clear();
        spriteCount = 0;
    }

    /**
     * Clears references to internal buffers and resets state.
     *
     * <p>This does not delete any OpenGL textures. It only makes this batch unusable unless you
     * reinitialize it.</p>
     */
    public void dispose() {
        posBuffer = null;
        uvBuffer = null;
        colorBuffer = null;
        spriteCount = 0;
        currentTextureID = 0;
        drawing = false;
    }
}