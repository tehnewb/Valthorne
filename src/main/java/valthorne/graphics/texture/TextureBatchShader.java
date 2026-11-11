package valthorne.graphics.texture;

import valthorne.graphics.shader.Shader;

/**
 * Owns a sprite-batch program with stable sampler routing. Stages may be packaged
 * identifiers or portable bgfx source strings. Custom stages must follow
 * the packed sprite instance contract; null or blank names select the defaults.
 * Construction initializes sampler routing once, with no per-draw name allocation.
 *
 * <pre>{@code
 * TextureBatchShader shader = new TextureBatchShader(8, "engine/batch.vert", "game/tint.frag");
 * batch.setShader(shader);
 * }</pre>
 */
public class TextureBatchShader extends Shader {
    private boolean distanceFields; // Whether the current fragment stage accepts adaptive MSDF ranges.
    private final int maxTextureUnits; // Number of sampled texture slots exposed by this program.

    /**
     * Loads the default sprite-batch stages.
     *
     * @param maxTextureUnits sampled texture slot count from one through sixteen
     */
    public TextureBatchShader(int maxTextureUnits) {
        this(maxTextureUnits, null, null);
    }

    /**
     * Loads a custom fragment stage with the default sprite vertex contract.
     *
     * @param maxTextureUnits sampled texture slot count from one through sixteen
     * @param fragmentStage packaged fragment identifier or portable source, or null for the default
     */
    public TextureBatchShader(int maxTextureUnits, String fragmentStage) {
        this(maxTextureUnits, null, fragmentStage);
    }

    /**
     * Loads packaged stages or compiles portable sources and assigns sampler slots.
     *
     * @param maxTextureUnits sampled texture slot count from one through sixteen
     * @param vertexStage packaged vertex identifier or portable source, or null for the default
     * @param fragmentStage packaged fragment identifier or portable source, or null for the default
     */
    public TextureBatchShader(int maxTextureUnits, String vertexStage, String fragmentStage) {
        this(maxTextureUnits, vertexStage, fragmentStage, null);
    }

    /**
     * Compiles portable stages with custom varying declarations or loads packaged
     * stage identifiers. Null stage overrides select the standard batch stages.
     *
     * @param maxTextureUnits sampled texture slot count from one through sixteen
     * @param vertexStage packaged vertex identifier or portable source
     * @param fragmentStage packaged fragment identifier or portable source
     * @param varyings shared varying declarations, or null for engine defaults
     */
    public TextureBatchShader(int maxTextureUnits, String vertexStage, String fragmentStage, String varyings) {
        super(selectVertex(maxTextureUnits, vertexStage), selectFragment(fragmentStage), varyings);
        this.maxTextureUnits = maxTextureUnits;
        distanceFields = bgfx().hasUniform("u_distanceRange");
        TextureBatchContract.bindSamplers(this, maxTextureUnits);
    }

    /**
     * Validates slot count before native allocation and selects the vertex stage.
     *
     * @param count sampled slot count
     * @param stage optional packaged identifier
     * @return resolved identifier
     */
    private static String selectVertex(int count, String stage) {
        /*
         * Constructor arguments are evaluated before Shader creates native resources.
         */
        if (count < 1 || count > 16) throw new IllegalArgumentException("Texture units must be between one and sixteen");
        return stage == null || stage.isBlank() ? "engine/batch.vert" : stage;
    }

    /**
     * Selects the default fragment stage for an absent override.
     *
     * @param stage optional packaged identifier
     * @return resolved identifier
     */
    private static String selectFragment(String stage) {
        /*
         * Logical identifiers stay identical across the compiled backend families.
         */
        return stage == null || stage.isBlank() ? "engine/batch.frag" : stage;
    }

    /**
     * Returns the configured sampled texture slot count.
     *
     * @return texture slot count
     */
    public int getMaxTextureUnits() {
        return maxTextureUnits;
    }
    /**
     * Reports whether the selected fragment stage handles adaptive atlas sampling.
     * @return whether coverage/MSDF textures can use this shader
     */
    public boolean supportsDistanceFields() { return distanceFields; }

    @Override
    public boolean reload(String vertexSource, String fragmentSource) {
        if (!super.reload(vertexSource, fragmentSource)) return false;
        distanceFields = bgfx().hasUniform("u_distanceRange");
        TextureBatchContract.bindSamplers(this, maxTextureUnits);
        return true;
    }

    /**
     * Reports additional vertex transforms that require scalable text rendering.
     * @return false for an ordinary batch shader
     */
    public boolean transformsText() { return false; }
}
