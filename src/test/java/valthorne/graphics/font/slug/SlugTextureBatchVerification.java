package valthorne.graphics.font.slug;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import valthorne.Window;
import valthorne.graphics.Color;
import valthorne.graphics.font.SystemFonts;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureBatch;
import valthorne.graphics.texture.TextureData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL21.GL_SRGB8_ALPHA8;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM;

/**
 * Native readback regression for live curves submitted through TextureBatch.
 * A hidden GLFW context compares immediate, retained and small-capacity draws at
 * multiple zooms, on linear and sRGB attachments, and under rotated/nonuniform
 * projections. Mixed sprite/text draws exercise painter order, clipping,
 * translation, opacity, queue capacity and renderer state restoration.
 * Run with verifySlugTextureBatch; no visible window or editor is required.
 */
public final class SlugTextureBatchVerification {
    /*
     * Readback dimensions cover the same 15-em text at up to eightfold zoom.
     */
    private static final int WIDTH = 640;
    /*
     * Target height leaves space for clipped and transformed glyphs.
     */
    private static final int HEIGHT = 192;
    private final SlugFont font; // Borrowed font used by all comparison paths.
    private final TextureBatch batch = new TextureBatch(256); // Ordinary mixed renderer under test.
    private final TextureBatch smallBatch = new TextureBatch(2); // Forces glyph capacity flushes.
    private final Texture white; // One-pixel sprite used to test draw ordering and tint.
    private final Matrix4f projection = new Matrix4f(); // Projection applied identically to both paths.

    /**
     * Allocates the native fixtures with a current graphics context.
     *
     * @param font borrowed live outline font
     */
    private SlugTextureBatchVerification(SlugFont font) {
        this.font = font;
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        pixel.putInt(-1).flip();
        white = new Texture(new TextureData(pixel, 1, 1));
    }

    /**
     * Creates a hidden context and runs actual GPU comparisons before disposing it.
     *
     * @param arguments optional test font path
     * @throws Exception if an output image cannot be written
     */
    public static void main(String[] arguments) throws Exception {
        /*
         * Direct GLFW setup avoids dependencies on scene/editor initialization
         * and tests the same OpenGL shader path used by the production batch.
         */
        if (!glfwInit()) throw new IllegalStateException("GLFW initialization failed");
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        long window = glfwCreateWindow(WIDTH, HEIGHT, "Slug TextureBatch verification", 0, 0);
        if (window == 0) throw new IllegalStateException("Hidden context creation failed");
        try {
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            String path = arguments.length == 0 ? SystemFonts.find(false).toString() : arguments[0];
            SlugFont font = SlugFont.load(new SlugLoader().load(SlugParameters.fromPath(path)));
            SlugTextureBatchVerification verification = new SlugTextureBatchVerification(font);
            try {
                verification.verify(GL_RGBA8);
                verification.verify(GL_SRGB8_ALPHA8);
                verification.verifyLifecycle();
                check(glGetError() == GL_NO_ERROR, "OpenGL error after rendering");
                System.out.println("Slug TextureBatch verified: equivalent direct/retained curves at 1x/1.75x/4x/8x, rotation, nonuniform scale, sRGB, mixed order, clipping, opacity, capacity and lifecycle.");
            } finally {
                verification.dispose();
                font.dispose();
            }
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }

    /**
     * Compares both APIs in an explicit framebuffer with known color encoding.
     *
     * @param format RGBA8 or SRGB8_ALPHA8 attachment format
     * @throws Exception if the diagnostic image cannot be written
     */
    private void verify(int format) throws Exception {
        int target = glGenTextures();
        int framebuffer = glGenFramebuffers();
        glBindTexture(GL_TEXTURE_2D, target);
        glTexImage2D(GL_TEXTURE_2D, 0, format, WIDTH, HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target, 0);
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "Incomplete test framebuffer");
        try {
            float[] zooms = {1f, 1.75f, 4f, 8f};
            for (float zoom : zooms) {
                projection.setOrtho2D(0f, WIDTH / zoom, 0f, HEIGHT / zoom);
                for (int mode = 0; mode < 5; mode++) {
                    ByteBuffer expected = render(false, mode);
                    ByteBuffer actual = render(true, mode);
                    equal(expected, actual, "zoom=" + zoom + ", mode=" + mode + ", format=" + format);
                    if (zoom == 8f && mode == 0 && format == GL_RGBA8)
                        save(actual, Path.of("build/slug-verification/live-curves-8x.png"));
                }
            }
            projection.setOrtho2D(0f, WIDTH / 4f, 0f, HEIGHT / 4f);
            projection.rotateZ(.15f);
            equal(render(false, 1), render(true, 1), "Rotated projection");
            projection.scale(1.5f, .75f, 1f);
            equal(render(false, 3), render(true, 3), "Nonuniform projection and mixed drawing");
            glEnable(GL_FRAMEBUFFER_SRGB);
            equal(render(false, 0), render(true, 0), "Initially enabled framebuffer conversion");
            verifyTextTransform();
        } finally {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glDeleteFramebuffers(framebuffer);
            glDeleteTextures(target);
        }
    }

    /**
     * Compares a scoped text rotation with the equivalent camera transform, then
     * checks that clipping cuts the rotated result in final world coordinates.
     */
    private void verifyTextTransform() {
        ByteBuffer expected = renderTextTransform(true, false);
        ByteBuffer actual = renderTextTransform(false, false);
        equal(expected, actual, "Scoped text transform versus camera transform");
        ByteBuffer clipped = renderTextTransform(false, true);
        int lit = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int offset = (y * WIDTH + x) * 4;
                boolean inside = x >= 110 && x < 125 && y >= 30 && y < 115;
                for (int component = 0; component < 3; component++) {
                    int value = clipped.get(offset + component) & 255;
                    check(value == (inside ? actual.get(offset + component) & 255 : 0), "Rotated world clip differs at " + x + ", " + y);
                    if (value != 0) lit++;
                }
            }
        }
        check(lit > 20, "Rotated clip discarded all text");
    }

    /**
     * Renders a quarter-turn with exact coefficients to isolate batch transform
     * and clipping behavior from floating-point matrix composition differences.
     *
     * @param cameraTransform true to put the rotation in the camera projection
     * @param clipped true to apply a final-world rectangular clip
     * @return bottom-up framebuffer pixels
     */
    private ByteBuffer renderTextTransform(boolean cameraTransform, boolean clipped) {
        glViewport(0, 0, WIDTH, HEIGHT);
        glDisable(GL_SCISSOR_TEST);
        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
        projection.setOrtho2D(0f, WIDTH, 0f, HEIGHT);
        if (cameraTransform)
            projection.mul(new Matrix4f().m00(0f).m01(1f).m10(-1f).m11(0f).m30(140f).m31(24f));
        Window.setProjectionMatrix(projection.get(new float[16]));
        batch.begin();
        if (!cameraTransform) batch.setTextTransform(0f, 1f, 140f, 24f);
        if (clipped) batch.beginScissor(110f, 30f, 15f, 85f);
        font.draw(batch, "REEEEE", 4f, 8f, 30f, Color.WHITE);
        if (clipped) batch.endScissor();
        batch.setTextTransform(1f, 0f, 0f, 0f);
        batch.end();
        ByteBuffer pixels = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
        glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return pixels;
    }

    /**
     * Renders one scenario and returns its pixels. Retained layouts and capacity
     * splits are compared against immediate layout in the ordinary texture batch.
     *
     * @param integrated true to exercise retained layout or capacity splitting
     * @param mode direct, retained, ascent/descent sizing, mixed state, or small capacity
     * @return bottom-up RGBA framebuffer pixels
     */
    private ByteBuffer render(boolean integrated, int mode) {
        glViewport(0, 0, WIDTH, HEIGHT);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_STENCIL_TEST);
        glDisable(GL_CULL_FACE);
        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
        Window.setProjectionMatrix(projection.get(new float[16]));
        TextureBatch current = integrated && mode == 4 ? smallBatch : batch;
        long before = current.getTotalDrawCalls();
        current.begin();
        float size = mode == 2 ? 15f / (font.ascent() - font.descent()) : 15f;
        float baseline = mode == 2 ? 3f - font.descent() * size : 8f;
        Color tint = mode == 3 ? new Color(.9f, .7f, .4f, .8f) : Color.WHITE;
        if (mode == 3) {
            current.draw(white, 0, 0, 75, 40, new Color(.1f, .2f, .4f, 1f));
            current.pushTranslation(2f, 1f);
            current.beginScissor(5f, 0f, 45f, 40f);
            current.beginScissor(8f, 2f, 30f, 30f);
            current.setOpacityMultiplier(.5f);
        }
        int spriteProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int spriteVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        boolean srgb = glIsEnabled(GL_FRAMEBUFFER_SRGB);
        if (integrated) {
            if (mode == 1) font.createRun("REEEEE", size).draw(current, 4f, baseline, tint);
            else font.draw(current, "REEEEE", 4f, baseline, size, tint);
        } else {
            font.draw(current, "REEEEE", 4f, baseline, size, tint);
        }
        if (mode == 3) {
            current.endScissor();
            current.endScissor();
            current.popTranslation();
            current.setOpacityMultiplier(1f);
            current.draw(white, 12f, 9f, 7f, 18f, new Color(.7f, .1f, .2f, 1f));
        }
        current.flush();
        check(glGetInteger(GL_CURRENT_PROGRAM) == spriteProgram, "Sprite shader was not restored");
        check(glGetInteger(GL_VERTEX_ARRAY_BINDING) == spriteVao, "Sprite VAO was not restored");
        check(glIsEnabled(GL_FRAMEBUFFER_SRGB) == srgb, "Color conversion state leaked");
        current.end();
        if (integrated && mode != 3)
            check(current.getTotalDrawCalls() - before == (mode == 4 ? 3 : 1), "Unexpected glyph batching");
        ByteBuffer pixels = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
        glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return pixels;
    }

    /**
     * Checks grouping, empty input, default tint, invalid sizes and disposal failure
     * recovery without creating a new renderer for each label.
     */
    private void verifyLifecycle() {
        projection.setOrtho2D(0f, WIDTH, 0f, HEIGHT);
        Window.setProjectionMatrix(projection.get(new float[16]));
        long before = batch.getTotalDrawCalls();
        batch.begin();
        batch.setColor(Color.WHITE);
        font.draw(batch, "R", 4, 8, 15, null);
        font.createRun("E", 15).draw(batch, 14, 8, null);
        font.draw(batch, "", 24, 8, 15, null);
        font.draw(batch, "hidden", 24, 8, 0, null);
        batch.end();
        check(batch.getTotalDrawCalls() - before == 1, "Consecutive labels did not share a draw");
        boolean rejected = false;
        try {
            font.draw(batch, "R", 4, 8, 15, null);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        check(rejected, "Drawing outside begin/end must fail");
        batch.begin();
        rejected = false;
        try {
            font.draw(batch, "R", 4, 8, Float.NaN, null);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "Nonfinite size must fail");
        SlugFont disposable = SlugFont.load(SystemFonts.find(false).toString());
        font.draw(batch, "R", 4, 8, 15, null);
        disposable.draw(batch, "E", 14, 8, 15, null);
        disposable.dispose();
        rejected = false;
        try {
            batch.flush();
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        check(rejected, "Queued disposed font must fail");
        font.draw(batch, "Recovered", 4, 8, 15, null);
        batch.end();
    }

    /**
     * Releases only the resources owned by this fixture.
     */
    private void dispose() {
        batch.dispose();
        smallBatch.dispose();
        white.dispose();
    }

    /**
     * Requires exact pixel agreement and nonempty rendered text.
     *
     * @param expected reference submission pixels
     * @param actual alternate submission pixels
     * @param scenario failure context
     */
    private static void equal(ByteBuffer expected, ByteBuffer actual, String scenario) {
        /*
         * Exact readback catches changed coverage as well as state and ordering
         * errors; the nonzero check prevents two blank frames from passing.
         */
        int lit = 0;
        for (int i = 0; i < expected.capacity(); i++) {
            int difference = Math.abs(Byte.toUnsignedInt(expected.get(i)) - Byte.toUnsignedInt(actual.get(i)));
            if (difference != 0)
                throw new AssertionError(scenario + " differs at byte " + i + ": " + Byte.toUnsignedInt(expected.get(i)) + " versus " + Byte.toUnsignedInt(actual.get(i)));
            if (i % 4 != 3 && actual.get(i) != 0) lit++;
        }
        check(lit > 20, scenario + " produced no visible text");
    }

    /**
     * Exports actual GPU pixels for visual review, flipping only readback row order.
     *
     * @param pixels bottom-up RGBA pixels
     * @param path destination PNG
     * @throws Exception if the image cannot be written
     */
    private static void save(ByteBuffer pixels, Path path) throws Exception {
        /*
         * Convert framebuffer RGBA to the image's ARGB representation without
         * resampling so the saved artifact preserves the actual glyph edges.
         */
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int offset = (y * WIDTH + x) * 4;
                int rgba = (pixels.get(offset + 3) & 255) << 24 | (pixels.get(offset) & 255) << 16
                        | (pixels.get(offset + 1) & 255) << 8 | pixels.get(offset + 2) & 255;
                image.setRGB(x, HEIGHT - y - 1, rgba);
            }
        }
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "png", path.toFile());
    }

    /**
     * Throws an assertion failure with a useful native-test diagnostic.
     *
     * @param condition required invariant
     * @param message failure explanation
     */
    private static void check(boolean condition, String message) {
        /*
         * Explicit checks remain active when the JVM is launched without -ea.
         */
        if (!condition) throw new AssertionError(message);
    }
}
