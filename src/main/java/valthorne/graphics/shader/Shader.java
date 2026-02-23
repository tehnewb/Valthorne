package valthorne.graphics.shader;

import org.lwjgl.opengl.GL20;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL31.GL_INVALID_INDEX;
import static org.lwjgl.opengl.GL31.glGetUniformBlockIndex;
import static org.lwjgl.opengl.GL31.glUniformBlockBinding;

/**
 * OpenGL shader program wrapper that compiles, links, validates, and manages a GLSL
 * vertex/fragment shader pair, with hot-reload support and cached lookups.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Build
 * Shader shader = new Shader(VERT_SRC, FRAG_SRC);
 *
 * // Optional: lock attribute indices BEFORE the first link (or call reload() after changing bindings)
 * shader.bindAttribLocation(0, "a_pos");
 * shader.bindAttribLocation(1, "a_uv");
 * shader.reload();
 *
 * // Use
 * shader.bind();
 * shader.setUniform1i("u_tex0", 0);
 * shader.setUniform2f("u_resolution", Window.getWidth(), Window.getHeight());
 * shader.unbind();
 *
 * // Cleanup
 * shader.dispose();
 * }</pre>
 *
 * <h2>Features</h2>
 * <ul>
 *     <li><b>Compile + link</b> of vertex + fragment shader sources into one program.</li>
 *     <li><b>Hot reload</b> using stored sources or new sources via {@link #reload()} / {@link #reload(String, String)}.</li>
 *     <li><b>Uniform caching</b> for {@code glGetUniformLocation} calls.</li>
 *     <li><b>Attribute caching</b> for {@code glGetAttribLocation} calls.</li>
 *     <li><b>Uniform block caching</b> for {@code glGetUniformBlockIndex} calls.</li>
 *     <li><b>Explicit attribute bindings</b> via {@link #bindAttribLocation(int, String)} (applied at link time).</li>
 * </ul>
 *
 * <h2>Notes</h2>
 * <ul>
 *     <li>Attribute bindings must be set <b>before linking</b>. If you call {@link #bindAttribLocation(int, String)}
 *     after the program is already built, call {@link #reload()} to relink and apply them.</li>
 *     <li>Uniforms/attributes may be optimized out by the GLSL compiler; their locations can be {@code -1}.</li>
 *     <li>{@link #dispose()} deletes the program and attached shaders, and clears all caches.</li>
 * </ul>
 *
 * @author Albert Beaupre
 * @since February 12th, 2026
 */
public class Shader {

    private int programID; // OpenGL program object id (0 if not created/disposed).
    private int vertexID; // OpenGL vertex shader object id (0 if not created/disposed).
    private int fragmentID; // OpenGL fragment shader object id (0 if not created/disposed).

    private String vertexSource; // Last stored vertex shader source string (used by reload()).
    private String fragmentSource; // Last stored fragment shader source string (used by reload()).

    private final Map<String, Integer> uniformCache = new HashMap<>(); // Cache: uniform name -> location (glGetUniformLocation).
    private final Map<String, Integer> attribCache = new HashMap<>(); // Cache: attribute name -> location (glGetAttribLocation).
    private final Map<String, Integer> uniformBlockCache = new HashMap<>(); // Cache: uniform block name -> index (glGetUniformBlockIndex).
    private final Map<String, Integer> attribBindings = new HashMap<>(); // Requested attribute bindings applied during link: name -> index.

    /**
     * Creates and builds a shader program from the provided GLSL sources.
     *
     * <p>This constructor compiles both stages and links them into a program immediately.</p>
     *
     * <p>If you need explicit attribute locations, call {@link #bindAttribLocation(int, String)}
     * <b>before</b> linking. Since this constructor links immediately, the typical pattern is:</p>
     *
     * <pre>{@code
     * Shader s = new Shader(v, f);
     * s.bindAttribLocation(0, "a_pos");
     * s.bindAttribLocation(1, "a_uv");
     * s.reload(); // apply bindings by relinking
     * }</pre>
     *
     * @param vertexSource   GLSL vertex shader source (must not be null)
     * @param fragmentSource GLSL fragment shader source (must not be null)
     * @throws NullPointerException  if either source is null
     * @throws IllegalStateException if compilation or linking fails
     */
    public Shader(String vertexSource, String fragmentSource) {
        if (vertexSource == null) throw new NullPointerException("vertexSource");
        if (fragmentSource == null) throw new NullPointerException("fragmentSource");

        this.vertexSource = vertexSource;
        this.fragmentSource = fragmentSource;

        buildProgram();
    }

    /**
     * Compiles a single shader stage from source.
     *
     * <p>This method creates a shader object, assigns the source, compiles it, and validates the compile status.
     * If compilation fails, it deletes the shader object and throws an exception that includes the driver log.</p>
     *
     * @param type shader type (e.g., {@link GL20#GL_VERTEX_SHADER} or {@link GL20#GL_FRAGMENT_SHADER})
     * @param src  GLSL source code (must be non-null)
     * @return compiled shader object id
     * @throws IllegalStateException if compilation fails
     */
    private static int compile(int type, String src) {
        int id = glCreateShader(type);
        glShaderSource(id, src);
        glCompileShader(id);

        int compiled = glGetShaderi(id, GL_COMPILE_STATUS);
        if (compiled == GL_FALSE) {
            String log = glGetShaderInfoLog(id);
            glDeleteShader(id);
            throw new IllegalStateException("Shader compile failed:\n" + log);
        }
        return id;
    }

    /**
     * Builds (or rebuilds) the OpenGL program from the currently stored sources.
     *
     * <p>Steps:</p>
     * <ol>
     *     <li>Compile vertex + fragment shaders.</li>
     *     <li>Create a new program and attach both stages.</li>
     *     <li>Apply requested attribute bindings (if any) before linking.</li>
     *     <li>Link the program and fail hard if linking fails.</li>
     *     <li>Validate the program (prints a warning log if validation fails).</li>
     *     <li>If a previous program existed, safely detach/delete old program and shaders.</li>
     *     <li>Swap in the new ids and clear lookup caches (locations can change after relink).</li>
     * </ol>
     *
     * <p>This method is the core of hot reload. It is intentionally strict on compile/link errors,
     * because using a half-built program usually leads to undefined rendering behavior.</p>
     *
     * @throws IllegalStateException if compilation or linking fails
     */
    private void buildProgram() {
        int newVert = compile(GL_VERTEX_SHADER, vertexSource);
        int newFrag = compile(GL_FRAGMENT_SHADER, fragmentSource);
        int newProg = glCreateProgram();

        glAttachShader(newProg, newVert);
        glAttachShader(newProg, newFrag);

        for (Map.Entry<String, Integer> e : attribBindings.entrySet()) {
            glBindAttribLocation(newProg, e.getValue(), e.getKey());
        }

        glLinkProgram(newProg);

        int linked = glGetProgrami(newProg, GL_LINK_STATUS);
        if (linked == GL_FALSE) {
            String log = glGetProgramInfoLog(newProg);
            glDetachShader(newProg, newVert);
            glDetachShader(newProg, newFrag);
            glDeleteProgram(newProg);
            glDeleteShader(newVert);
            glDeleteShader(newFrag);
            throw new IllegalStateException("Shader link failed:\n" + log);
        }

        glValidateProgram(newProg);
        int validated = glGetProgrami(newProg, GL_VALIDATE_STATUS);
        if (validated == GL_FALSE) {
            String log = glGetProgramInfoLog(newProg);
            System.err.println("Shader validate warning:\n" + log);
        }

        if (programID != 0) {
            if (glGetInteger(GL_CURRENT_PROGRAM) == programID) {
                glUseProgram(0);
            }

            glDetachShader(programID, vertexID);
            glDetachShader(programID, fragmentID);
            glDeleteProgram(programID);
            glDeleteShader(vertexID);
            glDeleteShader(fragmentID);
        }

        programID = newProg;
        vertexID = newVert;
        fragmentID = newFrag;

        uniformCache.clear();
        attribCache.clear();
        uniformBlockCache.clear();
    }

    /**
     * Binds this shader program.
     *
     * <p>This is a direct wrapper over {@code glUseProgram(programID)}.</p>
     *
     * <p>Call this before setting uniforms and issuing draw calls that depend on this program.</p>
     */
    public void bind() {
        glUseProgram(programID);
    }

    /**
     * Unbinds any active shader program.
     *
     * <p>This is a direct wrapper over {@code glUseProgram(0)}.</p>
     *
     * <p>Unbinding is optional in many engines, but it can be useful for debugging state leaks
     * or when mixing fixed-function pipeline behavior with shaders.</p>
     */
    public void unbind() {
        glUseProgram(0);
    }

    /**
     * Hot-reloads using the last known vertex/fragment sources.
     *
     * <p>If rebuilding succeeds, the program ids are replaced and uniform/attribute caches are cleared.</p>
     *
     * <p>If rebuilding fails, the old program remains alive and this method returns false.</p>
     *
     * @return true if reload succeeded, false otherwise
     */
    public boolean reload() {
        try {
            buildProgram();
            return true;
        } catch (RuntimeException ex) {
            System.err.println(ex.getMessage());
            return false;
        }
    }

    /**
     * Hot-reloads using new sources.
     *
     * <p>This method updates the stored sources <b>only if</b> the rebuild succeeds.</p>
     *
     * <p>If the build fails, it restores the old sources and keeps the old program alive,
     * so you can retry or fall back without losing a working program.</p>
     *
     * @param newVertexSource   new vertex shader source (must not be null)
     * @param newFragmentSource new fragment shader source (must not be null)
     * @return true if reload succeeded, false if it failed
     * @throws NullPointerException if either source is null
     */
    public boolean reload(String newVertexSource, String newFragmentSource) {
        if (newVertexSource == null) throw new NullPointerException("newVertexSource");
        if (newFragmentSource == null) throw new NullPointerException("newFragmentSource");

        String oldV = this.vertexSource;
        String oldF = this.fragmentSource;

        this.vertexSource = newVertexSource;
        this.fragmentSource = newFragmentSource;

        try {
            buildProgram();
            return true;
        } catch (RuntimeException ex) {
            this.vertexSource = oldV;
            this.fragmentSource = oldF;
            System.err.println(ex.getMessage());
            return false;
        }
    }

    /**
     * Requests an explicit attribute binding (name -> index).
     *
     * <p>This is applied during {@link #buildProgram()} (before linking) via {@code glBindAttribLocation}.</p>
     *
     * <p>If the program has already been linked, call {@link #reload()} to relink and apply the new binding.</p>
     *
     * <p>This method also clears any cached attribute location for {@code name}, since a relink can change it.</p>
     *
     * @param index attribute index you want this name to occupy
     * @param name  attribute name as declared in GLSL (must not be null)
     * @throws NullPointerException if name is null
     */
    public void bindAttribLocation(int index, String name) {
        if (name == null) throw new NullPointerException("name");
        attribBindings.put(name, index);
        attribCache.remove(name);
    }

    /**
     * Returns the attribute location for the given attribute name.
     *
     * <p>This uses an internal cache to avoid repeated {@code glGetAttribLocation} calls.</p>
     *
     * <p>Drivers may optimize out unused attributes, in which case OpenGL returns {@code -1}.</p>
     *
     * @param name attribute name in GLSL (must not be null)
     * @return attribute location, or -1 if not found/optimized out
     * @throws NullPointerException if name is null
     */
    public int getAttribLocation(String name) {
        if (name == null) throw new NullPointerException("name");

        Integer cached = attribCache.get(name);
        if (cached != null) return cached;

        int loc = glGetAttribLocation(programID, name);
        attribCache.put(name, loc);
        return loc;
    }

    /**
     * Returns the uniform location for a given uniform name, querying OpenGL if needed.
     *
     * <p>This uses an internal cache because {@code glGetUniformLocation} can be relatively expensive.</p>
     *
     * <p>Drivers may optimize out uniforms that are never used; such uniforms return {@code -1}.</p>
     *
     * @param name uniform name in GLSL (must not be null)
     * @return uniform location, or -1 if not found/optimized out
     * @throws NullPointerException if name is null
     */
    private int uniformLocation(String name) {
        if (name == null) throw new NullPointerException("name");

        Integer cached = uniformCache.get(name);
        if (cached != null) return cached;

        int loc = glGetUniformLocation(programID, name);
        uniformCache.put(name, loc);
        return loc;
    }

    /**
     * Sets an {@code int} uniform (1 component).
     *
     * <p>This method looks up the uniform location (cached) and calls {@code glUniform1i} if the location is valid.</p>
     *
     * <p>If the uniform is optimized out (location {@code -1}), this method does nothing.</p>
     *
     * @param name uniform name (must not be null)
     * @param v    value
     * @return this shader for chaining
     * @throws NullPointerException if name is null
     */
    public Shader setUniform1i(String name, int v) {
        int loc = uniformLocation(name);
        if (loc != -1) glUniform1i(loc, v);
        return this;
    }

    /**
     * Sets a {@code float} uniform (1 component).
     *
     * <p>This method looks up the uniform location (cached) and calls {@code glUniform1f} if the location is valid.</p>
     *
     * <p>If the uniform is optimized out (location {@code -1}), this method does nothing.</p>
     *
     * @param name uniform name (must not be null)
     * @param v    value
     * @return this shader for chaining
     * @throws NullPointerException if name is null
     */
    public Shader setUniform1f(String name, float v) {
        int loc = uniformLocation(name);
        if (loc != -1) glUniform1f(loc, v);
        return this;
    }

    /**
     * Sets a {@code vec2} uniform.
     *
     * <p>This method looks up the uniform location (cached) and calls {@code glUniform2f} if the location is valid.</p>
     *
     * <p>If the uniform is optimized out (location {@code -1}), this method does nothing.</p>
     *
     * @param name uniform name (must not be null)
     * @param x    x component
     * @param y    y component
     * @return this shader for chaining
     * @throws NullPointerException if name is null
     */
    public Shader setUniform2f(String name, float x, float y) {
        int loc = uniformLocation(name);
        if (loc != -1) glUniform2f(loc, x, y);
        return this;
    }

    /**
     * Sets a {@code vec3} uniform.
     *
     * <p>This method looks up the uniform location (cached) and calls {@code glUniform3f} if the location is valid.</p>
     *
     * <p>If the uniform is optimized out (location {@code -1}), this method does nothing.</p>
     *
     * @param name uniform name (must not be null)
     * @param x    x component
     * @param y    y component
     * @param z    z component
     * @throws NullPointerException if name is null
     */
    public void setUniform3f(String name, float x, float y, float z) {
        int loc = uniformLocation(name);
        if (loc != -1) glUniform3f(loc, x, y, z);
    }

    /**
     * Sets a {@code vec4} uniform.
     *
     * <p>This method looks up the uniform location (cached) and calls {@code glUniform4f} if the location is valid.</p>
     *
     * <p>If the uniform is optimized out (location {@code -1}), this method does nothing.</p>
     *
     * @param name uniform name (must not be null)
     * @param x    x component
     * @param y    y component
     * @param z    z component
     * @param w    w component
     * @return this shader for chaining
     * @throws NullPointerException if name is null
     */
    public Shader setUniform4f(String name, float x, float y, float z, float w) {
        int loc = uniformLocation(name);
        if (loc != -1) glUniform4f(loc, x, y, z, w);
        return this;
    }

    /**
     * Gets the uniform block index for a named uniform block.
     *
     * <p>This uses an internal cache to avoid repeated {@code glGetUniformBlockIndex} calls.</p>
     *
     * <p>If the block is not present (or was optimized out), OpenGL may return {@code GL_INVALID_INDEX}.
     * This method normalizes that to {@code -1}.</p>
     *
     * @param blockName uniform block name in GLSL (must not be null)
     * @return block index, or -1 if not found/optimized out
     * @throws NullPointerException if blockName is null
     */
    public int getUniformBlockIndex(String blockName) {
        if (blockName == null) throw new NullPointerException("blockName");

        Integer cached = uniformBlockCache.get(blockName);
        if (cached != null) return cached;

        int idx = glGetUniformBlockIndex(programID, blockName);
        if (idx == GL_INVALID_INDEX) idx = -1;

        uniformBlockCache.put(blockName, idx);
        return idx;
    }

    /**
     * Binds a named uniform block to a binding point.
     *
     * <p>This is a convenience wrapper for:</p>
     * <ol>
     *     <li>{@link #getUniformBlockIndex(String)} to resolve the block index</li>
     *     <li>{@code glUniformBlockBinding(programID, blockIndex, bindingPoint)}</li>
     * </ol>
     *
     * <p>Typical usage is to bind the block to a fixed binding point, then bind your UBO to the same point:</p>
     * <pre>{@code
     * shader.bindUniformBlock("Globals", 0);
     * glBindBufferBase(GL_UNIFORM_BUFFER, 0, uboId);
     * }</pre>
     *
     * @param blockName    uniform block name in GLSL (must not be null)
     * @param bindingPoint binding point index used with glBindBufferBase
     * @return true if the block exists and was bound, false if not found/optimized out
     * @throws NullPointerException if blockName is null
     */
    public boolean bindUniformBlock(String blockName, int bindingPoint) {
        int idx = getUniformBlockIndex(blockName);
        if (idx == -1) return false;
        glUniformBlockBinding(programID, idx, bindingPoint);
        return true;
    }

    /**
     * Binds a uniform block (by index) to a binding point.
     *
     * <p>This is useful when you already cached the block index yourself.</p>
     *
     * <p>If {@code blockIndex < 0}, this method does nothing.</p>
     *
     * @param blockIndex   uniform block index (-1 does nothing)
     * @param bindingPoint binding point index
     */
    public void bindUniformBlock(int blockIndex, int bindingPoint) {
        if (blockIndex < 0) return;
        glUniformBlockBinding(programID, blockIndex, bindingPoint);
    }

    /**
     * Returns the OpenGL program id.
     *
     * <p>Useful for advanced interop (manual uniform setting, pipeline debugging, etc.).</p>
     *
     * @return program id (0 if disposed or never built)
     */
    public int getProgramID() {
        return programID;
    }

    /**
     * Returns the last stored vertex shader source.
     *
     * <p>This is the source used when calling {@link #reload()}.</p>
     *
     * @return vertex shader source (may be null only if you deliberately pass null via reflection, etc.)
     */
    public String getVertexSource() {
        return vertexSource;
    }

    /**
     * Returns the last stored fragment shader source.
     *
     * <p>This is the source used when calling {@link #reload()}.</p>
     *
     * @return fragment shader source (may be null only if you deliberately pass null via reflection, etc.)
     */
    public String getFragmentSource() {
        return fragmentSource;
    }

    /**
     * Deletes the program and shader objects and clears all caches/bindings.
     *
     * <p>This method is safe to call multiple times.</p>
     *
     * <p>It will unbind the program first to avoid leaving OpenGL bound to a deleted id.</p>
     */
    public void dispose() {
        unbind();

        if (programID != 0) {
            glDetachShader(programID, vertexID);
            glDetachShader(programID, fragmentID);
            glDeleteProgram(programID);
        }
        if (vertexID != 0) glDeleteShader(vertexID);
        if (fragmentID != 0) glDeleteShader(fragmentID);

        programID = 0;
        vertexID = 0;
        fragmentID = 0;

        uniformCache.clear();
        attribCache.clear();
        uniformBlockCache.clear();
        attribBindings.clear();
    }
}