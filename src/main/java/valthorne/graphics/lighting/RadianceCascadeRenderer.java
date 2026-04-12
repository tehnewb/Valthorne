package valthorne.graphics.lighting;

import valthorne.graphics.shader.ComputeShader;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL42.*;

/**
 * 2D global illumination via Radiance Cascades (flatland variant).
 *
 * <p>Pipeline each frame:
 * <ol>
 *   <li><b>Populate</b> – for every probe at every cascade level, raymarch the
 *       {@link GISceneBuffer} over the level's assigned distance interval and
 *       store gathered radiance + end-transmittance.</li>
 *   <li><b>Merge</b>    – walk from the coarsest level down to level 0,
 *       bilinearly sampling the parent level and accumulating radiance through
 *       the remaining transmittance.</li>
 *   <li><b>Apply</b>    – for each screen pixel, bilinearly interpolate the
 *       four nearest level-0 probes and average their ray directions to produce
 *       a per-pixel irradiance texture.</li>
 * </ol>
 *
 * <p>Requires OpenGL 4.3+ (compute shaders).
 */
public final class RadianceCascadeRenderer {

    public static final int   DEFAULT_CASCADES     = 8;
    public static final int   DEFAULT_BASE_RAYS    = 4;
    public static final float DEFAULT_BASE_SPACING = 4f;
    public static final float DEFAULT_BASE_RANGE   = 4f;
    public static final int   DEFAULT_STEPS        = 16;

    // ── Populate ─────────────────────────────────────────────────────────────
    // For each (probeX*raysPerProbe + rayIdx, probeY) texel in the cascade
    // texture, shoot one ray from the probe into the scene and store radiance+T.
    private static final String POPULATE_SRC = """
            #version 430
            layout(local_size_x = 8, local_size_y = 8) in;

            layout(rgba16f, binding = 0) writeonly uniform image2D u_cascade;
            layout(binding = 1)          uniform sampler2D         u_scene;

            uniform int   u_level;
            uniform int   u_baseRays;
            uniform float u_baseSpacing;
            uniform float u_baseRange;
            uniform vec2  u_sceneSize;
            uniform int   u_steps;

            const float TAU = 6.28318530718;

            void main() {
                ivec2 coord = ivec2(gl_GlobalInvocationID.xy);
                ivec2 sz    = imageSize(u_cascade);
                if (coord.x >= sz.x || coord.y >= sz.y) return;

                int   raysPerProbe  = u_baseRays << u_level;
                float spacing       = u_baseSpacing * float(1 << u_level);
                float intervalStart = u_level == 0
                    ? 0.0
                    : u_baseRange * float(1 << max(u_level - 1, 0));
                float intervalEnd   = u_baseRange * float(1 << u_level);

                int probeX = coord.x / raysPerProbe;
                int rayIdx = coord.x % raysPerProbe;
                int probeY = coord.y;

                vec2  probePos = (vec2(float(probeX), float(probeY)) + 0.5) * spacing;
                float angle    = (float(rayIdx) + 0.5) * (TAU / float(raysPerProbe));
                vec2  dir      = vec2(cos(angle), sin(angle));

                vec3  radiance     = vec3(0.0);
                float transmittance = 1.0;
                float stepSize     = (intervalEnd - intervalStart) / float(u_steps);

                for (int s = 0; s < u_steps; s++) {
                    float t = intervalStart + (float(s) + 0.5) * stepSize;
                    vec2  p = probePos + dir * t;
                    if (any(lessThan(p, vec2(0.0))) || any(greaterThanEqual(p, u_sceneSize))) break;

                    // scene: RGB = emission, A = transmittance (1=air, 0=wall).
                    // Threshold alpha to a hard 0/1 boundary so that NEAREST-filtered
                    // wall edges never appear semi-transparent to GI rays.
                    vec4  scene  = texture(u_scene, p / u_sceneSize);
                    float occA   = scene.a >= 0.5 ? scene.a : 0.0;

                    // Only accumulate emission if the ray hasn't entered a wall yet.
                    // (emit before blocking so the lit surface of a wall contributes.)
                    radiance     += scene.rgb * transmittance;
                    transmittance *= occA;
                    if (transmittance < 0.001) break;
                }

                imageStore(u_cascade, coord, vec4(radiance, transmittance));
            }
            """;

    // ── Merge ─────────────────────────────────────────────────────────────────
    // Reads the already-merged parent level (i+1) and accumulates its radiance
    // through the current level's end-transmittance.  Bilinearly interpolates
    // between the 4 nearest parent probes; each direction r at level i maps to
    // directions 2r and 2r+1 at level i+1.
    private static final String MERGE_SRC = """
            #version 430
            layout(local_size_x = 8, local_size_y = 8) in;

            layout(rgba16f, binding = 0) uniform image2D   u_cascadeCurrent;
            layout(binding = 1)          uniform sampler2D u_cascadeParent;

            uniform int   u_level;
            uniform int   u_baseRays;
            uniform float u_baseSpacing;

            // Fetch (average of child rays childA/childB) from parent probe (px, py).
            vec4 fetchParent(int px, int py, int childA, int childB) {
                ivec2 parentSz  = textureSize(u_cascadeParent, 0);
                int   parentRPP = u_baseRays << (u_level + 1);
                px = clamp(px, 0, parentSz.x / parentRPP - 1);
                py = clamp(py, 0, parentSz.y - 1);
                vec4 a = texelFetch(u_cascadeParent, ivec2(px * parentRPP + childA, py), 0);
                vec4 b = texelFetch(u_cascadeParent, ivec2(px * parentRPP + childB, py), 0);
                return (a + b) * 0.5;
            }

            void main() {
                ivec2 coord = ivec2(gl_GlobalInvocationID.xy);
                ivec2 sz    = imageSize(u_cascadeCurrent);
                if (coord.x >= sz.x || coord.y >= sz.y) return;

                int   raysPerProbe  = u_baseRays << u_level;
                float spacing       = u_baseSpacing * float(1 << u_level);
                float parentSpacing = spacing * 2.0;

                int probeX = coord.x / raysPerProbe;
                int rayIdx = coord.x % raysPerProbe;
                int probeY = coord.y;

                vec4 current = imageLoad(u_cascadeCurrent, coord);

                // Fractional probe coordinates in the parent grid
                vec2  probePos  = (vec2(float(probeX), float(probeY)) + 0.5) * spacing;
                vec2  parentF   = probePos / parentSpacing - 0.5;
                ivec2 base      = ivec2(floor(parentF));
                vec2  w         = fract(parentF);

                // Child ray indices at parent level
                int childA = rayIdx * 2;
                int childB = rayIdx * 2 + 1;

                // Bilinear sample from 4 parent probe corners
                vec4 p00 = fetchParent(base.x,     base.y,     childA, childB);
                vec4 p10 = fetchParent(base.x + 1, base.y,     childA, childB);
                vec4 p01 = fetchParent(base.x,     base.y + 1, childA, childB);
                vec4 p11 = fetchParent(base.x + 1, base.y + 1, childA, childB);
                vec4 parent = mix(mix(p00, p10, w.x), mix(p01, p11, w.x), w.y);

                // Merge: own radiance + transmitted parent radiance
                vec3  mergedRgb   = current.rgb + current.a * parent.rgb;
                float mergedAlpha = current.a * parent.a;

                imageStore(u_cascadeCurrent, coord, vec4(mergedRgb, mergedAlpha));
            }
            """;

    // ── Apply ─────────────────────────────────────────────────────────────────
    // Converts fully-merged cascade level 0 into a per-pixel irradiance map.
    // For each pixel, bilinearly interpolate the 4 nearest level-0 probes and
    // average their rays.  u_exposure scales the raw accumulated radiance into
    // a [0,1] range suitable for additive compositing onto the light map.
    private static final String APPLY_SRC = """
            #version 430
            layout(local_size_x = 8, local_size_y = 8) in;

            layout(rgba16f, binding = 0) writeonly uniform image2D u_irradiance;
            layout(binding = 1)          uniform sampler2D         u_cascade0;

            uniform int   u_baseRays;
            uniform float u_baseSpacing;
            uniform vec2  u_screenSize;
            uniform float u_exposure;

            // Average all rays for probe (px, py) in cascade 0.
            vec3 probeIrradiance(int px, int py) {
                ivec2 sz    = textureSize(u_cascade0, 0);
                int   maxPX = sz.x / u_baseRays - 1;
                int   maxPY = sz.y - 1;
                px = clamp(px, 0, maxPX);
                py = clamp(py, 0, maxPY);
                vec3 sum = vec3(0.0);
                for (int r = 0; r < u_baseRays; r++) {
                    sum += texelFetch(u_cascade0, ivec2(px * u_baseRays + r, py), 0).rgb;
                }
                return sum / float(u_baseRays);
            }

            void main() {
                ivec2 coord = ivec2(gl_GlobalInvocationID.xy);
                if (coord.x >= int(u_screenSize.x) || coord.y >= int(u_screenSize.y)) return;

                vec2  pixelPos = vec2(coord) + 0.5;
                vec2  probeF   = pixelPos / u_baseSpacing - 0.5;
                ivec2 base     = ivec2(floor(probeF));
                vec2  w        = fract(probeF);

                vec3 p00 = probeIrradiance(base.x,     base.y    );
                vec3 p10 = probeIrradiance(base.x + 1, base.y    );
                vec3 p01 = probeIrradiance(base.x,     base.y + 1);
                vec3 p11 = probeIrradiance(base.x + 1, base.y + 1);

                vec3 irradiance = mix(mix(p00, p10, w.x), mix(p01, p11, w.x), w.y);
                // Reinhard tone-map the accumulated radiance so values are in [0,1].
                // u_exposure lets users tune the overall GI brightness.
                irradiance = (irradiance * u_exposure) / (1.0 + irradiance * u_exposure);
                imageStore(u_irradiance, coord, vec4(irradiance, 1.0));
            }
            """;

    public static final float DEFAULT_EXPOSURE = 1f;

    private final int   cascadeCount;
    private final int   baseRays;
    private final float baseSpacing;
    private final float baseRange;
    private final int   steps;
    private       float exposure;

    private CascadeLevel[] levels;
    private LightTexture   irradiance;

    private final ComputeShader populateShader;
    private final ComputeShader mergeShader;
    private final ComputeShader applyShader;

    private int sceneWidth;
    private int sceneHeight;

    public RadianceCascadeRenderer(int sceneWidth, int sceneHeight) {
        this(sceneWidth, sceneHeight, DEFAULT_CASCADES, DEFAULT_BASE_RAYS, DEFAULT_BASE_SPACING, DEFAULT_BASE_RANGE, DEFAULT_STEPS);
    }

    public RadianceCascadeRenderer(int sceneWidth, int sceneHeight,
                                   int cascadeCount, int baseRays,
                                   float baseSpacing, float baseRange, int steps) {
        if (!ComputeShader.isComputeSupported())
            throw new IllegalStateException("Radiance Cascades GI requires OpenGL 4.3+");
        this.sceneWidth   = sceneWidth;
        this.sceneHeight  = sceneHeight;
        this.cascadeCount = cascadeCount;
        this.baseRays     = baseRays;
        this.baseSpacing  = baseSpacing;
        this.baseRange    = baseRange;
        this.steps        = steps;
        this.exposure     = DEFAULT_EXPOSURE;

        populateShader = new ComputeShader(POPULATE_SRC);
        mergeShader    = new ComputeShader(MERGE_SRC);
        applyShader    = new ComputeShader(APPLY_SRC);

        createTextures(sceneWidth, sceneHeight);
    }

    private void createTextures(int w, int h) {
        levels = new CascadeLevel[cascadeCount];
        for (int i = 0; i < cascadeCount; i++)
            levels[i] = new CascadeLevel(w, h, i, baseRays, baseSpacing, baseRange);
        irradiance = new LightTexture(w, h);
    }

    /**
     * Runs the full populate → merge → apply pipeline.
     *
     * @param sceneTextureId the {@link GISceneBuffer} texture (RGB=emission, A=transmittance)
     */
    public void render(int sceneTextureId) {
        // ── 1. Populate all levels ────────────────────────────────────────────
        populateShader.bind();
        populateShader.setUniform1i("u_baseRays",    baseRays);
        populateShader.setUniform1f("u_baseSpacing", baseSpacing);
        populateShader.setUniform1f("u_baseRange",   baseRange);
        populateShader.setUniform2f("u_sceneSize",   sceneWidth, sceneHeight);
        populateShader.setUniform1i("u_steps",       steps);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, sceneTextureId);

        for (int i = 0; i < cascadeCount; i++) {
            CascadeLevel lvl = levels[i];
            populateShader.setUniform1i("u_level", i);
            ComputeShader.bindImage2D(lvl.textureId, 0, GL_WRITE_ONLY, GL_RGBA16F);

            int gx = (lvl.probeCountX * lvl.raysPerProbe + 7) / 8;
            int gy = (lvl.probeCountY + 7) / 8;
            populateShader.dispatch(gx, gy, 1);
        }

        ComputeShader.memoryBarrierAll();
        populateShader.unbind();

        // ── 2. Merge far-to-near (level N-2 down to 0) ───────────────────────
        mergeShader.bind();
        mergeShader.setUniform1i("u_baseRays",    baseRays);
        mergeShader.setUniform1f("u_baseSpacing", baseSpacing);

        for (int i = cascadeCount - 2; i >= 0; i--) {
            CascadeLevel current = levels[i];
            CascadeLevel parent  = levels[i + 1];
            mergeShader.setUniform1i("u_level", i);

            ComputeShader.bindImage2D(current.textureId, 0, GL_READ_WRITE, GL_RGBA16F);

            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, parent.textureId);

            int gx = (current.probeCountX * current.raysPerProbe + 7) / 8;
            int gy = (current.probeCountY + 7) / 8;
            mergeShader.dispatch(gx, gy, 1);
            ComputeShader.memoryBarrierAll();
        }

        mergeShader.unbind();

        // ── 3. Apply: cascade 0 → irradiance texture ─────────────────────────
        applyShader.bind();
        applyShader.setUniform1i("u_baseRays",    baseRays);
        applyShader.setUniform1f("u_baseSpacing", baseSpacing);
        applyShader.setUniform2f("u_screenSize",  sceneWidth, sceneHeight);
        applyShader.setUniform1f("u_exposure",    exposure);

        ComputeShader.bindImage2D(irradiance.getTextureID(), 0, GL_WRITE_ONLY, GL_RGBA16F);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, levels[0].textureId);

        int gx = (sceneWidth  + 7) / 8;
        int gy = (sceneHeight + 7) / 8;
        applyShader.dispatch(gx, gy, 1);
        ComputeShader.memoryBarrierAll();

        applyShader.unbind();

        // Cleanup
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0);
    }

    public int getIrradianceTextureId() {
        return irradiance.getTextureID();
    }

    /** Controls the Reinhard exposure applied to raw irradiance before compositing. Default 1.0. */
    public void setExposure(float exposure) { this.exposure = exposure; }
    public float getExposure()              { return exposure; }

    public void resize(int newWidth, int newHeight) {
        if (sceneWidth == newWidth && sceneHeight == newHeight) return;
        sceneWidth  = newWidth;
        sceneHeight = newHeight;
        for (CascadeLevel lvl : levels) lvl.dispose();
        irradiance.dispose();
        createTextures(newWidth, newHeight);
    }

    public void dispose() {
        populateShader.dispose();
        mergeShader.dispose();
        applyShader.dispose();
        for (CascadeLevel lvl : levels) lvl.dispose();
        irradiance.dispose();
    }
}
