package valthorne.graphics.shader;

import valthorne.utility.properties.PropertySet;
import valthorne.utility.properties.PropertyValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.IntFunction;

import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL31.*;

/**
 * Immutable CPU shader asset containing resolved GLSL and validated initial
 * program settings. Loading does not require OpenGL. asShader/asComputeShader
 * compile independent programs on the graphics thread; the caller owns and
 * disposes them using the existing shader API. This data needs no disposal.
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * vertex:string=effect.vert
 * fragment:string=effect.frag
 * attribute.a_position:integer=0
 * define.USE_TINT:integer=1
 * uniform.int.u_texture:integer=0
 * uniform.vec4.u_tint:string=1,0.8,0.2,1
 * strictUniforms:boolean=false
 * }</pre>
 * <p>Paths are relative to the config. For each stage use exactly one of its
 * path, inline Source or cache ID properties: vertex/vertexSource/vertexID,
 * fragment/fragmentSource/fragmentID, compute/computeSource/computeID. Cache
 * IDs are nonnegative ints resolved once per stage by a caller-supplied lookup.
 * Vertex and fragment are required together; compute is exclusive with both.</p>
 *
 * <p>define.NAME values become single-line GLSL macros in stable name order,
 * inserted after the leading #version directive (comments/whitespace may precede
 * it). #line restores original source line numbers. Files are ordinary GLSL;
 * no custom #include language or implicit filesystem watching is introduced.</p>
 *
 * <p>attribute.NAME sets a nonnegative vertex location before first link.
 * uniformBlock.NAME sets a uniform-buffer binding point. uniform.TYPE.NAME
 * sets defaults; types are int, bool, float, vec2, vec3, vec4, ivec2 and mat4.
 * Vectors/matrices use comma-separated values; mat4 is column-major. int also
 * selects sampler/image units, but never binds a texture or buffer. Defaults
 * are type-checked against active GLSL uniforms. Missing/optimized-out uniforms
 * and blocks are skipped unless strictUniforms=true. Compute has no attributes.</p>
 *
 * <h2>Lifetime and state</h2>
 * <p>Runtime factories preserve the previously bound program and release newly
 * compiled programs if default initialization fails. Uniform initialization is
 * construction-time work, not a per-frame callback. Reapply defaults after a
 * successful existing Shader.reload with applyTo(shader); reload itself keeps
 * its original API and behavior. Dynamic uniforms, draw state, image/storage
 * bindings, dispatch group counts and barriers remain runtime operations.
 * Compute requires the support already enforced by ComputeShader. Loading may
 * run on asset workers; program creation, configuration and disposal require a
 * current graphics context. Existing render loops incur no new parsing or lookup
 * work beyond the existing shader wrapper.</p>
 *
 * <pre>{@code
 * ShaderData data = ShaderData.load("assets/effect.properties");
 * Shader shader = data.asShader(); // graphics thread
 * shader.bind();
 * shader.setUniform1f("u_time", elapsed);
 * // draw using the matching vertex/uniform contract
 * shader.unbind();
 * shader.dispose();
 * }</pre>
 */
public final class ShaderData {
    /**
     * Scalar properties; named define/binding/uniform properties are parsed separately.
     */
    private static final Set<String> KEYS = Set.of("vertex", "vertexSource", "vertexID", "fragment", "fragmentSource", "fragmentID", "compute", "computeSource", "computeID", "strictUniforms");

    private final PropertySet properties; // Private original settings snapshot.
    private final String vertexSource; // Preprocessed GLSL, empty for compute assets.
    private final String fragmentSource; // Preprocessed GLSL, empty for compute assets.
    private final String computeSource; // Preprocessed GLSL, empty for graphics assets.
    private final Map<String, Integer> attributes; // Immutable pre-link vertex bindings.
    private final Map<String, Integer> blocks; // Immutable initial uniform-buffer bindings.
    private final ShaderUniform[] uniforms; // Private immutable parsed uniform defaults.
    private final boolean strictUniforms; // Whether inactive/missing uniform and block names fail initialization.

    /**
     * Validates schema, resolves stage dependencies and preprocesses source once.
     * @param properties scalar config snapshot
     * @param sources source-file resolver
     * @param sourceIDs optional source-ID lookup
     */
    private ShaderData(PropertySet properties, Function<String, String> sources, IntFunction<String> sourceIDs) {
        this.properties = copy(properties);
        Map<String, Integer> attributes = new HashMap<>();
        Map<String, Integer> blocks = new HashMap<>();
        Map<String, String> defines = new TreeMap<>();
        ArrayList<ShaderUniform> uniforms = new ArrayList<>();
        Set<String> uniformNames = new HashSet<>();
        for (Map.Entry<String, PropertyValue<?>> entry : this.properties.asMap().entrySet()) {
            String key = entry.getKey();
            if (KEYS.contains(key)) continue;
            if (key.startsWith("attribute.")) {
                String name = key.substring("attribute.".length());
                identifier(name);
                if (name.startsWith("gl_")) throw new IllegalArgumentException("Built-in attributes cannot be bound: " + name);
                attributes.put(name, nonnegative(entry.getValue(), key));
            } else if (key.startsWith("uniformBlock.")) {
                String name = key.substring("uniformBlock.".length());
                if (name.isBlank() || name.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid block name");
                blocks.put(name, nonnegative(entry.getValue(), key));
            } else if (key.startsWith("define.")) {
                String name = key.substring("define.".length());
                identifier(name);
                String value = entry.getValue().asString();
                if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\0') >= 0 || value.indexOf('\\') >= 0)
                    throw new IllegalArgumentException("Defines must be single-line GLSL values: " + name);
                defines.put(name, value);
            } else if (key.startsWith("uniform.")) {
                ShaderUniform uniform = new ShaderUniform(key, entry.getValue());
                if (!uniformNames.add(uniform.name())) throw new IllegalArgumentException("Duplicate default for uniform: " + uniform.name());
                uniforms.add(uniform);
            } else throw new IllegalArgumentException("Unknown shader property: " + key);
        }
        strictUniforms = this.properties.getOrDefault("strictUniforms", false).asBoolean();
        boolean compute = stagePresent(this.properties, "compute");
        if (compute && (stagePresent(this.properties, "vertex") || stagePresent(this.properties, "fragment")))
            throw new IllegalArgumentException("Compute and vertex/fragment stages cannot be mixed");
        if (compute && !attributes.isEmpty()) throw new IllegalArgumentException("Compute shaders have no vertex attributes");
        vertexSource = compute ? "" : preprocess(resolve(this.properties, "vertex", sources, sourceIDs), defines);
        fragmentSource = compute ? "" : preprocess(resolve(this.properties, "fragment", sources, sourceIDs), defines);
        computeSource = compute ? preprocess(resolve(this.properties, "compute", sources, sourceIDs), defines) : "";
        this.attributes = Map.copyOf(attributes);
        this.blocks = Map.copyOf(blocks);
        this.uniforms = uniforms.toArray(ShaderUniform[]::new);
    }

    /**
     * Loads a filesystem config and its relative GLSL dependencies without GL calls.
     * @param path config path
     * @return immutable CPU data
     */
    public static ShaderData load(String path) {
        return new ShaderLoader().load(ShaderParameters.fromPath(path));
    }

    /**
     * Loads a filesystem config with optional cache-ID stage resolution.
     * @param path config path
     * @param sourceResolver non-null GLSL lookup
     * @return immutable CPU data
     */
    public static ShaderData load(String path, IntFunction<String> sourceResolver) {
        return new ShaderLoader().load(ShaderParameters.fromPath(path).withSourceResolver(sourceResolver));
    }

    /**
     * Parses config bytes containing inline stage sources.
     * @param configBytes UTF-8 PropertySet text
     * @return immutable CPU data
     */
    public static ShaderData load(byte[] configBytes) {
        return new ShaderLoader().load(ShaderParameters.fromBytes(configBytes, "shader-bytes"));
    }

    /**
     * Parses config bytes with named in-memory GLSL dependencies.
     * @param configBytes UTF-8 PropertySet text
     * @param sources dependency-name to source map
     * @return immutable CPU data
     */
    public static ShaderData load(byte[] configBytes, Map<String, String> sources) {
        return new ShaderLoader().load(ShaderParameters.fromBytes(configBytes, sources, "shader-bytes"));
    }

    /**
     * Snapshots in-memory settings containing inline stage sources.
     * @param properties effect settings
     * @return immutable CPU data
     */
    public static ShaderData load(PropertySet properties) {
        return load(properties, Map.of());
    }

    /**
     * Snapshots settings and resolves path properties from an immutable source map.
     * @param properties effect settings
     * @param sources dependency-name to GLSL map
     * @return immutable CPU data
     */
    public static ShaderData load(PropertySet properties, Map<String, String> sources) {
        if (sources == null) throw new IllegalArgumentException("Sources cannot be null");
        Map<String, String> snapshot = Map.copyOf(sources);
        return read(properties, snapshot::get, null);
    }

    /**
     * Resolves vertexID/fragmentID/computeID through a caller-owned source cache.
     * Each requested stage is resolved once; returned Strings are immutable and
     * callback failures propagate unchanged. No OpenGL calls occur here.
     * @param properties effect settings
     * @param sourceResolver non-null GLSL lookup on the calling/loading thread
     * @return immutable CPU data
     */
    public static ShaderData load(PropertySet properties, IntFunction<String> sourceResolver) {
        if (sourceResolver == null) throw new IllegalArgumentException("Source resolver cannot be null");
        return read(properties, ignored -> null, sourceResolver);
    }

    /**
     * Shares validation between direct factories and the asset loader.
     * @param properties original settings
     * @param sources path lookup
     * @param sourceIDs optional ID lookup
     * @return resolved data
     */
    static ShaderData read(PropertySet properties, Function<String, String> sources, IntFunction<String> sourceIDs) {
        if (properties == null) throw new IllegalArgumentException("Properties cannot be null");
        return new ShaderData(properties, sources, sourceIDs);
    }

    /**
     * Copies scalar values without nesting PropertyValue wrappers.
     * @param source source collection
     * @return independent collection
     */
    private static PropertySet copy(PropertySet source) {
        PropertySet copy = new PropertySet();
        for (Map.Entry<String, PropertyValue<?>> entry : source.asMap().entrySet()) copy.set(entry.getKey(), entry.getValue().value());
        return copy;
    }

    /**
     * Validates a GLSL identifier used for macros and attribute bindings.
     * @param name nonblank ASCII identifier
     */
    private static void identifier(String name) {
        if (name.isEmpty()) throw new IllegalArgumentException("GLSL identifier cannot be empty");
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(c == '_' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || i > 0 && c >= '0' && c <= '9'))
                throw new IllegalArgumentException("Invalid GLSL identifier: " + name);
        }
    }

    /**
     * Reads an exact nonnegative int without fractional narrowing or overflow.
     * @param value property value
     * @param key diagnostic property name
     * @return validated int
     */
    private static int nonnegative(PropertyValue<?> value, String key) {
        double number = value.asDouble();
        if (!Double.isFinite(number) || number != Math.rint(number) || number < 0 || number > Integer.MAX_VALUE)
            throw new IllegalArgumentException(key + " must be a nonnegative int");
        return (int) number;
    }

    /**
     * Detects any source selector for a shader stage.
     * @param properties settings
     * @param stage vertex, fragment or compute
     * @return whether the stage was configured
     */
    private static boolean stagePresent(PropertySet properties, String stage) {
        return properties.contains(stage) || properties.contains(stage + "Source") || properties.contains(stage + "ID");
    }

    /**
     * Resolves one unambiguous source form without compiling GLSL.
     * @param properties settings
     * @param stage required stage
     * @param paths source-file lookup
     * @param ids optional cache lookup
     * @return nonblank immutable GLSL
     */
    private static String resolve(PropertySet properties, String stage, Function<String, String> paths, IntFunction<String> ids) {
        int selectors = (properties.contains(stage) ? 1 : 0) + (properties.contains(stage + "Source") ? 1 : 0) + (properties.contains(stage + "ID") ? 1 : 0);
        if (selectors != 1) throw new IllegalArgumentException("Expected exactly one " + stage + ", " + stage + "Source or " + stage + "ID");
        String source;
        if (properties.contains(stage)) {
            String path = properties.getRequired(stage, String.class).value();
            if (path.isBlank()) throw new IllegalArgumentException("Stage path cannot be blank: " + stage);
            source = paths.apply(path);
        } else if (properties.contains(stage + "Source")) source = properties.getRequired(stage + "Source", String.class).value();
        else {
            int id = nonnegative(properties.getRequired(stage + "ID"), stage + "ID");
            if (ids == null) throw new IllegalArgumentException(stage + "ID requires a source resolver");
            source = ids.apply(id);
        }
        if (source != null && !source.isEmpty() && source.charAt(0) == '\uFEFF') source = source.substring(1);
        if (source == null || source.isBlank() || source.indexOf('\0') >= 0)
            throw new IllegalArgumentException("No valid GLSL source resolved for " + stage);
        return source;
    }

    /**
     * Skips a legal leading whitespace/comment prefix to locate a #version token.
     * @param source GLSL text
     * @return first non-comment token offset, or source length
     */
    private static int firstToken(String source) {
        int offset = 0;
        while (offset < source.length()) {
            if (Character.isWhitespace(source.charAt(offset))) offset++;
            else if (source.startsWith("//", offset)) {
                int end = source.indexOf('\n', offset + 2);
                offset = end < 0 ? source.length() : end + 1;
            } else if (source.startsWith("/*", offset)) {
                int end = source.indexOf("*/", offset + 2);
                if (end < 0) throw new IllegalArgumentException("Unclosed leading GLSL comment");
                offset = end + 2;
            } else break;
        }
        return offset;
    }

    /**
     * Finds the end of a version directive without inserting macros inside a
     * trailing block comment that continues across physical source lines.
     * @param source GLSL source
     * @param start directive start
     * @return insertion offset after the first uncommented newline or at EOF
     */
    private static int directiveEnd(String source, int start) {
        boolean block = false;
        for (int i = start; i < source.length(); i++) {
            if (block) {
                if (source.startsWith("*/", i)) {
                    block = false;
                    i++;
                }
            } else if (source.startsWith("/*", i)) {
                block = true;
                i++;
            } else if (source.startsWith("//", i)) {
                int end = source.indexOf('\n', i + 2);
                return end < 0 ? source.length() : end + 1;
            } else if (source.charAt(i) == '\n') return i + 1;
        }
        if (block) throw new IllegalArgumentException("Unclosed GLSL version comment");
        return source.length();
    }

    /**
     * Inserts macros after #version while restoring original diagnostic line numbers.
     * @param source resolved GLSL
     * @param defines stable ordered macro values
     * @return preprocessed GLSL or the original string when no macros exist
     */
    private static String preprocess(String source, Map<String, String> defines) {
        if (defines.isEmpty()) return source;
        int first = firstToken(source);
        int insertion = 0;
        int directive = first;
        if (first < source.length() && source.charAt(first) == '#') {
            directive++;
            while (directive < source.length() && source.charAt(directive) != '\n' && Character.isWhitespace(source.charAt(directive))) directive++;
        }
        if (directive > first && source.startsWith("version", directive) && (directive + 7 == source.length() || Character.isWhitespace(source.charAt(directive + 7)))) {
            insertion = directiveEnd(source, first);
        }
        int nextLine = 1;
        for (int i = 0; i < insertion; i++) if (source.charAt(i) == '\n') nextLine++;
        StringBuilder result = new StringBuilder(source.length() + defines.size() * 32 + 24);
        result.append(source, 0, insertion);
        if (insertion > 0 && source.charAt(insertion - 1) != '\n') {
            result.append('\n');
            nextLine++;
        }
        for (Map.Entry<String, String> define : defines.entrySet())
            result.append("#define ").append(define.getKey()).append(' ').append(define.getValue()).append('\n');
        result.append("#line ").append(nextLine).append('\n').append(source, insertion, source.length());
        return result.toString();
    }

    /**
     * Returns whether this asset describes a compute rather than graphics program.
     * @return true for compute
     */
    public boolean isCompute() {
        return !computeSource.isEmpty();
    }

    /**
     * Returns resolved/preprocessed vertex GLSL, or empty for compute assets.
     * @return immutable source
     */
    public String vertexSource() {
        return vertexSource;
    }

    /**
     * Returns resolved/preprocessed fragment GLSL, or empty for compute assets.
     * @return immutable source
     */
    public String fragmentSource() {
        return fragmentSource;
    }

    /**
     * Returns resolved/preprocessed compute GLSL, or empty for graphics assets.
     * @return immutable source
     */
    public String computeSource() {
        return computeSource;
    }

    /**
     * Returns independent editable original settings, not rewritten source metadata.
     * @return copied PropertySet
     */
    public PropertySet properties() {
        return copy(properties);
    }

    /**
     * Validates bindings against the current context before compiling a program.
     */
    private void validateBindings() {
        if (!attributes.isEmpty()) {
            int limit = glGetInteger(GL_MAX_VERTEX_ATTRIBS);
            for (int location : attributes.values())
                if (location >= limit) throw new IllegalArgumentException("Attribute location exceeds context limit");
        }
        if (!blocks.isEmpty()) {
            int limit = glGetInteger(GL_MAX_UNIFORM_BUFFER_BINDINGS);
            for (int binding : blocks.values())
                if (binding >= limit) throw new IllegalArgumentException("Uniform block binding exceeds context limit");
        }
    }

    /**
     * Applies post-link defaults while the new or existing program is bound.
     * @param program active target program
     */
    private void initialize(int program) {
        for (Map.Entry<String, Integer> block : blocks.entrySet()) {
            int index = glGetUniformBlockIndex(program, block.getKey());
            if (index != GL_INVALID_INDEX) glUniformBlockBinding(program, index, block.getValue());
            else if (strictUniforms) throw new IllegalArgumentException("Missing active uniform block: " + block.getKey());
        }
        for (ShaderUniform uniform : uniforms) uniform.apply(program, strictUniforms);
    }

    /**
     * Compiles a graphics program with attributes bound before its first link.
     * Restores the caller's active program and cleans up failed initialization.
     * @return independent caller-owned Shader
     */
    public Shader asShader() {
        if (isCompute()) throw new IllegalStateException("Use asComputeShader for compute data");
        validateBindings();
        int previous = glGetInteger(GL_CURRENT_PROGRAM);
        Shader shader = null;
        try {
            shader = new Shader(vertexSource, fragmentSource, target -> {
                for (Map.Entry<String, Integer> binding : attributes.entrySet()) target.bindAttribLocation(binding.getValue(), binding.getKey());
            });
            shader.bind();
            initialize(shader.getProgramID());
            return shader;
        } catch (RuntimeException | Error failure) {
            if (shader != null) shader.dispose();
            throw failure;
        } finally {
            glUseProgram(previous);
        }
    }

    /**
     * Compiles a compute program when the current context supports ComputeShader.
     * Texture/image/SSBO resources and dispatch scheduling remain caller-owned.
     * @return independent caller-owned ComputeShader
     */
    public ComputeShader asComputeShader() {
        if (!isCompute()) throw new IllegalStateException("Use asShader for vertex/fragment data");
        validateBindings();
        int previous = glGetInteger(GL_CURRENT_PROGRAM);
        ComputeShader shader = null;
        try {
            shader = new ComputeShader(computeSource);
            shader.bind();
            initialize(glGetInteger(GL_CURRENT_PROGRAM));
            return shader;
        } catch (RuntimeException | Error failure) {
            if (shader != null) shader.dispose();
            throw failure;
        } finally {
            glUseProgram(previous);
        }
    }

    /**
     * Reapplies defaults to a graphics program, for example after a successful reload.
     * Attribute bindings still require pre-link configuration; they are not changed here.
     * Initialization is sequential: failure may leave earlier defaults changed on
     * this existing target; only the previously bound program is always restored.
     * @param shader live target compiled for this config's uniform contract
     */
    public void applyTo(Shader shader) {
        if (shader == null || shader.getProgramID() == 0) throw new IllegalArgumentException("Shader must be live");
        validateBindings();
        int previous = glGetInteger(GL_CURRENT_PROGRAM);
        try {
            shader.bind();
            initialize(shader.getProgramID());
        } finally {
            glUseProgram(previous);
        }
    }

    /**
     * Reapplies defaults to a caller-owned compute program and restores prior binding.
     * @param shader live target with the matching uniform contract
     */
    public void applyTo(ComputeShader shader) {
        if (shader == null) throw new IllegalArgumentException("Shader cannot be null");
        validateBindings();
        int previous = glGetInteger(GL_CURRENT_PROGRAM);
        try {
            shader.bind();
            int program = glGetInteger(GL_CURRENT_PROGRAM);
            if (program == 0) throw new IllegalArgumentException("Compute shader must be live");
            initialize(program);
        } finally {
            glUseProgram(previous);
        }
    }
}
