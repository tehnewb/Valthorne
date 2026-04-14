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
 * 2D radiance-cascade lighting.
 *
 * <p>Each cascade covers a fixed distance interval. Higher cascades cast longer
 * rays from fewer probes, and each stored texel represents four angularly
 * adjacent rays that have already been averaged together. Cascades are rendered
 * from far to near so a lower cascade can immediately merge the parent result
 * when one of its ray groups stays visible.
 *
 * <p>The final output is a full-screen irradiance texture reconstructed from
 * the cascade-0 probe field. {@link LightMapRenderer} additively composites
 * that field back onto the current framebuffer.
 */
public final class RadianceCascadeRenderer {

    private static final int MAX_TRACE_STEPS = 1024;

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
            uniform int   u_minSteps;
            uniform int   u_maxSteps;

            const float TAU = 6.28318530718;
            const int PREAVERAGE_RAY_COUNT = 4;
            const float EMISSION_EPSILON = 0.0001;

            bool isEmissive(vec4 sampleValue) {
                return dot(sampleValue.rgb, sampleValue.rgb) > EMISSION_EPSILON;
            }

            vec4 sampleScene(vec2 p) {
                return texture(u_scene, p / u_sceneSize);
            }

            vec4 traceRay(vec2 probePos, vec2 dir, float intervalStart, float intervalRange) {
                vec2 rayStart = probePos + dir * intervalStart;
                int stepCount = clamp(
                    int(ceil(intervalRange)),
                    u_minSteps,
                    u_maxSteps
                );
                float stepSize = intervalRange / float(stepCount);

                for (int s = 0; s < stepCount; s++) {
                    float t = (float(s) + 0.5) * stepSize;
                    vec2 p = rayStart + dir * t;
                    if (any(lessThan(p, vec2(0.0))) || any(greaterThanEqual(p, u_sceneSize))) {
                        return vec4(0.0, 0.0, 0.0, 1.0);
                    }

                    vec4 scene = sampleScene(p);
                    bool opaque = scene.a < 0.5;
                    bool emissive = isEmissive(scene);

                    if (opaque) {
                        return vec4(0.0, 0.0, 0.0, 0.0);
                    }

                    // Higher cascades can start inside the source footprint; keep marching
                    // instead of terminating the ray immediately.
                    if (emissive && s == 0 && u_level != 0) {
                        continue;
                    }

                    if (emissive) {
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
            layout(binding = 2)          uniform sampler2D         u_scene;

            uniform int   u_baseRayGroups;
            uniform float u_baseSpacing;
            uniform float u_exposure;
            uniform vec2  u_sceneSize;

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

            ivec2 clampProbeCoord(ivec2 probe) {
                ivec2 cascadeSize = textureSize(u_cascade0, 0);
                int maxProbeX = cascadeSize.x / u_baseRayGroups - 1;
                int maxProbeY = cascadeSize.y - 1;
                return ivec2(
                    clamp(probe.x, 0, maxProbeX),
                    clamp(probe.y, 0, maxProbeY)
                );
            }

            vec2 probePosition(ivec2 probe) {
                ivec2 clamped = clampProbeCoord(probe);
                return (vec2(clamped) + 0.5) * u_baseSpacing;
            }

            float segmentVisibility(vec2 start, vec2 end) {
                vec2 delta = end - start;
                float distanceToProbe = length(delta);
                if (distanceToProbe <= 0.5) {
                    return 1.0;
                }

                int steps = clamp(
                    int(ceil(distanceToProbe / max(1.0, u_baseSpacing * 0.5))),
                    1,
                    32
                );
                float stepSize = distanceToProbe / float(steps);
                vec2 dir = delta / distanceToProbe;

                for (int i = 0; i < steps; i++) {
                    vec2 p = start + dir * ((float(i) + 0.5) * stepSize);
                    if (texture(u_scene, p / u_sceneSize).a < 0.5) {
                        return 0.0;
                    }
                }
                return 1.0;
            }

            void main() {
                ivec2 coord = ivec2(gl_GlobalInvocationID.xy);
                ivec2 size = imageSize(u_irradiance);
                if (coord.x >= size.x || coord.y >= size.y) return;

                vec2 pixelPos = vec2(coord) + 0.5;
                if (texture(u_scene, pixelPos / u_sceneSize).a < 0.5) {
                    imageStore(u_irradiance, coord, vec4(0.0));
                    return;
                }

                vec3 irradiance = vec3(0.0);
                vec2 probeCoord = pixelPos / u_baseSpacing - 0.5;
                ivec2 nearestProbe = ivec2(round(probeCoord));
                float totalWeight = 0.0;

                for (int oy = -1; oy <= 1; oy++) {
                    for (int ox = -1; ox <= 1; ox++) {
                        ivec2 probe = nearestProbe + ivec2(ox, oy);
                        vec2 probePos = probePosition(probe);
                        float distanceWeight = max(0.0, 1.75 - length(probePos - pixelPos) / u_baseSpacing);
                        if (distanceWeight <= 0.0) {
                            continue;
                        }

                        float visibility = segmentVisibility(pixelPos, probePos);
                        float weight = distanceWeight * visibility;
                        if (weight <= 0.0) {
                            continue;
                        }

                        irradiance += probeIrradiance(probe.x, probe.y) * weight;
                        totalWeight += weight;
                    }
                }

                if (totalWeight > 0.0001) {
                    irradiance /= totalWeight;
                } else {
                    irradiance = probeIrradiance(nearestProbe.x, nearestProbe.y);
                }

                irradiance = vec3(1.0) - exp(-irradiance * u_exposure);
                imageStore(u_irradiance, coord, vec4(irradiance, 1.0));
            }
            """;

    private static final String BLUR_SRC = """
            #version 430
            layout(local_size_x = 8, local_size_y = 8) in;

            layout(rgba16f, binding = 0) writeonly uniform image2D u_output;
            layout(binding = 1)          uniform sampler2D         u_input;
            layout(binding = 2)          uniform sampler2D         u_scene;

            uniform ivec2 u_direction;

            void main() {
                ivec2 coord = ivec2(gl_GlobalInvocationID.xy);
                ivec2 size = imageSize(u_output);
                if (coord.x >= size.x || coord.y >= size.y) return;

                vec4 centerScene = texelFetch(u_scene, coord, 0);
                if (centerScene.a < 0.5) {
                    imageStore(u_output, coord, vec4(0.0));
                    return;
                }

                const float weights[5] = float[](
                    0.22702703,
                    0.19459459,
                    0.12162162,
                    0.05405405,
                    0.01621622
                );

                vec3 sum = texelFetch(u_input, coord, 0).rgb * weights[0];
                float total = weights[0];

                for (int i = 1; i < 5; i++) {
                    ivec2 offset = u_direction * i;
                    ivec2 plusCoord = clamp(coord + offset, ivec2(0), size - 1);
                    ivec2 minusCoord = clamp(coord - offset, ivec2(0), size - 1);

                    vec4 plusScene = texelFetch(u_scene, plusCoord, 0);
                    vec4 minusScene = texelFetch(u_scene, minusCoord, 0);

                    float plusWeight = weights[i] * step(0.5, plusScene.a);
                    float minusWeight = weights[i] * step(0.5, minusScene.a);

                    if (plusWeight > 0.0) {
                        sum += texelFetch(u_input, plusCoord, 0).rgb * plusWeight;
                        total += plusWeight;
                    }

                    if (minusWeight > 0.0) {
                        sum += texelFetch(u_input, minusCoord, 0).rgb * minusWeight;
                        total += minusWeight;
                    }
                }

                imageStore(u_output, coord, vec4(sum / max(total, 0.0001), 1.0));
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
    private LightTexture blurScratch;

    private final ComputeShader traceShader;
    private final ComputeShader applyShader;
    private final ComputeShader blurShader;

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
            throw new IllegalStateException("Radiance Cascades requires OpenGL 4.3+");
        }

        this.sceneWidth = sceneWidth;
        this.sceneHeight = sceneHeight;
        this.cascadeCount = effectiveCascadeCount(sceneWidth, sceneHeight, config);
        this.baseRays = config.baseRays;
        this.baseSpacing = config.baseSpacing;
        this.baseRange = config.baseRange;
        this.steps = config.marchSteps;
        this.exposure = config.exposure;
        this.config = config.withCascades(this.cascadeCount);

        traceShader = new ComputeShader(TRACE_SRC);
        applyShader = new ComputeShader(APPLY_SRC);
        blurShader = new ComputeShader(BLUR_SRC);

        createTextures(sceneWidth, sceneHeight);
    }

    private void createTextures(int width, int height) {
        levels = new CascadeLevel[cascadeCount];
        for (int i = 0; i < cascadeCount; i++) {
            levels[i] = new CascadeLevel(width, height, i, baseRays, baseSpacing, baseRange);
        }
        irradiance = new LightTexture(width, height);
        blurScratch = new LightTexture(width, height);
    }

    private static int effectiveCascadeCount(int sceneWidth, int sceneHeight, RadianceCascadeConfig config) {
        float diagonal = (float) Math.hypot(sceneWidth, sceneHeight);
        int count = 1;
        float intervalStart = 0f;
        float intervalLength = config.baseRange;

        while (count < config.cascades && intervalStart + intervalLength < diagonal) {
            intervalStart += intervalLength;
            intervalLength *= 4f;
            count++;
        }
        return count;
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
        traceShader.setUniform1i("u_minSteps", steps);
        traceShader.setUniform1i("u_maxSteps", Math.max(steps, MAX_TRACE_STEPS));
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
        applyShader.setUniform1f("u_baseSpacing", baseSpacing);
        applyShader.setUniform1f("u_exposure", exposure);
        applyShader.setUniform1i("u_cascade0", 1);
        applyShader.setUniform1i("u_scene", 2);
        applyShader.setUniform2f("u_sceneSize", sceneWidth, sceneHeight);

        ComputeShader.bindImage2D(irradiance.getTextureID(), 0, GL_WRITE_ONLY, GL_RGBA16F);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, levels[0].textureId);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, sceneTextureId);

        int gx = (sceneWidth + 7) / 8;
        int gy = (sceneHeight + 7) / 8;
        applyShader.dispatch(gx, gy, 1);
        ComputeShader.memoryBarrierAll();

        applyShader.unbind();

        blurIrradiance(sceneTextureId);

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0);
    }

    private void blurIrradiance(int sceneTextureId) {
        blurShader.bind();
        blurShader.setUniform1i("u_input", 1);
        blurShader.setUniform1i("u_scene", 2);

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, sceneTextureId);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, irradiance.getTextureID());
        blurShader.setUniform2i("u_direction", 1, 0);
        ComputeShader.bindImage2D(blurScratch.getTextureID(), 0, GL_WRITE_ONLY, GL_RGBA16F);
        blurShader.dispatch((sceneWidth + 7) / 8, (sceneHeight + 7) / 8, 1);
        ComputeShader.memoryBarrierAll();

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, blurScratch.getTextureID());
        blurShader.setUniform2i("u_direction", 0, 1);
        ComputeShader.bindImage2D(irradiance.getTextureID(), 0, GL_WRITE_ONLY, GL_RGBA16F);
        blurShader.dispatch((sceneWidth + 7) / 8, (sceneHeight + 7) / 8, 1);
        ComputeShader.memoryBarrierAll();

        blurShader.unbind();
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
        blurScratch.dispose();
        createTextures(newWidth, newHeight);
    }

    public void dispose() {
        traceShader.dispose();
        applyShader.dispose();
        blurShader.dispose();
        for (CascadeLevel level : levels) {
            level.dispose();
        }
        irradiance.dispose();
        blurScratch.dispose();
    }
}
