package valthorne.graphics.shader;

import valthorne.graphics.Sprite;
import valthorne.graphics.texture.Texture;
import valthorne.math.MathUtils;

/**
 * Renders a vertically mirrored, tinted sprite reflection with a fading alpha
 * and optional horizontal ripple. Sampling parameters use backing-texture pixels.
 * The shader owns its OpenGL program and requires a current context for creation,
 * drawing, and disposal.
 * <p>Applying the effect temporarily moves and resizes the sprite below its original
 * bounds, then restores those bounds after successful drawing. Failures can leave
 * bounds changed or this program bound. Texture unit zero is used, and the previous
 * shader program is not restored.
 * <pre>{@code
 * ReflectionShader reflection = new ReflectionShader();
 * // During rendering, with playerSprite already initialized:
 * playerSprite.draw();
 * reflection.apply(playerSprite, elapsedSeconds, 0.35f, 0.65f,
 *         0.6f, 0.75f, 1f, 2f, 18f, 2.5f);
 * // During graphics shutdown:
 * reflection.dispose();
 * }</pre>
 *
 * @author Albert Beaupre
 */
public class ReflectionShader extends TexturedQuadShader {

    /**
     * Fragment source loaded from the packaged reflection effect shader resource.
     */
    private static final String FRAG_SRC = ShaderSources.load("effects/reflection.frag");

    /**
     * Creates a reflection shader using built-in GLSL 120 sources.
     *
     * <p>This compiles and links the shader program immediately via {@link Shader#Shader(String, String)}.</p>
     */
    public ReflectionShader() {
        super(FRAG_SRC);
    }

    /**
     * Draws a vertically reflected copy directly below the sprite using a clamped
     * fraction of its height. Temporarily changes sprite bounds and restores them
     * only after successful drawing. A nonpositive clamped amount skips drawing;
     * exceptions can leave the bounds changed or the shader bound. The prior shader
     * program is not restored.
     *
     * @param sprite sprite with a valid texture
     * @param timeSeconds animation time in seconds
     * @param amount fraction of sprite height to reflect, clamped to zero through one
     * @param alpha reflection opacity multiplier
     * @param tintR red reflection tint
     * @param tintG green reflection tint
     * @param tintB blue reflection tint
     * @param rippleAmpPx ripple displacement amplitude in texture pixels
     * @param rippleFreq spatial ripple frequency supplied to the shader
     * @param rippleSpeed temporal ripple speed supplied to the shader
     * @throws NullPointerException if sprite is null
     */
    public void apply(Sprite sprite, float timeSeconds, float amount, float alpha, float tintR, float tintG, float tintB, float rippleAmpPx, float rippleFreq, float rippleSpeed) {
        if (sprite == null) throw new NullPointerException("Sprite cannot be null");

        float ox = sprite.getX();
        float oy = sprite.getY();
        float ow = sprite.getWidth();
        float oh = sprite.getHeight();
        float a01 = MathUtils.clamp(amount, 0, 1);

        if (a01 <= 0f) return;

        float rh = oh * a01;

        sprite.setPosition(ox, oy - rh);
        sprite.setSize(ow, rh);

        bind();
        setUniform1i(UNIFORM_TEXTURE, 0);
        setUniform2f("u_texelSize", 1f / sprite.getTexture().getData().width(), 1f / sprite.getTexture().getData().height());
        setUniform1f("u_alpha", alpha);
        setUniform4f("u_tint", tintR, tintG, tintB, 1.0f);
        setUniform1f("u_time", timeSeconds);
        setUniform1f("u_rippleAmpPx", rippleAmpPx);
        setUniform1f("u_rippleFreq", rippleFreq);
        setUniform1f("u_rippleSpeed", rippleSpeed);
        sprite.draw();
        unbind();

        sprite.setPosition(ox, oy);
        sprite.setSize(ow, oh);
    }
}
