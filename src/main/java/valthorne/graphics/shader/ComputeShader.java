package valthorne.graphics.shader;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glGetIntegeri_v;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL42.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Minimal wrapper for an OpenGL compute shader program (GL 4.3+).
 *
 * <p>Features:
 * - Compile/link a single GL_COMPUTE_SHADER stage
 * - Uniform setters (int/float/vec2/vec3/vec4/mat4 subset)
 * - Image and SSBO binding helpers
 * - Dispatch and memory barrier helpers
 *
 * Notes:
 * - Call {@link #dispose()} to delete GL resources when finished.
 * - Check availability via {@link #isComputeSupported()} before constructing.
 */
public class ComputeShader {

    private int programID;
    private int computeID;

    private final Map<String, Integer> uniformCache = new HashMap<>();

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

    public ComputeShader(String computeSource) {
        if (!isComputeSupported()) {
            throw new IllegalStateException("Compute shaders not supported by current GL context");
        }
        if (computeSource == null) throw new NullPointerException("computeSource");
        buildProgram(computeSource);
    }

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

    public void bind() { glUseProgram(programID); }
    public void unbind() { glUseProgram(0); }

    public void dispatch(int groupsX, int groupsY, int groupsZ) {
        glDispatchCompute(groupsX, groupsY, groupsZ);
    }

    public static void memoryBarrierAll() {
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);
    }

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

    private int uniformLocation(String name) {
        Integer cached = uniformCache.get(name);
        if (cached != null) return cached;
        int loc = glGetUniformLocation(programID, name);
        uniformCache.put(name, loc);
        return loc;
    }

    public void setUniform1i(String name, int v0) { glUniform1i(uniformLocation(name), v0); }
    public void setUniform1f(String name, float v0) { glUniform1f(uniformLocation(name), v0); }
    public void setUniform2f(String name, float x, float y) { glUniform2f(uniformLocation(name), x, y); }
    public void setUniform3f(String name, float x, float y, float z) { glUniform3f(uniformLocation(name), x, y, z); }
    public void setUniform4f(String name, float x, float y, float z, float w) { glUniform4f(uniformLocation(name), x, y, z, w); }
    public void setUniform2i(String name, int x, int y) { glUniform2i(uniformLocation(name), x, y); }

    // Image/SSBO binding helpers
    public static void bindImage2D(int texId, int unit, int access, int internalFormat) {
        glBindImageTexture(unit, texId, 0, false, 0, access, internalFormat);
    }

    public static int createSSBO(long size, int usage) {
        int id = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, id);
        glBufferData(GL_SHADER_STORAGE_BUFFER, size, usage);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        return id;
    }

    public static void bindSSBO(int ssboId, int binding) {
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, binding, ssboId);
    }

    public static void updateSSBO(int ssboId, long offset, java.nio.ByteBuffer data) {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssboId);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, offset, data);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    }
}
