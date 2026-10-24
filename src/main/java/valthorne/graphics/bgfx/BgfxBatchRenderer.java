package valthorne.graphics.bgfx;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;
import org.lwjgl.bgfx.BGFXVertexLayout;
import org.lwjgl.system.MemoryStack;
import valthorne.graphics.Color;
import valthorne.graphics.GraphicsResource;
import valthorne.graphics.shader.Shader;

import static org.lwjgl.bgfx.BGFX.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Uploads the public TextureBatch's existing sprite and glyph records as compact
 * bgfx instances. One static six-vertex quad is shared across all records. CPU
 * layout, clipping, culling and retained text remain in the existing batch code.
 *
 * <p>Sprite/glyph records use six vec4 attributes (96 bytes); particle and light
 * pipelines select smaller aligned layouts. Reusable GPU chunks preserve every queued upload at a
 * distinct offset until presentation. Chunks grow only at a new workload peak and
 * are reused in later frames, avoiding buffer-resize invalidation, per-frame Java
 * allocation and expanded per-glyph vertices. All resources are render-thread owned.</p>
 */
public final class BgfxBatchRenderer implements AutoCloseable, GraphicsResource {
    /**
     * Six vec4 attributes shared by sprite and glyph instances.
     */
    private static final int DEFAULT_INSTANCE_STRIDE = 96;
    /**
     * Legacy straight-alpha blending applied to both RGB and destination alpha.
     */
    private static final long SPRITE_STATE = BGFX_STATE_WRITE_RGB | BGFX_STATE_WRITE_A | BGFX_STATE_MSAA | BGFX_STATE_BLEND_FUNC(BGFX_STATE_BLEND_SRC_ALPHA, BGFX_STATE_BLEND_INV_SRC_ALPHA);
    /**
     * Slug fragment shaders produce premultiplied color coverage.
     */
    private static final long GLYPH_STATE = BGFX_STATE_WRITE_RGB | BGFX_STATE_WRITE_A | BGFX_STATE_MSAA | BGFX_STATE_BLEND_FUNC(BGFX_STATE_BLEND_ONE, BGFX_STATE_BLEND_INV_SRC_ALPHA);

    private final int capacity; // Number of instances held in one reusable upload chunk.
    private final int instanceStride; // Aligned bytes per instance, selected once at construction.
    private ByteBuffer staging; // Owned padded CPU upload buffer.
    private BGFXVertexLayout instanceLayout; // Owned aligned vec4 layout for dynamic instance buffers.
    private short quad = BGFX_INVALID_HANDLE; // Owned immutable quad geometry.
    private short[] chunks = new short[4]; // Owned dynamic GPU buffer handles.
    private int chunkCount; // Allocated chunk count retained across frames.
    private int chunkIndex; // Current frame's active chunk.
    private int offset; // First unused record in the active chunk.
    private long frame = -1; // Last upload generation observed from the coordinator.
    private boolean closed; // Whether native resources have been released.

    /**
     * Allocates geometry and one reusable CPU/GPU instance chunk.
     *
     * @param capacity positive maximum instances in one public batch flush
     */
    public BgfxBatchRenderer(int capacity) {
        this(capacity, DEFAULT_INSTANCE_STRIDE);
    }

    /**
     * Allocates compact instance storage for a shader's vec4 input records.
     *
     * @param capacity positive maximum records per submission
     * @param instanceStride record bytes, a multiple of 16 between 16 and 96
     */
    public BgfxBatchRenderer(int capacity, int instanceStride) {
        BgfxGraphics.device();
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        if (instanceStride < 16 || instanceStride > DEFAULT_INSTANCE_STRIDE || (instanceStride & 15) != 0)
            throw new IllegalArgumentException("Instance stride must be a multiple of 16 between 16 and 96");
        this.capacity = capacity;
        this.instanceStride = instanceStride;
        try {
            staging = memCalloc(Math.multiplyExact(capacity, instanceStride));
            instanceLayout = BGFXVertexLayout.calloc();
            restoreGraphicsResources();
            BgfxResources.register(this);
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    /**
     * Allocates one independently addressed GPU chunk at a new workload peak.
     */
    private void allocateChunk() {
        if (chunkCount == chunks.length) chunks = Arrays.copyOf(chunks, chunks.length * 2);
        short handle = bgfx_create_dynamic_vertex_buffer(capacity, instanceLayout, BGFX_BUFFER_NONE);
        if (handle == BGFX_INVALID_HANDLE) throw new IllegalStateException("Could not allocate bgfx instance chunk");
        chunks[chunkCount++] = handle;
    }

    /**
     * Copies packed records into an unused native instance range and submits once.
     *
     * @param records packed source records positioned at zero
     * @param count populated record count
     * @param sourceStride bytes per source record, no greater than the instance stride
     * @param shader compiled batch-compatible program
     * @param textures borrowed sampled texture handles indexed by unit
     * @param textureCount populated texture units
     * @param glyphs whether fragments produce premultiplied glyph coverage
     */
    public void submit(ByteBuffer records, int count, int sourceStride, Shader shader, int[] textures, int textureCount, boolean glyphs) {
        submit(records, count, sourceStride, shader, textures, textureCount, glyphs ? GLYPH_STATE : SPRITE_STATE);
    }

    /**
     * Submits aligned instances with an explicitly selected native draw state.
     *
     * @param records packed source records at the current buffer position
     * @param count populated records, no greater than this renderer's capacity
     * @param sourceStride source bytes per record
     * @param shader compatible compiled instance program
     * @param textures borrowed sampled texture indices
     * @param textureCount populated texture slots
     * @param state complete write, depth, blend and topology flags
     */
    public void submit(ByteBuffer records, int count, int sourceStride, Shader shader, int[] textures, int textureCount, long state) {
        BgfxGraphics.device();
        if (closed) throw new IllegalStateException("bgfx batch renderer is closed");
        if (count == 0) return;
        if (count < 0 || count > capacity || sourceStride < 1 || sourceStride > instanceStride || records.remaining() < (long) count * sourceStride)
            throw new IllegalArgumentException("Invalid packed instance range");
        if (frame != BgfxGraphics.frameId()) {
            frame = BgfxGraphics.frameId();
            chunkIndex = 0;
            offset = 0;
        }
        if (offset + count > capacity) {
            chunkIndex++;
            offset = 0;
            if (chunkIndex == chunkCount) allocateChunk();
        }
        long source = memAddress(records);
        long destination = memAddress(staging);
        if (source != destination) {
            for (int i = 0; i < count; i++)
                memCopy(source + (long) i * sourceStride, destination + (long) i * instanceStride, sourceStride);
        }
        int bytes = count * instanceStride;
        long upload = nbgfx_copy(destination, bytes);
        if (upload == 0) throw new IllegalStateException("Could not allocate bgfx instance upload");
        nbgfx_update_dynamic_vertex_buffer(chunks[chunkIndex], offset, upload);
        shader.bgfx().apply(textures, textureCount);
        bgfx_set_vertex_buffer(0, quad, 0, 6);
        bgfx_set_instance_data_from_dynamic_vertex_buffer(chunks[chunkIndex], offset, count);
        bgfx_set_state(state, 0);
        bgfx_submit(BgfxGraphics.view(), shader.bgfx().handle(), 0, BGFX_DISCARD_ALL);
        offset += count;
    }

    /**
     * Draws arbitrary four-corner textured quads through the compact instance path.
     * Positions and UVs are read by absolute index without changing caller cursors.
     *
     * @param positions four XY corners per quad
     * @param uvs corresponding UV coordinates
     * @param quadCount number of populated quads
     * @param tint copied vertex tint, or null for white
     * @param shader compiled immediate-quad program
     * @param textures sampled texture handles
     */
    public void quads(FloatBuffer positions, FloatBuffer uvs, int quadCount, Color tint, Shader shader, int[] textures) {
        if (instanceStride != DEFAULT_INSTANCE_STRIDE) throw new IllegalStateException("Immediate quads require 96-byte instances");
        if (quadCount < 0 || (long) quadCount * 8 > positions.limit() || (long) quadCount * 8 > uvs.limit())
            throw new IndexOutOfBoundsException("Insufficient quad positions or UVs");
        float r = tint == null ? 1 : tint.r();
        float g = tint == null ? 1 : tint.g();
        float b = tint == null ? 1 : tint.b();
        float a = tint == null ? 1 : tint.a();
        for (int first = 0; first < quadCount; first += capacity) {
            int count = Math.min(capacity, quadCount - first);
            staging.clear();
            for (int i = 0; i < count; i++) {
                int source = (first + i) * 8;
                int destination = i * instanceStride;
                for (int j = 0; j < 8; j++) staging.putFloat(destination + j * 4, positions.get(source + j));
                for (int j = 0; j < 8; j++) staging.putFloat(destination + 32 + j * 4, uvs.get(source + j));
                staging.putFloat(destination + 64, r);
                staging.putFloat(destination + 68, g);
                staging.putFloat(destination + 72, b);
                staging.putFloat(destination + 76, a);
            }
            submit(staging, count, instanceStride, shader, textures, 16, false);
        }
    }

    @Override
    public void close() {
        BgfxGraphics.device();
        if (closed) return;
        BgfxResources.unregister(this);
        releaseGraphicsResources();
        if (instanceLayout != null) instanceLayout.free();
        if (staging != null) memFree(staging);
        closed = true;
    }

    @Override
    public void releaseGraphicsResources() {
        for (int i = 0; i < chunkCount; i++) bgfx_destroy_dynamic_vertex_buffer(chunks[i]);
        chunkCount = 0;
        if (quad != BGFX_INVALID_HANDLE) bgfx_destroy_vertex_buffer(quad);
        quad = BGFX_INVALID_HANDLE;
    }

    @Override
    public void restoreGraphicsResources() {
        if ((bgfx_get_caps().supported() & BGFX_CAPS_INSTANCING) == 0)
            throw new UnsupportedOperationException("The selected device does not support GPU instancing");
        frame = -1;
        offset = chunkIndex = 0;
        try {
            bgfx_vertex_layout_begin(instanceLayout, BgfxGraphics.device().backend().rendererType());
            for (int i = 0; i < instanceStride / 16; i++)
                bgfx_vertex_layout_add(instanceLayout, BGFX_ATTRIB_TEXCOORD0 + i, (byte) 4, BGFX_ATTRIB_TYPE_FLOAT, false, false);
            bgfx_vertex_layout_end(instanceLayout);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                BGFXVertexLayout layout = BGFXVertexLayout.calloc(stack);
                bgfx_vertex_layout_begin(layout, BgfxGraphics.device().backend().rendererType());
                bgfx_vertex_layout_add(layout, BGFX_ATTRIB_POSITION, (byte) 2, BGFX_ATTRIB_TYPE_FLOAT, false, false);
                bgfx_vertex_layout_add(layout, BGFX_ATTRIB_TEXCOORD0, (byte) 2, BGFX_ATTRIB_TYPE_FLOAT, false, false);
                bgfx_vertex_layout_end(layout);
                FloatBuffer data = stack.floats(0, 0, 0, 0, 1, 0, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 1, 0, 1, 0, 0, 0, 0);
                quad = bgfx_create_vertex_buffer(bgfx_copy(data), layout, BGFX_BUFFER_NONE);
                if (quad == BGFX_INVALID_HANDLE) throw new IllegalStateException("Could not create bgfx quad geometry");
            }
            allocateChunk();
        } catch (RuntimeException | Error failure) {
            releaseGraphicsResources();
            throw failure;
        }
    }

}
