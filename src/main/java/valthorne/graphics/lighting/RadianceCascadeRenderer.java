package valthorne.graphics.lighting;

import valthorne.graphics.shader.ComputeShader;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.GL_TEXTURE2;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL30.GL_RGBA16F;
import static org.lwjgl.opengl.GL42.GL_WRITE_ONLY;

/**
 * 2D global illumination via radiance cascades.
 *
 * <p>Each cascade covers a fixed distance interval. Higher cascades cast longer
 * rays from fewer probes, and each stored texel represents four angularly
 * adjacent rays that have already been averaged together. Cascades are rendered
 * from far to near so a lower cascade can immediately merge the parent result
 * when one of its ray groups stays visible.
 *
 * <p>The final output is a low-resolution screen-space radiance field at the
 * cascade-0 probe resolution. {@link LightMapRenderer} upscales that field when
 * compositing it back onto the current framebuffer.
 */
public final class RadianceCascadeRenderer {

    public static final int DEFAULT_CASCADES = RadianceCascadeConfig.DEFAULT_CASCADES;
    public static final int DEFAULT_BASE_RAYS = RadianceCascadeConfig.DEFAULT_BASE_RAYS;
    public static final float DEFAULT_BASE_SPACING = RadianceCascadeConfig.DEFAULT_BASE_SPACING;
    public static final float DEFAULT_BASE_RANGE = RadianceCascadeConfig.DEFAULT_BASE_RANGE;
    public static final int DEFAULT_STEPS = RadianceCascadeConfig.DEFAULT_MARCH_STEPS;
    public static final float DEFAULT_EXPOSURE = RadianceCascadeConfig.DEFAULT_EXPOSURE;

    private static final String TRACE_SRC = """
            #version 430
            layout(local_size_x = 8, local_size_y = 8) in;

            layout(rgba16f, binding = 0) writeonly uniform image2D u_cascade;
            layout(binding = 1)          uniform sampler2D         u_scene;
            layout(binding = 2)          uniform sampler2D         u_parent;

            uniform int   u_hasParent;
            uniform int   u_level;
            uniform int   u_baseRays;
            uniform float u_baseSpacing;
            uniform float u_baseRange;
            uniform vec2  u_sceneSize;
            uniform int   u_steps;

            const float TAU = 6.28318530718;
            const int PREAVERAGE_RAY_COUNT = 4;

            vec4 traceRay(vec2 probePos, vec2 dir, float intervalStart, float intervalRange) {
                vec2 rayStart = probePos + dir * intervalStart;
                float stepSize = intervalRange / float(u_steps);

                for (int s = 0; s < u_steps; s++) {
                    float t = (float(s) + 0.5) * stepSize;
                    vec2 p = rayStart + dir * t;
                    if (any(lessThan(p, vec2(0.0))) || any(greaterThanEqual(p, u_sceneSize))) {
                        return vec4(0.0, 0.0, 0.0, 1.0);
                    }

                    vec4 scene = texture(u_scene, p / u_sceneSize);
                    bool hit = scene.a < 0.5 || dot(scene.rgb, scene.rgb) > 0.0001;

                    // Higher cascades should not immediately self-hit from inside an emissive volume.
                    if (hit && s == 0 && u_level != 0 && scene.a >= 0.5) {
                        return vec4(0.0, 0.0, 0.0, 1.0);
                    }

                    if (hit) {
                        return vec4(scene.rgb, 0.0);
                    }
                }

                return vec4(0.0, 0.0, 0.0, 1.0);
            }

            vec4 sampleParentProbe(int probeX, int probeY, int rayGroupIndex) {
                ivec2 parentSize = textureSize(u_parent, 0);
                int parentRayGroups = u_baseRays * (1 << (u_level * 2));
                int maxProbeX = max(0, parentSize.x / parentRayGroups - 1);
                probeX = clamp(probeX, 0, maxProbeX);
                probeY = clamp(probeY, 0, parentSize.y - 1);
                return texelFetch(u_parent, ivec2(probeX * parentRayGroups + rayGroupIndex, probeY), 0);
            }

            void main() {
                ivec2 coord = ivec2(gl_GlobalInvocationID.xy);
                ivec2 size = imageSize(u_cascade);
                if (coord.x >= size.x || coord.y >= size.y) return;

                int intervalScale = 1 << (u_level * 2);
                int actualRaysPerProbe = u_baseRays * intervalScale;
                int rayGroupsPerProbe = actualRaysPerProbe / PREAVERAGE_RAY_COUNT;
                float spacing = u_baseSpacing * float(1 << u_level);
                float intervalStart = u_baseRange * (float(intervalScale - 1) / 3.0);
                float intervalRange = u_baseRange * float(intervalScale);

                int probeX = coord.x / rayGroupsPerProbe;
                int rayGroup = coord.x % rayGroupsPerProbe;
                int probeY = coord.y;

                vec2 probePos = (vec2(float(probeX), float(probeY)) + 0.5) * spacing;
                vec4 rayGroupResult = vec4(0.0);

                for (int i = 0; i < PREAVERAGE_RAY_COUNT; i++) {
                    int actualRayIndex = rayGroup * PREAVERAGE_RAY_COUNT + i;
                    float angle = (float(actualRayIndex) + 0.5) * (TAU / float(actualRaysPerProbe));
                    vec2 dir = vec2(cos(angle), sin(angle));
                    vec4 rayResult = traceRay(probePos, dir, intervalStart, intervalRange);

                    if (u_hasParent != 0 && rayResult.a > 0.0) {
                        float parentSpacing = spacing * 2.0;
                        vec2 parentCoord = probePos / parentSpacing - 0.5;
                        ivec2 base = ivec2(floor(parentCoord));
                        vec2 w = fract(parentCoord);

                        vec4 p00 = sampleParentProbe(base.x,     base.y,     actualRayIndex);
                        vec4 p10 = sampleParentProbe(base.x + 1, base.y,     actualRayIndex);
                        vec4 p01 = sampleParentProbe(base.x,     base.y + 1, actualRayIndex);
                        vec4 p11 = sampleParentProbe(base.x + 1, base.y + 1, actualRayIndex);
                        vec4 parent = mix(mix(p00, p10, w.x), mix(p01, p11, w.x), w.y);

                        rayResult.rgb += rayResult.a * parent.rgb;
                        rayResult.a *= parent.a;
                    }

                    rayGroupResult += rayResult;
                }

                imageStore(u_cascade, coord, rayGroupResult * 0.25);
            }
            """;

    private static final String APPLY_SRC = """
            #version 430
            layout(local_size_x = 8, local_size_y = 8) in;

            layout(rgba16f, binding = 0) writeonly uniform image2D u_irradiance;
            layout(binding = 1)          uniform sampler2D         u_cascade0;

            uniform int   u_baseRayGroups;
            uniform float u_exposure;

            vec3 probeIrradiance(int probeX, int probeY) {
                ivec2 cascadeSize = textureSize(u_cascade0, 0);
                int maxProbeX = cascadeSize.x / u_baseRayGroups - 1;
                int maxProbeY = cascadeSize.y - 1;
                probeX = clamp(probeX, 0, maxProbeX);
                probeY = clamp(probeY, 0, maxProbeY);

                vec3 sum = vec3(0.0);
                for (int rayGroup = 0; rayGroup < u_baseRayGroups; rayGroup++) {
                    sum += texelFetch(u_cascade0, ivec2(probeX * u_baseRayGroups + rayGroup, probeY), 0).rgb;
                }
                return sum / float(u_baseRayGroups);
            }

            void main() {
                ivec2 coord = ivec2(gl_GlobalInvocationID.xy);
                ivec2 size = imageSize(u_irradiance);
                if (coord.x >= size.x || coord.y >= size.y) return;

                vec3 irradiance = probeIrradiance(coord.x, coord.y);
                irradiance = vec3(1.0) - exp(-irradiance * u_exposure);
                imageStore(u_irradiance, coord, vec4(irradiance, 1.0));
            }
            """;

    private final int cascadeCount;
    private final int baseRays;
    private final float baseSpacing;
    private final float baseRange;
    private final int steps;
    private final RadianceCascadeConfig config;
    private float exposure;

    private CascadeLevel[] levels;
    private LightTexture irradiance;

    private final ComputeShader traceShader;
    private final ComputeShader applyShader;

    private int sceneWidth;
    private int sceneHeight;

    public static boolean isSupported() {
        return ComputeShader.isComputeSupported();
    }

    public RadianceCascadeRenderer(int sceneWidth, int sceneHeight) {
        this(sceneWidth, sceneHeight, DEFAULT_CASCADES, DEFAULT_BASE_RAYS, DEFAULT_BASE_SPACING, DEFAULT_BASE_RANGE, DEFAULT_STEPS);
    }

    public RadianceCascadeRenderer(int sceneWidth, int sceneHeight,
                                   int cascadeCount, int baseRays,
                                   float baseSpacing, float baseRange, int steps) {
        this(sceneWidth, sceneHeight, new RadianceCascadeConfig(
                cascadeCount,
                baseRays,
                baseSpacing,
                baseRange,
                steps,
                DEFAULT_EXPOSURE
        ));
    }

    public RadianceCascadeRenderer(int sceneWidth, int sceneHeight, RadianceCascadeConfig config) {
        if (config == null) throw new NullPointerException("config cannot be null");
        if (!isSupported()) {
            throw new IllegalStateException("Radiance Cascades GI requires OpenGL 4.3+");
        }

        this.sceneWidth = sceneWidth;
        this.sceneHeight = sceneHeight;
        this.cascadeCount = config.cascades;
        this.baseRays = config.baseRays;
        this.baseSpacing = config.baseSpacing;
        this.baseRange = config.baseRange;
        this.steps = config.marchSteps;
        this.exposure = config.exposure;
        this.config = config;

        traceShader = new ComputeShader(TRACE_SRC);
        applyShader = new ComputeShader(APPLY_SRC);

        createTextures(sceneWidth, sceneHeight);
    }

    private void createTextures(int width, int height) {
        levels = new CascadeLevel[cascadeCount];
        for (int i = 0; i < cascadeCount; i++) {
            levels[i] = new CascadeLevel(width, height, i, baseRays, baseSpacing, baseRange);
        }
        irradiance = new LightTexture(levels[0].probeCountX, levels[0].probeCountY);
    }

    /**
     * Runs the full trace-and-merge pipeline followed by the final probe-field resolve.
     *
     * @param sceneBuffer the scene description buffer (RGB=emission, A=transmittance)
     */
    public void render(GISceneBuffer sceneBuffer) {
        if (sceneBuffer == null) throw new NullPointerException("sceneBuffer cannot be null");
        render(sceneBuffer.getTextureID());
    }

    public void render(int sceneTextureId) {
        traceShader.bind();
        traceShader.setUniform1i("u_baseRays", baseRays);
        traceShader.setUniform1f("u_baseSpacing", baseSpacing);
        traceShader.setUniform1f("u_baseRange", baseRange);
        traceShader.setUniform2f("u_sceneSize", sceneWidth, sceneHeight);
        traceShader.setUniform1i("u_steps", steps);
        traceShader.setUniform1i("u_scene", 1);
        traceShader.setUniform1i("u_parent", 2);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, sceneTextureId);

        for (int i = cascadeCount - 1; i >= 0; i--) {
            CascadeLevel level = levels[i];
            traceShader.setUniform1i("u_level", i);
            traceShader.setUniform1i("u_hasParent", i < cascadeCount - 1 ? 1 : 0);
            ComputeShader.bindImage2D(level.textureId, 0, GL_WRITE_ONLY, GL_RGBA16F);

            glActiveTexture(GL_TEXTURE2);
            glBindTexture(GL_TEXTURE_2D, i < cascadeCount - 1 ? levels[i + 1].textureId : 0);

            int gx = (level.probeCountX * level.rayGroupsPerProbe + 7) / 8;
            int gy = (level.probeCountY + 7) / 8;
            traceShader.dispatch(gx, gy, 1);
            ComputeShader.memoryBarrierAll();
        }

        traceShader.unbind();

        applyShader.bind();
        applyShader.setUniform1i("u_baseRayGroups", Math.max(1, baseRays / CascadeLevel.PREAVERAGE_RAY_COUNT));
        applyShader.setUniform1f("u_exposure", exposure);
        applyShader.setUniform1i("u_cascade0", 1);

        ComputeShader.bindImage2D(irradiance.getTextureID(), 0, GL_WRITE_ONLY, GL_RGBA16F);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, levels[0].textureId);

        int gx = (levels[0].probeCountX + 7) / 8;
        int gy = (levels[0].probeCountY + 7) / 8;
        applyShader.dispatch(gx, gy, 1);
        ComputeShader.memoryBarrierAll();

        applyShader.unbind();

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0);
    }

    public int getIrradianceTextureId() {
        return irradiance.getTextureID();
    }

    public void setExposure(float exposure) {
        this.exposure = exposure;
    }

    public float getExposure() {
        return exposure;
    }

    public RadianceCascadeConfig getConfig() {
        return config.withExposure(exposure);
    }

    public void resize(int newWidth, int newHeight) {
        if (sceneWidth == newWidth && sceneHeight == newHeight) return;
        sceneWidth = newWidth;
        sceneHeight = newHeight;
        for (CascadeLevel level : levels) {
            level.dispose();
        }
        irradiance.dispose();
        createTextures(newWidth, newHeight);
    }

    public void dispose() {
        traceShader.dispose();
        applyShader.dispose();
        for (CascadeLevel level : levels) {
            level.dispose();
        }
        irradiance.dispose();
    }
}
