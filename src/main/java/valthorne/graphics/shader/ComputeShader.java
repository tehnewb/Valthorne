package valthorne.graphics.shader;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL31.glBindBufferBase;
import static org.lwjgl.opengl.GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;
import static org.lwjgl.opengl.GL42.GL_TEXTURE_FETCH_BARRIER_BIT;
import static org.lwjgl.opengl.GL42.glBindImageTexture;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43.GL_COMPUTE_SHADER;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43.glDispatchCompute;

/**
 * Minimal wrapper for an OpenGL compute shader program (GL 4.3+).
 *
 * <p>Features:
 * - Compile/link a single GL_COMPUTE_SHADER stage
 * - Scalar and vector uniform setters
 * - Image and SSBO binding helpers
 * - Dispatch and memory barrier helpers
 * <p>
 * Notes:
 * - Call {@link #dispose()} to delete GL resources when finished.
 * - Check availability via {@link #isComputeSupported()} before constructing.
 *
 * <pre>{@code
 * ComputeShader shader = new ComputeShader(source);
 * shader.bind();
 * shader.setUniform1i("u_count", count);
 * shader.dispatch(groups, 1, 1);
 * ComputeShader.memoryBarrierAll();
 * shader.unbind();
 * shader.dispose();
 * }</pre>
 *
 * <p>All OpenGL operations require the appropriate current context. Uniform setters
 * use glUniform and require this program to be bound; dispatch also uses the currently
 * bound program. Binding helpers change global state and do not restore previous
 * bindings. Created storage buffers belong to the caller and are not deleted when
 * this shader is disposed.</p>
 *
 * @author Albert Beaupre
 */
public class ComputeShader {

    private final Map<String, Integer> uniformCache = new HashMap<>(); // Cached uniform locations, including absent locations (-1).
    private int programID; // Owned linked program name, or zero after disposal.
    private int computeID; // Owned compiled compute-stage name, or zero after disposal.

    /**
     * Checks compute support and compiles a single compute stage into a linked program.
     * Compilation and linking failures clean up resources allocated by those failed steps.
     * The new program is not automatically bound.
     *
     * @param computeSource GLSL compute-stage source
     * @throws NullPointerException if source is null after the support check
     * @throws IllegalStateException if support is absent or compilation/linking fails
     */
    public ComputeShader(String computeSource) {
        if (!isComputeSupported()) {
            String version = glGetString(GL_VERSION);
            throw new IllegalStateException("Compute shaders require an OpenGL 4.3+ context or GL_ARB_compute_shader support. Current GL context: " + (version != null ? version : "unknown"));
        }
        if (computeSource == null) throw new NullPointerException("computeSource");
        buildProgram(computeSource);
    }

    /**
     * Inspects the current context's version string, then falls back to checking both
     * compute-shader and shader-storage extension names. This string-based probe is
     * not an exhaustive capabilities check and requires an initialized current context.
     *
     * @return whether the version or extension probe reports support
     */
    public static boolean isComputeSupported() {
        // Basic check: GL version >= 4.3 or ARB_compute_shader available
        String ver = glGetString(GL_VERSION);
        if (ver != null) {
            try {
                String[] parts = ver.split("\\.");
                int major = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
                int minor = parts.length > 1 ? Integer.parseInt(parts[1].replaceAll("[^0-9]", "")) : 0;
                if (major > 4 || (major == 4 && minor >= 3)) return true;
            } catch (Exception ignored) {}
        }
        // Fallback extension probe
        String ext = glGetString(GL_EXTENSIONS);
        return ext != null && (ext.contains("GL_ARB_compute_shader") && ext.contains("GL_ARB_shader_storage_buffer_object"));
    }

    /**
     * Issues image-access, shader-storage, and texture-fetch barrier bits for dependent
     * GPU operations. Despite the name, this is not GL_ALL_BARRIER_BITS and does not
     * wait for completion on the CPU.
     */
    public static void memoryBarrierAll() {
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);
    }

    /**
     * Issues only the shader-image-access barrier bit. Choose additional barrier types
     * when later operations consume the output through other access paths.
     */
    public static void memoryBarrierImage() {
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    }

    // Image/SSBO binding helpers
    /**
     * Binds mip level zero of a two-dimensional texture as a nonlayered image. Changes
     * the indexed image binding and leaves validation of format/access compatibility to OpenGL.
     *
     * @param texId texture name, or zero to unbind
     * @param unit image binding index
     * @param access OpenGL read/write access constant
     * @param internalFormat image format compatible with the texture
     */
    public static void bindImage2D(int texId, int unit, int access, int internalFormat) {
        glBindImageTexture(unit, texId, 0, false, 0, access, internalFormat);
    }

    /**
     * Allocates uninitialized shader-storage buffer bytes and unbinds the generic target
     * to zero afterward. The caller owns the returned buffer and must delete it.
     *
     * @param size storage size in bytes
     * @param usage OpenGL buffer usage hint
     * @return newly allocated buffer name
     */
    public static int createSSBO(long size, int usage) {
        int id = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, id);
        glBufferData(GL_SHADER_STORAGE_BUFFER, size, usage);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        return id;
    }

    /**
     * Binds a whole storage buffer at an indexed shader-storage binding point. Does not
     * allocate storage or transfer ownership.
     *
     * @param ssboId buffer name, or zero to clear the binding
     * @param binding shader-storage binding index
     */
    public static void bindSSBO(int ssboId, int binding) {
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, binding, ssboId);
    }

    /**
     * Uploads the buffer's remaining bytes into existing storage at a byte offset, then
     * unbinds the generic storage target to zero. This does not resize the destination
     * or issue a memory barrier; the caller supplies suitable buffer bounds.
     *
     * @param ssboId existing storage buffer name
     * @param offset destination byte offset
     * @param data source bytes from position through limit
     */
    public static void updateSSBO(int ssboId, long offset, java.nio.ByteBuffer data) {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssboId);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, offset, data);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    }

    /**
     * Compiles and links one compute stage, retaining both names on success and clearing
     * uniform locations. Compiler or linker failures include the driver's diagnostic log
     * and delete the resources created for that failed stage.
     *
     * @param src compute-stage GLSL source
     * @throws IllegalStateException if compilation or linking fails
     */
    private void buildProgram(String src) {
        int cs = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(cs, src);
        glCompileShader(cs);
        if (glGetShaderi(cs, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(cs);
            glDeleteShader(cs);
            throw new IllegalStateException("Compute shader compile failed:\n" + log);
        }
        int prog = glCreateProgram();
        glAttachShader(prog, cs);
        glLinkProgram(prog);
        if (glGetProgrami(prog, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(prog);
            glDetachShader(prog, cs);
            glDeleteProgram(prog);
            glDeleteShader(cs);
            throw new IllegalStateException("Compute shader link failed:\n" + log);
        }
        this.programID = prog;
        this.computeID = cs;
        uniformCache.clear();
    }

    /**
     * Selects this program as the current OpenGL program. Does not save the previously
     * bound program; after disposal the stored zero name unbinds instead.
     */
    public void bind() {glUseProgram(programID);}

    /**
     * Binds program zero, regardless of which program is currently active. This does
     * not restore a program that was active before bind.
     */
    public void unbind() {glUseProgram(0);}

    /**
     * Dispatches workgroups using the currently bound compute program. Counts are groups,
     * not individual shader invocations. Does not bind this instance or insert a barrier.
     *
     * @param groupsX workgroup count along X
     * @param groupsY workgroup count along Y
     * @param groupsZ workgroup count along Z
     */
    public void dispatch(int groupsX, int groupsY, int groupsZ) {
        glDispatchCompute(groupsX, groupsY, groupsZ);
    }

    /**
     * Deletes the owned program and compute stage, first unbinding if this program is
     * active. Clears uniform locations and zeros names, making repeated disposal a no-op.
     * Buffers and textures used by the shader remain caller-owned.
     */
    public void dispose() {
        if (programID != 0) {
            if (glGetInteger(GL_CURRENT_PROGRAM) == programID) {
                glUseProgram(0);
            }
            glDetachShader(programID, computeID);
            glDeleteProgram(programID);
            glDeleteShader(computeID);
            programID = 0;
            computeID = 0;
        }
        uniformCache.clear();
    }

    /**
     * Looks up and caches a uniform location in this program, including -1 for inactive
     * or missing uniforms. Does not bind the program; disposal clears the cache.
     *
     * @param name GLSL uniform name
     * @return cached or queried location
     */
    private int uniformLocation(String name) {
        Integer cached = uniformCache.get(name);
        if (cached != null) return cached;
        int loc = glGetUniformLocation(programID, name);
        uniformCache.put(name, loc);
        return loc;
    }

    /**
     * Writes a 1-component int uniform using a cached location from this program.
     * Bind this shader first: glUniform writes the active program. Inactive or missing
     * uniforms have location -1 and are ignored by OpenGL.
     *
     * @param name GLSL uniform name
     * @param v0 scalar value
     */
    public void setUniform1i(String name, int v0) {glUniform1i(uniformLocation(name), v0);}

    /**
     * Writes a 1-component float uniform using a cached location from this program.
     * Bind this shader first: glUniform writes the active program. Inactive or missing
     * uniforms have location -1 and are ignored by OpenGL.
     *
     * @param name GLSL uniform name
     * @param v0 scalar value
     */
    public void setUniform1f(String name, float v0) {glUniform1f(uniformLocation(name), v0);}

    /**
     * Writes a 2-component float uniform using a cached location from this program.
     * Bind this shader first: glUniform writes the active program. Inactive or missing
     * uniforms have location -1 and are ignored by OpenGL.
     *
     * @param name GLSL uniform name
     * @param x X component
     * @param y Y component
     */
    public void setUniform2f(String name, float x, float y) {glUniform2f(uniformLocation(name), x, y);}

    /**
     * Writes a 3-component float uniform using a cached location from this program.
     * Bind this shader first: glUniform writes the active program. Inactive or missing
     * uniforms have location -1 and are ignored by OpenGL.
     *
     * @param name GLSL uniform name
     * @param x X component
     * @param y Y component
     * @param z Z component
     */
    public void setUniform3f(String name, float x, float y, float z) {glUniform3f(uniformLocation(name), x, y, z);}

    /**
     * Writes a 4-component float uniform using a cached location from this program.
     * Bind this shader first: glUniform writes the active program. Inactive or missing
     * uniforms have location -1 and are ignored by OpenGL.
     *
     * @param name GLSL uniform name
     * @param x X component
     * @param y Y component
     * @param z Z component
     * @param w W component
     */
    public void setUniform4f(String name, float x, float y, float z, float w) {glUniform4f(uniformLocation(name), x, y, z, w);}

    /**
     * Writes a 2-component int uniform using a cached location from this program.
     * Bind this shader first: glUniform writes the active program. Inactive or missing
     * uniforms have location -1 and are ignored by OpenGL.
     *
     * @param name GLSL uniform name
     * @param x X component
     * @param y Y component
     */
    public void setUniform2i(String name, int x, int y) {glUniform2i(uniformLocation(name), x, y);}
}
