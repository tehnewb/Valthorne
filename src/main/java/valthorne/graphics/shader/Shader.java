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

    private int programID;                                          // OpenGL program object id (0 if not created/disposed).
    private int vertexID;                                           // OpenGL vertex shader object id (0 if not created/disposed).
    private int fragmentID;                                         // OpenGL fragment shader object id (0 if not created/disposed).

    private String vertexSource;                                    // Last stored vertex shader source string (used by reload()).
    private String fragmentSource;                                  // Last stored fragment shader source string (used by reload()).

    private final Map<String, Integer> uniformCache = new HashMap<>();       // Cache of uniform name -> uniform location (glGetUniformLocation).
    private final Map<String, Integer> attribCache = new HashMap<>();        // Cache of attribute name -> attribute location (glGetAttribLocation).
    private final Map<String, Integer> uniformBlockCache = new HashMap<>();  // Cache of uniform block name -> block index (glGetUniformBlockIndex).
    private final Map<String, Integer> attribBindings = new HashMap<>();     // Requested attribute bindings applied during link: name -> index.

    /**
     * Creates and builds a shader program from the provided GLSL sources.
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
     * Compiles a single shader stage.
     *
     * @param type shader type (e.g., {@link GL20#GL_VERTEX_SHADER} or {@link GL20#GL_FRAGMENT_SHADER})
     * @param src  GLSL source code
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
     * <p>This compiles the vertex/fragment shaders, attaches them, applies any requested attribute bindings,
     * links, and validates the program. If a previous program existed, it is deleted after the new program
     * is successfully created.</p>
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
     * Binds this shader program (calls {@code glUseProgram(programID)}).
     */
    public void bind() {
        glUseProgram(programID);
    }

    /**
     * Unbinds any shader program (calls {@code glUseProgram(0)}).
     */
    public void unbind() {
        glUseProgram(0);
    }

    /**
     * Hot-reloads using the last known vertex/fragment sources.
     *
     * <p>If rebuilding fails, the old program remains alive.</p>
     *
     * @return true if reload succeeded, false if it failed
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
     * Hot-reloads using new sources (also updates stored sources if build succeeds).
     *
     * <p>If rebuilding fails, this restores the old sources and keeps the old program alive.</p>
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
     * Requests an attribute binding (name -> index).
     *
     * <p>This only takes effect on link, so call this before first use, or call {@link #reload()}
     * after adding bindings if the program was already linked.</p>
     *
     * @param index attribute index
     * @param name  attribute name in GLSL
     * @throws NullPointerException if name is null
     */
    public void bindAttribLocation(int index, String name) {
        if (name == null) throw new NullPointerException("name");
        attribBindings.put(name, index);

        // Attribute locations may change after relink, so clear cache now.
        attribCache.remove(name);
    }

    /**
     * Returns the attribute location for the given attribute name.
     *
     * @param name attribute name in GLSL
     * @return location, or -1 if not found/optimized out
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
     * Returns the cached uniform location for a given uniform name, querying OpenGL if needed.
     *
     * @param name uniform name in GLSL
     * @return uniform location, or -1 if not found/optimized out
     */
    private int uniformLocation(String name) {
        Integer cached = uniformCache.get(name);
        if (cached != null) return cached;

        int loc = glGetUniformLocation(programID, name);
        uniformCache.put(name, loc);
        return loc;
    }

    /**
     * Sets an {@code int} uniform (1 component).
     *
     * @param name uniform name
     * @param v    value
     * @return this shader for chaining
     */
    public Shader setUniform1i(String name, int v) {
        int loc = uniformLocation(name);
        if (loc != -1) glUniform1i(loc, v);
        return this;
    }

    /**
     * Sets a {@code float} uniform (1 component).
     *
     * @param name uniform name
     * @param v    value
     * @return this shader for chaining
     */
    public Shader setUniform1f(String name, float v) {
        int loc = uniformLocation(name);
        if (loc != -1) glUniform1f(loc, v);
        return this;
    }

    /**
     * Sets a {@code vec2} uniform.
     *
     * @param name uniform name
     * @param x    x component
     * @param y    y component
     * @return this shader for chaining
     */
    public Shader setUniform2f(String name, float x, float y) {
        int loc = uniformLocation(name);
        if (loc != -1) glUniform2f(loc, x, y);
        return this;
    }

    /**
     * Sets a {@code vec4} uniform.
     *
     * @param name uniform name
     * @param x    x component
     * @param y    y component
     * @param z    z component
     * @param w    w component
     * @return this shader for chaining
     */
    public Shader setUniform4f(String name, float x, float y, float z, float w) {
        int loc = uniformLocation(name);
        if (loc != -1) glUniform4f(loc, x, y, z, w);
        return this;
    }

    /**
     * Gets the uniform block index for a named block.
     *
     * @param blockName uniform block name in GLSL (e.g., "Globals")
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
     * <p>GLSL example:</p>
     * <pre>{@code
     * layout(std140) uniform Globals { mat4 u_proj; };
     * }</pre>
     *
     * <p>Java example:</p>
     * <pre>{@code
     * shader.bindUniformBlock("Globals", 0);
     * glBindBufferBase(GL_UNIFORM_BUFFER, 0, uboId);
     * }</pre>
     *
     * @param blockName    uniform block name
     * @param bindingPoint binding point index used with glBindBufferBase
     * @return true if the block exists and was bound, false otherwise
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
     * @return program id
     */
    public int getProgramID() {
        return programID;
    }

    /**
     * Returns the last stored vertex shader source (used by {@link #reload()}).
     *
     * @return vertex shader source
     */
    public String getVertexSource() {
        return vertexSource;
    }

    /**
     * Returns the last stored fragment shader source (used by {@link #reload()}).
     *
     * @return fragment shader source
     */
    public String getFragmentSource() {
        return fragmentSource;
    }

    /**
     * Deletes the program and shader objects and clears all caches/bindings.
     *
     * <p>Safe to call multiple times.</p>
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