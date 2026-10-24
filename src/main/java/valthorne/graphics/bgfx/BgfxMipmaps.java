package valthorne.graphics.bgfx;

import java.nio.ByteBuffer;
import org.lwjgl.stb.STBIR_RESIZE;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.stb.STBImageResize.*;
import static org.lwjgl.system.MemoryUtil.memAddress;

/**
 * Generates RGBA8 mip chains with STB's native image resampler. Each level uses
 * the box filter, linear byte values, clamped edges, and four independent channels.
 * Color and alpha therefore retain the engine's unweighted channel convention.
 * Odd dimensions use STB's fractional filter coverage instead of integer bins.
 *
 * <p>The caller supplies validated base pixels and enough contiguous storage for
 * every mip level. Source and destination levels occupy disjoint ranges of that
 * allocation. STB owns and releases temporary filter workspace per level; Java
 * retains no scratch images, and the buffer's position and limit are preserved.
 * Calls run synchronously during texture configuration on the owning thread.</p>
 */
final class BgfxMipmaps {
    /**
     * Prevents constructing stateless mip-chain generators.
     */
    private BgfxMipmaps() {
    }

    /**
     * Appends all downsampled levels after the base image using native filtering.
     *
     * @param upload owned direct storage containing base pixels at absolute offset zero
     * @param width validated positive base width
     * @param height validated positive base height
     * @throws IllegalStateException if STB cannot allocate its filter workspace
     */
    static void generate(ByteBuffer upload, int width, int height) {
        /*
         * Reuse one stack descriptor for the complete chain. Absolute native
         * addresses avoid temporary buffer views and leave the cursor unchanged.
         */
        if (width == 1 && height == 1) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            STBIR_RESIZE settings = STBIR_RESIZE.malloc(stack);
            long base = memAddress(upload, 0);
            int source = 0;
            int destination = width * height * 4;
            while (width > 1 || height > 1) {
                int nextWidth = Math.max(1, width / 2);
                int nextHeight = Math.max(1, height / 2);
                nstbir_resize_init(settings.address(), base + source, width, height, width * 4, base + destination, nextWidth, nextHeight, nextWidth * 4, STBIR_4CHANNEL, STBIR_TYPE_UINT8);
                stbir_set_filters(settings, STBIR_FILTER_BOX, STBIR_FILTER_BOX);
                if (stbir_resize_extended(settings) == 0) throw new IllegalStateException("Could not generate texture mipmaps");
                source = destination;
                destination += nextWidth * nextHeight * 4;
                width = nextWidth;
                height = nextHeight;
            }
        }
    }
}
