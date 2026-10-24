package valthorne.graphics.bgfx;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.lwjgl.bgfx.BGFXMemory;
import org.lwjgl.bgfx.BGFXUniformInfo;
import org.lwjgl.system.MemoryStack;
import valthorne.graphics.GraphicsResource;

import static org.lwjgl.bgfx.BGFX.*;

/**
 * Owns a packaged bgfx program and reflected uniform state. The public Shader
 * facade uses this implementation when sources identify compiled stages with a
 * {@code bgfx:} prefix. Binaries are loaded once; shaderc is never run at runtime.
 *
 * <p>Uniform values persist in primitive arrays and are replayed at every draw,
 * preserving immediate uniform-update semantics despite bgfx's deferred
 * command model. Lookup uses small contiguous metadata arrays, not per-frame maps
 * or iterators. Optimized-out uniforms are ignored. Stage and uniform ownership
 * remains valid until close, including programs using shared shader code.</p>
 */
public final class BgfxShader implements AutoCloseable, GraphicsResource {
    private final String vertexName; // Logical graphics or compute stage identifier.
    private final String fragmentName; // Fragment stage identifier, null for compute programs.
    private boolean live; // Whether retained uniform handles belong to the current device.

    /**
     * Container version emitted by the shader compiler matching LWJGL 3.4.3.
     */
    private static final int CONTAINER_VERSION = 12;
    /**
     * Header bytes preceding the uniform count: magic, hashes and raw binding masks.
     */
    private static final int UNIFORM_TABLE_OFFSET = 20;
    /**
     * Serialized uniform flag identifying texture sampler bindings.
     */
    private static final int SAMPLER_FLAG = 0x20;
    /**
     * Vulkan container bindings reserve two slots for the stage uniform buffers.
     */
    private static final int SPIRV_BINDING_SHIFT = 2;
    private short program = BGFX_INVALID_HANDLE; // Owned linked native program.
    private short vertex = BGFX_INVALID_HANDLE; // Owned stage reference retaining reflected uniforms.
    private short fragment = BGFX_INVALID_HANDLE; // Owned fragment stage reference.
    private BgfxUniform[] uniforms = new BgfxUniform[16]; // Compact reflected metadata storage.
    private int uniformCount; // Number of populated metadata entries.

    /**
     * Loads and links named stages from the selected renderer's binary directory.
     *
     * @param vertexName vertex name without bgfx prefix or binary suffix
     * @param fragmentName fragment name without bgfx prefix or binary suffix
     */
    public BgfxShader(String vertexName, String fragmentName) {
        BgfxGraphics.device();
        this.vertexName = vertexName;
        this.fragmentName = fragmentName;
        live = true;
        try {
            vertex = load(vertexName);
            fragment = load(fragmentName);
            program = bgfx_create_program(vertex, fragment, false);
            if (program == BGFX_INVALID_HANDLE) throw new IllegalStateException("Could not link bgfx shader " + vertexName + " / " + fragmentName);
            reflect(vertex);
            reflect(fragment);
            BgfxResources.register(this);
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    /**
     * Loads one compiled compute stage and retains its reflected uniform state.
     *
     * @param computeName packaged compute stage without prefix or binary suffix
     */
    public BgfxShader(String computeName) {
        BgfxGraphics.device();
        this.vertexName = computeName;
        this.fragmentName = null;
        live = true;
        try {
            vertex = load(computeName);
            program = bgfx_create_compute_program(vertex, false);
            if (program == BGFX_INVALID_HANDLE) throw new IllegalStateException("Could not link compute shader " + computeName);
            reflect(vertex);
            BgfxResources.register(this);
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    /**
     * Loads a bgfx shader container into native-owned upload memory.
     *
     * @param name stage name without final binary suffix
     * @return owned stage handle
     */
    private short load(String name) {
        /*
         * Classpath containers include backend-specific bytecode and reflection.
         * Shader creation consumes a native copy independent of Java I/O storage.
         */
        String path = "/valthorne/bgfx/" + BgfxShaderResources.directory() + "/" + name + ".bin";
        try (InputStream stream = BgfxShader.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalArgumentException("Missing compiled shader: " + path);
            byte[] bytes = stream.readAllBytes();
            BGFXMemory memory = bgfx_alloc(bytes.length);
            if (memory == null) throw new IllegalStateException("Could not allocate shader upload memory");
            memory.data()
                    .put(bytes);
            short handle = bgfx_create_shader(memory);
            if (handle == BGFX_INVALID_HANDLE) throw new IllegalStateException("Invalid compiled shader: " + path);
            try {
                readSamplerBindings(bytes);
                return handle;
            } catch (RuntimeException | Error failure) {
                bgfx_destroy_shader(handle);
                throw failure;
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not load " + path, failure);
        }
    }

    /**
     * Retains sampler register locations from the versioned compiled container.
     * Public sampler values select texture units; compiled registers stay fixed,
     * so assigning a sampler to another public unit does not change its GPU slot.
     *
     * @param bytes complete shaderc container matching the bundled runtime
     */
    private void readSamplerBindings(byte[] bytes) {
        if (bytes.length < UNIFORM_TABLE_OFFSET + 2 || bytes[1] != 'S' || bytes[2] != 'H' || bytes[3] != CONTAINER_VERSION)
            throw new IllegalArgumentException("Unsupported bgfx shader container version");
        ByteBuffer data = ByteBuffer.wrap(bytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .position(UNIFORM_TABLE_OFFSET);
        int count = Short.toUnsignedInt(data.getShort());
        for (int i = 0; i < count; i++) {
            int length = Byte.toUnsignedInt(data.get());
            if (data.remaining() < length + 10) throw new IllegalArgumentException("Truncated shader uniform table");
            int nameOffset = data.position();
            data.position(nameOffset + length);
            int type = Byte.toUnsignedInt(data.get());
            data.get();
            int register = Short.toUnsignedInt(data.getShort());
            data.position(data.position() + 6);
            if ((type & SAMPLER_FLAG) != 0) {
                if (bgfx_get_caps().rendererType() == BGFX_RENDERER_TYPE_VULKAN) register -= SPIRV_BINDING_SHIFT;
                if (register < 0 || register >= 16) throw new IllegalArgumentException("Compiled sampler register exceeds the engine's texture-slot limit");
                String name = new String(bytes, nameOffset, length, StandardCharsets.UTF_8);
                BgfxUniform uniform = declareSampler(name);
                uniform.stages |= 1 << register;
            }
        }
    }

    /**
     * Owns one explicit sampler reference, shared across its compiled stages.
     *
     * @param name native sampler name
     * @return retained sampler metadata
     */
    private BgfxUniform declareSampler(String name) {
        BgfxUniform uniform = find(name);
        if (uniform != null) {
            if (uniform.type != BGFX_UNIFORM_TYPE_SAMPLER) throw new IllegalArgumentException("Uniform is not a sampler: " + name);
            return uniform;
        }
        short handle = bgfx_create_uniform(name, BGFX_UNIFORM_TYPE_SAMPLER, 1);
        if (handle == BGFX_INVALID_HANDLE) throw new IllegalStateException("Could not allocate sampler " + name);
        uniform = new BgfxUniform(name, handle, BGFX_UNIFORM_TYPE_SAMPLER, 1, 0);
        uniform.owned = true;
        if (uniformCount == uniforms.length) uniforms = Arrays.copyOf(uniforms, uniforms.length * 2);
        uniforms[uniformCount++] = uniform;
        return uniform;
    }

    /**
     * Appends unique reflected uniforms from one retained shader stage.
     *
     * @param shader valid native shader
     */
    private void reflect(short shader) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int count = Short.toUnsignedInt(nbgfx_get_shader_uniforms(shader, 0, (short) 0));
            ShortBuffer handles = stack.mallocShort(count);
            bgfx_get_shader_uniforms(shader, handles);
            BGFXUniformInfo info = BGFXUniformInfo.calloc(stack);
            for (int i = 0; i < count; i++) {
                short handle = handles.get(i);
                bgfx_get_uniform_info(handle, info);
                String name = info.nameString();
                if (find(name) != null) continue;
                if (uniformCount == uniforms.length) uniforms = Arrays.copyOf(uniforms, uniforms.length * 2);
                int type = info.type();
                int components = type == BGFX_UNIFORM_TYPE_MAT4 ? 16 : type == BGFX_UNIFORM_TYPE_MAT3 ? 9 : type == BGFX_UNIFORM_TYPE_VEC4 ? 4 : 0;
                BgfxUniform uniform = new BgfxUniform(name, handle, type, Short.toUnsignedInt(info.num()), components);
                if (name.equals("u_uiTransform") || name.equals("u_textTransform")) uniform.values[0] = 1;
                uniforms[uniformCount++] = uniform;
            }
        }
    }

    /**
     * Finds a reflected uniform without allocating or probing native state.
     *
     * @param name exact shader name
     * @return metadata, or null when optimized out
     */
    private BgfxUniform find(String name) {
        for (int i = 0; i < uniformCount; i++) {
            BgfxUniform uniform = uniforms[i];
            if (uniform.name.equals(name)) return uniform;
            int length = uniform.name.length();
            if (name.length() >= length + 2 && name.charAt(length) == '[' && name.charAt(name.length() - 1) == ']' && name.startsWith(uniform.name))
                return uniform;
        }
        return null;
    }

    /**
     * Resolves an optional decimal array index without allocating a substring.
     *
     * @param uniform reflected base uniform
     * @param name exact base name or indexed element name
     * @return checked array index
     */
    private int element(BgfxUniform uniform, String name) {
        if (name.length() == uniform.name.length()) return 0;
        if (name.length() == uniform.name.length() + 2) throw new IllegalArgumentException("Invalid uniform element: " + name);
        int index = 0;
        for (int i = uniform.name.length() + 1; i < name.length() - 1; i++) {
            char digit = name.charAt(i);
            if (digit < '0' || digit > '9' || index >= uniform.count)
                throw new IllegalArgumentException("Invalid uniform element: " + name);
            index = index * 10 + digit - '0';
        }
        if (index >= uniform.count) throw new IllegalArgumentException("Uniform element exceeds declared capacity: " + name);
        return index;
    }

    /**
     * Updates one vector uniform's retained scalar components.
     *
     * @param name uniform name
     * @param x first component
     * @param y second component
     * @param z third component
     * @param w fourth component
     */
    public void vector(String name, float x, float y, float z, float w) {
        checkOpen();
        BgfxUniform uniform = find(name);
        if (uniform == null) return;
        if (uniform.type != BGFX_UNIFORM_TYPE_VEC4) throw new IllegalArgumentException("Uniform is not a vector: " + name);
        int offset = element(uniform, name) * 4;
        uniform.values[offset] = x;
        uniform.values[offset + 1] = y;
        uniform.values[offset + 2] = z;
        uniform.values[offset + 3] = w;
    }

    /**
     * Updates retained matrix or vector elements, starting at the optional
     * array index in the uniform name. Values beyond the copied range remain
     * unchanged, and an invalid index or range is rejected before mutation.
     *
     * @param name uniform name
     * @param values complete float values in column-major matrix order
     * @throws IllegalArgumentException if the uniform is a sampler or the indexed range exceeds its capacity
     */
    public void values(String name, float[] values) {
        checkOpen();
        BgfxUniform uniform = find(name);
        if (uniform == null) return;
        if (uniform.type == BGFX_UNIFORM_TYPE_SAMPLER) throw new IllegalArgumentException("Uniform is not numeric: " + name);
        int offset = element(uniform, name) * (uniform.values.length / uniform.count);
        if (values.length > uniform.values.length - offset) throw new IllegalArgumentException("Uniform array exceeds declared capacity: " + name);
        System.arraycopy(values, 0, uniform.values, offset, values.length);
    }

    /**
     * Stores an integer scalar or a sampler's texture-slot assignment.
     *
     * @param name uniform name
     * @param value scalar or texture unit
     */
    public void integer(String name, int value) {
        checkOpen();
        BgfxUniform uniform = find(name);
        if (uniform == null) return;
        if (uniform.type == BGFX_UNIFORM_TYPE_SAMPLER) {
            if (value < 0 || value >= 16) throw new IllegalArgumentException("Sampler texture unit outside [0, 15]");
            uniform.unit = value;
        }
        else vector(name, value, 0, 0, 0);
    }

    /**
     * Declares a sampled texture binding, including load-only HLSL resources that
     * shaderc does not list as sampler-state uniforms in reflection.
     *
     * @param name compiled sampler name
     * @param unit compiled texture register index
     */
    public void sampler(String name, int unit) {
        checkOpen();
        if (unit < 0 || unit >= 16) throw new IllegalArgumentException("Sampler texture unit outside [0, 15]");
        BgfxUniform uniform = declareSampler(name);
        if (uniform.stages == 0) uniform.stages = 1 << unit;
        uniform.unit = unit;
    }

    /**
     * Applies persistent numeric uniforms and available textures before submission.
     *
     * @param textures texture handles indexed by public texture unit
     * @param count populated texture unit count
     */
    public void apply(int[] textures, int count) {
        checkOpen();
        for (int i = 0; i < uniformCount; i++) {
            BgfxUniform uniform = uniforms[i];
            if (uniform.handle == BGFX_INVALID_HANDLE) continue;
            if (uniform.type == BGFX_UNIFORM_TYPE_SAMPLER) {
                if (uniform.unit >= 0 && uniform.unit < count) {
                    int texture = textures[uniform.unit];
                    if (texture == Short.toUnsignedInt(BGFX_INVALID_HANDLE))
                        throw new IllegalStateException("Sampler has no bound texture: " + uniform.name);
                    int nativeTexture = BgfxGraphics.textureHandle(texture);
                    int flags = BgfxGraphics.textureFlags(nativeTexture);
                    for (int stages = uniform.stages == 0 ? 1 << uniform.unit : uniform.stages; stages != 0; stages &= stages - 1)
                        bgfx_set_texture(Integer.numberOfTrailingZeros(stages), uniform.handle, (short) nativeTexture, flags);
                }
            } else {
                if (uniform.viewport) BgfxGraphics.viewportAdjustment(uniform.values);
                bgfx_set_uniform(uniform.handle, uniform.values, uniform.count);
            }
        }
    }

    /**
     * Returns the owned program for submission without transferring ownership.
     *
     * @return valid native program
     */
    public short handle() {
        checkOpen();
        return program;
    }

    /**
     * Rejects native access after disposal or outside the owning render thread.
     */
    private void checkOpen() {
        BgfxGraphics.device();
        if (program == BGFX_INVALID_HANDLE) throw new IllegalStateException("Shader is disposed");
    }

    @Override
    public void close() {
        BgfxGraphics.device();
        BgfxResources.unregister(this);
        releaseGraphicsResources();
        uniformCount = 0;
        Arrays.fill(uniforms, null);
    }

    @Override
    public void releaseGraphicsResources() {
        if (!live) return;
        for (int i = 0; i < uniformCount; i++)
            if (uniforms[i].owned) bgfx_destroy_uniform(uniforms[i].handle);
        if (program != BGFX_INVALID_HANDLE) bgfx_destroy_program(program);
        if (vertex != BGFX_INVALID_HANDLE) bgfx_destroy_shader(vertex);
        if (fragment != BGFX_INVALID_HANDLE) bgfx_destroy_shader(fragment);
        program = vertex = fragment = BGFX_INVALID_HANDLE;
        live = false;
    }

    @Override
    public void restoreGraphicsResources() {
        BgfxUniform[] previous = Arrays.copyOf(uniforms, uniformCount);
        uniformCount = 0;
        Arrays.fill(uniforms, null);
        live = true;
        try {
            vertex = load(vertexName);
            if (fragmentName == null) {
                program = bgfx_create_compute_program(vertex, false);
            } else {
                fragment = load(fragmentName);
                program = bgfx_create_program(vertex, fragment, false);
            }
            if (program == BGFX_INVALID_HANDLE) throw new IllegalStateException("Could not restore shader " + vertexName);
            reflect(vertex);
            if (fragment != BGFX_INVALID_HANDLE) reflect(fragment);
            for (BgfxUniform saved : previous) {
                BgfxUniform next = find(saved.name);
                if (next == null && saved.type == BGFX_UNIFORM_TYPE_SAMPLER) next = declareSampler(saved.name);
                if (next == null) {
                    next = new BgfxUniform(saved.name, BGFX_INVALID_HANDLE, saved.type, saved.count, saved.values.length / saved.count);
                    if (uniformCount == uniforms.length) uniforms = Arrays.copyOf(uniforms, Math.max(16, uniforms.length * 2));
                    uniforms[uniformCount++] = next;
                }
                if (next.type != saved.type) throw new IllegalStateException("Replacement shader changed uniform type: " + saved.name);
                next.unit = saved.unit;
                if (next.stages == 0) next.stages = saved.stages;
                System.arraycopy(saved.values, 0, next.values, 0, Math.min(saved.values.length, next.values.length));
            }
        } catch (RuntimeException | Error failure) {
            releaseGraphicsResources();
            uniforms = Arrays.copyOf(previous, Math.max(16, previous.length));
            uniformCount = previous.length;
            throw failure;
        }
    }

}
