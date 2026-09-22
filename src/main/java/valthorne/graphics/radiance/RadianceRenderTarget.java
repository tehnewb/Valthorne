package valthorne.graphics.radiance;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.*;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;

/**
 * Owns an RGBA16F texture and, optionally, a color-only framebuffer used by the
 * radiance pipeline. All resource operations require the owning OpenGL context.
 * Begin/end save one framebuffer and viewport pair, so calls must be balanced and
 * must not nest on the same target. Other graphics state is not generally restored.
 *
 * <p>Resizing replaces storage and invalidates previously returned texture wrappers.
 * Wrappers borrow the target's texture ID and must not be disposed independently.
 * This helper has no closed-state guard; dispose after use and do not render again.</p>
 *
 * @author Albert Beaupre
 */
final class RadianceRenderTarget {

    private final boolean renderable; // Whether a color framebuffer accompanies the texture.
    private final boolean linear; // Filter choice used whenever texture storage is created.
    private final int[] previousViewport = new int[4]; // Single saved viewport rectangle for balanced begin/end.
    private int width; // Current storage width in pixels.
    private int height; // Current storage height in pixels.
    private int textureID; // Owned OpenGL texture name, or zero after disposal.
    private int framebufferID; // Owned framebuffer name, or zero for texture-only/disposed targets.
    private RadianceTexture texture; // Borrowed-facing texture wrapper, cleared on disposal.
    private int previousFramebuffer; // Framebuffer binding saved by the latest begin call.

    /**
     * Allocates floating-point texture storage and an optional framebuffer immediately.
     * Creation changes texture binding and binds framebuffer zero after creating an FBO.
     *
     * @param width positive storage width in pixels
     * @param height positive storage height in pixels
     * @param renderable whether to create a color framebuffer
     * @param linear whether texture sampling uses linear instead of nearest filtering
     * @throws IllegalArgumentException if a dimension is nonpositive
     * @throws IllegalStateException if the framebuffer is incomplete
     */
    RadianceRenderTarget(int width, int height, boolean renderable, boolean linear) {
        if (width <= 0) throw new IllegalArgumentException("width must be > 0");
        if (height <= 0) throw new IllegalArgumentException("height must be > 0");
        this.width = width;
        this.height = height;
        this.renderable = renderable;
        this.linear = linear;
        create();
    }

    /**
     * Allocates clamp-to-edge RGBA16F texture storage, wraps it, and optionally attaches
     * it to a framebuffer. An incomplete framebuffer triggers disposal and an exception.
     * Does not preserve incoming texture or framebuffer bindings.
     *
     * @throws IllegalStateException if the created framebuffer is incomplete
     */
    private void create() {
        textureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureID);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, linear ? GL_LINEAR : GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, linear ? GL_LINEAR : GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height, 0, GL_RGBA, GL_FLOAT, (ByteBuffer) null);
        texture = new RadianceTexture(textureID, width, height, linear);
        if (renderable) {
            framebufferID = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, framebufferID);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureID, 0);
            int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            if (status != GL_FRAMEBUFFER_COMPLETE) {
                dispose();
                throw new IllegalStateException("Incomplete framebuffer for radiance target: " + status);
            }
        }
    }

    /**
     * Saves the current framebuffer and viewport, then binds this target with a full-size
     * viewport. Does not clear contents or save other graphics state. Pair with end and
     * do not nest begins on this same instance.
     *
     * @throws IllegalStateException if this target has no framebuffer
     */
    void begin() {
        if (!renderable) throw new IllegalStateException("Target is not renderable");
        previousFramebuffer = glGetInteger(GL_FRAMEBUFFER_BINDING);
        glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);
        glBindFramebuffer(GL_FRAMEBUFFER, framebufferID);
        glViewport(0, 0, width, height);
    }

    /**
     * Restores the framebuffer and viewport saved by the latest begin. It does not track
     * whether begin occurred, so an unmatched end restores stale or initial values.
     *
     * @throws IllegalStateException if this target has no framebuffer
     */
    void end() {
        if (!renderable) throw new IllegalStateException("Target is not renderable");
        glBindFramebuffer(GL_FRAMEBUFFER, previousFramebuffer);
        glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
    }

    /**
     * Temporarily binds a renderable target and clears its color attachment, restoring
     * the saved framebuffer and viewport afterward. Texture-only targets do nothing.
     * Leaves the OpenGL clear color changed and uses the caller's other clear state;
     * do not invoke inside an active begin/end pair on this same instance.
     *
     * @param r red clear component
     * @param g green clear component
     * @param b blue clear component
     * @param a alpha clear component
     */
    void clear(float r, float g, float b, float a) {
        if (renderable) {
            begin();
            glClearColor(r, g, b, a);
            glClear(GL_COLOR_BUFFER_BIT);
            end();
        }
    }

    /**
     * Recreates storage when either positive dimension changes, discarding prior contents
     * and invalidating texture wrappers. Equal dimensions are a no-op, including after
     * disposal; recreation failures occur after old resources have been released.
     *
     * @param width positive replacement width in pixels
     * @param height positive replacement height in pixels
     * @throws IllegalArgumentException if either dimension is nonpositive
     */
    void resize(int width, int height) {
        if (width <= 0) throw new IllegalArgumentException("width must be > 0");
        if (height <= 0) throw new IllegalArgumentException("height must be > 0");
        if (this.width == width && this.height == height) return;
        dispose();
        this.width = width;
        this.height = height;
        create();
    }

    /**
     * Deletes nonzero framebuffer and texture names and clears the wrapper reference.
     * Repeated calls perform no further deletion. Dimensions are retained and wrappers
     * previously obtained from this object must no longer be used.
     */
    void dispose() {
        if (framebufferID != 0) {
            glDeleteFramebuffers(framebufferID);
            framebufferID = 0;
        }
        if (textureID != 0) {
            glDeleteTextures(textureID);
            textureID = 0;
        }
        texture = null;
    }

    /**
     * Returns the retained storage width, including after disposal.
     *
     * @return width in pixels
     */
    int getWidth() {
        return width;
    }

    /**
     * Returns the retained storage height, including after disposal.
     *
     * @return height in pixels
     */
    int getHeight() {
        return height;
    }

    /**
     * Returns the current texture name without binding it or transferring ownership.
     *
     * @return OpenGL texture name, or zero after disposal
     */
    int getTextureID() {
        return textureID;
    }

    /**
     * Returns the optional framebuffer name without binding it.
     *
     * @return framebuffer name, or zero for texture-only or disposed storage
     */
    int getFramebufferID() {
        return framebufferID;
    }

    /**
     * Returns the current borrowed wrapper. Do not dispose it separately; resize or
     * disposal of this target invalidates the returned wrapper.
     *
     * @return live wrapper, or null after disposal
     */
    RadianceTexture getTexture() {
        return texture;
    }
}
