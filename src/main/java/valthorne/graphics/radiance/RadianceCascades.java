package valthorne.graphics.radiance;

import valthorne.graphics.shader.ComputeShader;
import valthorne.graphics.texture.Texture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.GL_RGBA16F;
import static org.lwjgl.opengl.GL42.GL_WRITE_ONLY;
import static org.lwjgl.opengl.GL43.glBindImageTexture;

/**
 * Flatland/screenspace radiance cascades built around the scaling described in the
 * original paper: probe spacing doubles per level, ray count doubles per level,
 * and interval length doubles per level while remaining contiguous.
 *
 * <p>The hierarchy is built in three stages:</p>
 * <ol>
 *     <li>Trace a short base-length interval for every level.</li>
 *     <li>Extend that interval inside the level by shifting and merging it with itself.</li>
 *     <li>Merge levels back-to-front so cascade 0 contains full-range radiance cones.</li>
 * </ol>
 *
 * <p>The final light texture resolves diffuse-like lighting by integrating the merged
 * cascade 0 cones at each pixel.</p>
 */
public final class RadianceCascades {

    private static final int WORKGROUP_SIZE = 8;
    private static final float SQRT_TWO_OVER_TWO = 0.70710677f;

    private static final String TRACE_COMPUTE = """
            #version 430 core
            layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

            layout(rgba16f, binding = 0) writeonly uniform image2D u_outputImage;
            uniform sampler2D u_sceneTexture;
            uniform ivec2 u_sceneSize;
            uniform ivec2 u_internalSceneSize;
            uniform vec2 u_sceneScale;
            uniform ivec2 u_probeCount;
            uniform int u_probeSpacing;
            uniform int u_rayCount;
            uniform float u_intervalStart;
            uniform float u_traceLength;
            uniform float u_hitThreshold;

            const float PI = 3.14159265358979323846;
            const float TAU = PI * 2.0;

            vec4 emptyInterval() {
                return vec4(0.0, 0.0, 0.0, 1.0);
            }

            bool insideInternalCell(ivec2 cell) {
                return cell.x >= 0 && cell.y >= 0 && cell.x < u_internalSceneSize.x && cell.y < u_internalSceneSize.y;
            }

            bool insideInternalPos(vec2 pos) {
                return pos.x >= 0.0 && pos.y >= 0.0
                    && pos.x < float(u_internalSceneSize.x)
                    && pos.y < float(u_internalSceneSize.y);
            }

            ivec2 internalCellToScenePixel(ivec2 internalCell) {
                vec2 scenePos = (vec2(internalCell) + vec2(0.5)) * u_sceneScale - vec2(0.5);
                return ivec2(clamp(floor(scenePos), vec2(0.0), vec2(u_sceneSize) - vec2(1.0)));
            }

            vec4 readSceneCell(ivec2 internalCell) {
                if (!insideInternalCell(internalCell)) return vec4(0.0);
                return texelFetch(u_sceneTexture, internalCellToScenePixel(internalCell), 0);
            }

            vec2 directionForRay(int rayIndex, int rayCount) {
                float angle = (float(rayIndex) + 0.5) * (TAU / float(max(rayCount, 1)));
                return vec2(cos(angle), sin(angle));
            }

            vec4 hitToInterval(vec4 sceneValue) {
                float opacity = clamp(sceneValue.a, 0.0, 1.0);
                return vec4(sceneValue.rgb, 1.0 - opacity);
            }

            vec4 traceSegment(vec2 startPos, vec2 dir, float lengthToTrace) {
                if (lengthToTrace <= 0.0001) return emptyInterval();
                if (!insideInternalPos(startPos)) return emptyInterval();

                vec2 sceneStart = clamp(startPos, vec2(0.0), vec2(u_internalSceneSize) - vec2(0.0001));
                ivec2 cell = ivec2(floor(sceneStart));
                ivec2 step = ivec2(sign(dir));

                vec2 nextBoundary = vec2(
                    step.x > 0 ? floor(sceneStart.x) + 1.0 : floor(sceneStart.x),
                    step.y > 0 ? floor(sceneStart.y) + 1.0 : floor(sceneStart.y)
                );

                vec2 tMax = vec2(
                    step.x == 0 ? 1e30 : (nextBoundary.x - sceneStart.x) / dir.x,
                    step.y == 0 ? 1e30 : (nextBoundary.y - sceneStart.y) / dir.y
                );

                vec2 tDelta = vec2(
                    step.x == 0 ? 1e30 : 1.0 / abs(dir.x),
                    step.y == 0 ? 1e30 : 1.0 / abs(dir.y)
                );

                float travelled = 0.0;
                while (travelled <= lengthToTrace && insideInternalCell(cell)) {
                    vec4 sceneValue = readSceneCell(cell);
                    if (sceneValue.a > u_hitThreshold) {
                        return hitToInterval(sceneValue);
                    }

                    if (tMax.x < tMax.y) {
                        travelled = tMax.x;
                        cell.x += step.x;
                        tMax.x += tDelta.x;
                    } else if (tMax.y < tMax.x) {
                        travelled = tMax.y;
                        cell.y += step.y;
                        tMax.y += tDelta.y;
                    } else {
                        travelled = tMax.x;
                        cell.x += step.x;
                        cell.y += step.y;
                        tMax.x += tDelta.x;
                        tMax.y += tDelta.y;
                    }
                }

                return emptyInterval();
            }

            void main() {
                ivec2 texel = ivec2(gl_GlobalInvocationID.xy);
                if (texel.y >= u_probeCount.y) return;

                int probeX = texel.x / u_rayCount;
                if (probeX >= u_probeCount.x) return;

                int rayIndex = texel.x - probeX * u_rayCount;
                vec2 probePos = (vec2(float(probeX), float(texel.y)) + vec2(0.5)) * float(u_probeSpacing);
                vec2 dir = directionForRay(rayIndex, u_rayCount);
                vec2 startPos = probePos + dir * u_intervalStart;

                imageStore(u_outputImage, texel, traceSegment(startPos, dir, u_traceLength));
            }
            """;

    private static final String EXTEND_COMPUTE = """
            #version 430 core
            layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

            layout(rgba16f, binding = 0) writeonly uniform image2D u_outputImage;
            uniform sampler2D u_intervalTexture;
            uniform ivec2 u_probeCount;
            uniform int u_probeSpacing;
            uniform int u_rayCount;
            uniform float u_shiftDistance;
            uniform int u_linearSpatial;
            uniform float u_cutoff;

            const float PI = 3.14159265358979323846;
            const float TAU = PI * 2.0;

            vec4 emptyInterval() {
                return vec4(0.0, 0.0, 0.0, 1.0);
            }

            vec2 directionForRay(int rayIndex, int rayCount) {
                float angle = (float(rayIndex) + 0.5) * (TAU / float(max(rayCount, 1)));
                return vec2(cos(angle), sin(angle));
            }

            vec4 mergeInterval(vec4 nearValue, vec4 farValue) {
                return vec4(nearValue.rgb + nearValue.a * farValue.rgb, nearValue.a * farValue.a);
            }

            vec4 fetchProbeRay(ivec2 probe, int rayIndex) {
                if (probe.x < 0 || probe.y < 0 || probe.x >= u_probeCount.x || probe.y >= u_probeCount.y) {
                    return emptyInterval();
                }
                rayIndex = clamp(rayIndex, 0, u_rayCount - 1);
                return texelFetch(u_intervalTexture, ivec2(probe.x * u_rayCount + rayIndex, probe.y), 0);
            }

            vec4 sampleInterval(vec2 samplePos, int rayIndex) {
                vec2 probeCoord = samplePos / float(u_probeSpacing) - vec2(0.5);
                if (u_linearSpatial == 0) {
                    ivec2 probe = ivec2(floor(probeCoord + vec2(0.5)));
                    return fetchProbeRay(probe, rayIndex);
                }

                ivec2 base = ivec2(floor(probeCoord));
                vec2 frac = fract(probeCoord);

                vec4 s00 = fetchProbeRay(base + ivec2(0, 0), rayIndex);
                vec4 s10 = fetchProbeRay(base + ivec2(1, 0), rayIndex);
                vec4 s01 = fetchProbeRay(base + ivec2(0, 1), rayIndex);
                vec4 s11 = fetchProbeRay(base + ivec2(1, 1), rayIndex);

                vec4 top = mix(s00, s10, frac.x);
                vec4 bottom = mix(s01, s11, frac.x);
                return mix(top, bottom, frac.y);
            }

            void main() {
                ivec2 texel = ivec2(gl_GlobalInvocationID.xy);
                if (texel.y >= u_probeCount.y) return;

                int probeX = texel.x / u_rayCount;
                if (probeX >= u_probeCount.x) return;

                int rayIndex = texel.x - probeX * u_rayCount;
                vec4 nearValue = texelFetch(u_intervalTexture, texel, 0);
                if (nearValue.a <= u_cutoff) {
                    imageStore(u_outputImage, texel, nearValue);
                    return;
                }

                vec2 probePos = (vec2(float(probeX), float(texel.y)) + vec2(0.5)) * float(u_probeSpacing);
                vec2 dir = directionForRay(rayIndex, u_rayCount);
                vec4 farValue = sampleInterval(probePos + dir * u_shiftDistance, rayIndex);

                imageStore(u_outputImage, texel, mergeInterval(nearValue, farValue));
            }
            """;

    private static final String MERGE_COMPUTE = """
            #version 430 core
            layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

            layout(rgba16f, binding = 0) writeonly uniform image2D u_outputImage;
            uniform sampler2D u_currentIntervalTexture;
            uniform sampler2D u_nextMergedTexture;
            uniform ivec2 u_currentProbeCount;
            uniform ivec2 u_nextProbeCount;
            uniform int u_currentProbeSpacing;
            uniform int u_nextProbeSpacing;
            uniform int u_currentRayCount;
            uniform int u_nextRayCount;
            uniform int u_hasNext;
            uniform int u_linearSpatial;
            uniform float u_cutoff;

            vec4 emptyInterval() {
                return vec4(0.0, 0.0, 0.0, 1.0);
            }

            vec4 mergeInterval(vec4 nearValue, vec4 farValue) {
                return vec4(nearValue.rgb + nearValue.a * farValue.rgb, nearValue.a * farValue.a);
            }

            vec4 fetchCurrent(ivec2 probe, int rayIndex) {
                if (probe.x < 0 || probe.y < 0 || probe.x >= u_currentProbeCount.x || probe.y >= u_currentProbeCount.y) {
                    return emptyInterval();
                }
                rayIndex = clamp(rayIndex, 0, u_currentRayCount - 1);
                return texelFetch(u_currentIntervalTexture, ivec2(probe.x * u_currentRayCount + rayIndex, probe.y), 0);
            }

            vec4 fetchNext(ivec2 probe, int rayIndex) {
                if (probe.x < 0 || probe.y < 0 || probe.x >= u_nextProbeCount.x || probe.y >= u_nextProbeCount.y) {
                    return emptyInterval();
                }
                rayIndex = clamp(rayIndex, 0, u_nextRayCount - 1);
                return texelFetch(u_nextMergedTexture, ivec2(probe.x * u_nextRayCount + rayIndex, probe.y), 0);
            }

            vec4 sampleNextSingleRay(vec2 samplePos, int rayIndex) {
                vec2 probeCoord = samplePos / float(u_nextProbeSpacing) - vec2(0.5);
                if (u_linearSpatial == 0) {
                    ivec2 probe = ivec2(floor(probeCoord + vec2(0.5)));
                    return fetchNext(probe, rayIndex);
                }

                ivec2 base = ivec2(floor(probeCoord));
                vec2 frac = fract(probeCoord);

                vec4 s00 = fetchNext(base + ivec2(0, 0), rayIndex);
                vec4 s10 = fetchNext(base + ivec2(1, 0), rayIndex);
                vec4 s01 = fetchNext(base + ivec2(0, 1), rayIndex);
                vec4 s11 = fetchNext(base + ivec2(1, 1), rayIndex);

                vec4 top = mix(s00, s10, frac.x);
                vec4 bottom = mix(s01, s11, frac.x);
                return mix(top, bottom, frac.y);
            }

            vec4 sampleProjectedNext(vec2 samplePos, int rayIndex) {
                if (u_hasNext == 0) {
                    return emptyInterval();
                }

                int childFactor = max(u_nextRayCount / max(u_currentRayCount, 1), 1);
                vec4 sum = vec4(0.0);
                for (int child = 0; child < childFactor; child++) {
                    sum += sampleNextSingleRay(samplePos, rayIndex * childFactor + child);
                }
                return sum / float(childFactor);
            }

            void main() {
                ivec2 texel = ivec2(gl_GlobalInvocationID.xy);
                if (texel.y >= u_currentProbeCount.y) return;

                int probeX = texel.x / u_currentRayCount;
                if (probeX >= u_currentProbeCount.x) return;

                int rayIndex = texel.x - probeX * u_currentRayCount;
                vec4 nearValue = texelFetch(u_currentIntervalTexture, texel, 0);
                if (u_hasNext == 0 || nearValue.a <= u_cutoff) {
                    imageStore(u_outputImage, texel, nearValue);
                    return;
                }

                vec2 probePos = (vec2(float(probeX), float(texel.y)) + vec2(0.5)) * float(u_currentProbeSpacing);
                vec4 farValue = sampleProjectedNext(probePos, rayIndex);

                imageStore(u_outputImage, texel, mergeInterval(nearValue, farValue));
            }
            """;

    private static final String RESOLVE_COMPUTE = """
            #version 430 core
            layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

            layout(rgba16f, binding = 0) writeonly uniform image2D u_outputImage;
            uniform sampler2D u_baseMergedTexture;
            uniform sampler2D u_sceneTexture;
            uniform ivec2 u_outputSize;
            uniform ivec2 u_solveSize;
            uniform ivec2 u_probeCount;
            uniform int u_probeSpacing;
            uniform int u_rayCount;
            uniform int u_linearSpatial;
            uniform float u_intensity;
            uniform float u_surfaceThreshold;

            vec4 emptyInterval() {
                return vec4(0.0, 0.0, 0.0, 1.0);
            }

            float luminance(vec3 value) {
                return dot(value, vec3(0.2126, 0.7152, 0.0722));
            }

            vec3 toneMap(vec3 radiance) {
                vec3 linear = max(radiance, vec3(0.0));
                return vec3(1.0) - (vec3(1.0) / pow(vec3(1.0) + linear, vec3(2.5)));
            }

            vec2 directionForRay(int rayIndex, int rayCount) {
                const float PI = 3.14159265358979323846;
                const float TAU = PI * 2.0;
                float angle = (float(rayIndex) + 0.5) * (TAU / float(max(rayCount, 1)));
                return vec2(cos(angle), sin(angle));
            }

            vec4 readScene(ivec2 pixel) {
                pixel = clamp(pixel, ivec2(0), u_outputSize - ivec2(1));
                return texelFetch(u_sceneTexture, pixel, 0);
            }

            float occupancyAt(ivec2 pixel) {
                return readScene(pixel).a > u_surfaceThreshold ? 1.0 : 0.0;
            }

            float geometryPresence(ivec2 pixel) {
                float center = occupancyAt(pixel);

                float ring1 = 0.0;
                ring1 = max(ring1, occupancyAt(pixel + ivec2(-1, 0)));
                ring1 = max(ring1, occupancyAt(pixel + ivec2(1, 0)));
                ring1 = max(ring1, occupancyAt(pixel + ivec2(0, -1)));
                ring1 = max(ring1, occupancyAt(pixel + ivec2(0, 1)));
                ring1 = max(ring1, occupancyAt(pixel + ivec2(-1, -1)));
                ring1 = max(ring1, occupancyAt(pixel + ivec2(1, -1)));
                ring1 = max(ring1, occupancyAt(pixel + ivec2(-1, 1)));
                ring1 = max(ring1, occupancyAt(pixel + ivec2(1, 1)));

                float ring2 = 0.0;
                ring2 = max(ring2, occupancyAt(pixel + ivec2(-2, 0)));
                ring2 = max(ring2, occupancyAt(pixel + ivec2(2, 0)));
                ring2 = max(ring2, occupancyAt(pixel + ivec2(0, -2)));
                ring2 = max(ring2, occupancyAt(pixel + ivec2(0, 2)));
                ring2 = max(ring2, occupancyAt(pixel + ivec2(-2, -2)));
                ring2 = max(ring2, occupancyAt(pixel + ivec2(2, -2)));
                ring2 = max(ring2, occupancyAt(pixel + ivec2(-2, 2)));
                ring2 = max(ring2, occupancyAt(pixel + ivec2(2, 2)));

                return clamp(max(center, max(ring1 * 0.82, ring2 * 0.46)), 0.0, 1.0);
            }

            vec2 estimateNormal(ivec2 pixel, out float edgeStrength) {
                float left = occupancyAt(pixel + ivec2(-1, 0));
                float right = occupancyAt(pixel + ivec2(1, 0));
                float down = occupancyAt(pixel + ivec2(0, -1));
                float up = occupancyAt(pixel + ivec2(0, 1));

                float downLeft = occupancyAt(pixel + ivec2(-1, -1));
                float downRight = occupancyAt(pixel + ivec2(1, -1));
                float upLeft = occupancyAt(pixel + ivec2(-1, 1));
                float upRight = occupancyAt(pixel + ivec2(1, 1));

                vec2 gradient = vec2(
                    (right - left) + 0.5 * ((upRight + downRight) - (upLeft + downLeft)),
                    (up - down) + 0.5 * ((upLeft + upRight) - (downLeft + downRight))
                );

                edgeStrength = length(gradient);
                if (edgeStrength <= 0.0001) {
                    return vec2(0.0, 0.0);
                }
                return -gradient / edgeStrength;
            }

            vec4 fetchBase(ivec2 probe, int rayIndex) {
                if (probe.x < 0 || probe.y < 0 || probe.x >= u_probeCount.x || probe.y >= u_probeCount.y) {
                    return emptyInterval();
                }
                rayIndex = clamp(rayIndex, 0, u_rayCount - 1);
                return texelFetch(u_baseMergedTexture, ivec2(probe.x * u_rayCount + rayIndex, probe.y), 0);
            }

            vec4 sampleBase(vec2 solvePos, int rayIndex) {
                vec2 probeCoord = solvePos / float(u_probeSpacing) - vec2(0.5);
                if (u_linearSpatial == 0) {
                    ivec2 probe = ivec2(floor(probeCoord + vec2(0.5)));
                    return fetchBase(probe, rayIndex);
                }

                ivec2 base = ivec2(floor(probeCoord));
                vec2 frac = fract(probeCoord);

                vec4 s00 = fetchBase(base + ivec2(0, 0), rayIndex);
                vec4 s10 = fetchBase(base + ivec2(1, 0), rayIndex);
                vec4 s01 = fetchBase(base + ivec2(0, 1), rayIndex);
                vec4 s11 = fetchBase(base + ivec2(1, 1), rayIndex);

                vec4 top = mix(s00, s10, frac.x);
                vec4 bottom = mix(s01, s11, frac.x);
                return mix(top, bottom, frac.y);
            }

            void main() {
                ivec2 pixel = ivec2(gl_GlobalInvocationID.xy);
                if (pixel.x >= u_outputSize.x || pixel.y >= u_outputSize.y) return;

                vec2 solvePos = (vec2(pixel) + vec2(0.5)) * vec2(u_solveSize) / vec2(u_outputSize);
                vec4 sceneValue = readScene(pixel);
                float emissiveStrength = smoothstep(0.015, 0.18, luminance(sceneValue.rgb) * sceneValue.a);
                float nearGeometry = geometryPresence(pixel);
                float edgeStrength;
                vec2 normal = estimateNormal(pixel, edgeStrength);
                float directionalMix = smoothstep(0.08, 0.9, edgeStrength) * (1.0 - emissiveStrength * 0.65);

                vec3 total = vec3(0.0);
                float totalWeight = 0.0;
                float visibleWeight = 0.0;

                for (int rayIndex = 0; rayIndex < u_rayCount; rayIndex++) {
                    vec4 sampleValue = sampleBase(solvePos, rayIndex);
                    vec2 dir = directionForRay(rayIndex, u_rayCount);
                    float hemisphereWeight = max(dot(dir, normal), 0.0);
                    float weight = mix(1.0, hemisphereWeight, directionalMix);
                    total += sampleValue.rgb * weight;
                    totalWeight += weight;
                    visibleWeight += weight * (1.0 - sampleValue.a);
                }

                vec3 resolved = total * (u_intensity / max(totalWeight, 0.0001));
                vec3 mapped = toneMap(resolved);

                float rawLuma = luminance(resolved);
                float mappedLuma = luminance(mapped);
                float visibleRatio = visibleWeight / max(totalWeight, 0.0001);
                float localLight = smoothstep(0.0025, 0.085, rawLuma);
                float bloomLight = smoothstep(0.014, 0.18, rawLuma);
                float transmittancePresence = smoothstep(0.015, 0.40, visibleRatio);
                float surfaceMask = clamp(max(nearGeometry, emissiveStrength), 0.0, 1.0);
                float airLift = localLight * (0.18 + 0.40 * transmittancePresence);

                float alpha = max(emissiveStrength, localLight * mix(0.26, 0.98, surfaceMask));
                alpha = max(alpha, airLift);
                alpha = max(alpha, bloomLight * 0.12);
                alpha *= mix(0.55, 1.0, surfaceMask);
                alpha *= smoothstep(0.01, 0.16, mappedLuma + transmittancePresence * 0.55);
                alpha = clamp(alpha, 0.0, 1.0);

                imageStore(u_outputImage, pixel, vec4(mapped, alpha));
            }
            """;

    private final RadianceCascadeSettings settings;
    private final List<RadianceCascadeLevel> levels = new ArrayList<>();
    private final ComputeShader traceShader;
    private final ComputeShader extendShader;
    private final ComputeShader mergeShader;
    private final ComputeShader resolveShader;

    private RadianceRenderTarget resolvedLight;
    private int width;
    private int height;
    private int solveWidth;
    private int solveHeight;
    private float baseIntervalLength;

    public RadianceCascades(int width, int height) {
        this(width, height, new RadianceCascadeSettings());
    }

    public RadianceCascades(int width, int height, RadianceCascadeSettings settings) {
        if (width <= 0) throw new IllegalArgumentException("width must be > 0");
        if (height <= 0) throw new IllegalArgumentException("height must be > 0");
        if (settings == null) throw new NullPointerException("settings");

        settings.validateForFlatland();

        this.width = width;
        this.height = height;
        this.settings = settings;
        this.traceShader = new ComputeShader(TRACE_COMPUTE);
        this.extendShader = new ComputeShader(EXTEND_COMPUTE);
        this.mergeShader = new ComputeShader(MERGE_COMPUTE);
        this.resolveShader = new ComputeShader(RESOLVE_COMPUTE);
        rebuild();
    }

    private void rebuild() {
        disposeTargets();

        solveWidth = Math.max(1, (width + settings.getInternalScale() - 1) / settings.getInternalScale());
        solveHeight = Math.max(1, (height + settings.getInternalScale() - 1) / settings.getInternalScale());
        baseIntervalLength = resolveBaseIntervalLength();

        buildLevels();
        resolvedLight = new RadianceRenderTarget(width, height, false, true);
    }

    private float resolveBaseIntervalLength() {
        float configured = settings.getBaseIntervalLength();
        if (configured > 0f) {
            return configured;
        }
        return settings.getBaseProbeSpacing() * SQRT_TWO_OVER_TWO;
    }

    private void buildLevels() {
        int cap = settings.getMaxCascadeTextureWidth();
        float intervalStart = 0f;
        float intervalLength = baseIntervalLength;

        for (int levelIndex = 0; ; levelIndex++) {
            int spacing = checkedShift(settings.getBaseProbeSpacing(), levelIndex);
            int rayCount = checkedShift(settings.getBaseRayCount(), levelIndex);
            int probeCountX = Math.max(1, (int) Math.ceil(solveWidth / (double) spacing));
            int probeCountY = Math.max(1, (int) Math.ceil(solveHeight / (double) spacing));
            long textureWidth = (long) probeCountX * (long) rayCount;

            if (textureWidth > cap || probeCountY > cap) {
                if (levelIndex == 0) {
                    throw new IllegalStateException(
                            "Radiance cascade base level exceeds max texture size cap " + cap
                                    + ". Increase base probe spacing, increase internal scale, or raise the texture cap."
                    );
                }
                break;
            }

            levels.add(new RadianceCascadeLevel(levelIndex, spacing, rayCount, probeCountX, probeCountY, intervalStart, intervalLength));

            if (settings.getMaxLevels() > 0 && levels.size() >= settings.getMaxLevels()) {
                break;
            }

            if (intervalStart + intervalLength >= maxSceneDistanceForSpacing(spacing)) {
                break;
            }

            intervalStart += intervalLength;
            intervalLength *= settings.getBranchFactor();
        }

        if (levels.isEmpty()) {
            throw new IllegalStateException("Radiance cascades could not build any levels");
        }
    }

    private float maxSceneDistanceForSpacing(int spacing) {
        float paddedWidth = solveWidth + spacing;
        float paddedHeight = solveHeight + spacing;
        return (float) Math.hypot(paddedWidth, paddedHeight);
    }

    private int checkedShift(int value, int shift) {
        if (shift < 0) throw new IllegalArgumentException("shift must be >= 0");
        long result = (long) value << shift;
        if (result > Integer.MAX_VALUE) {
            throw new IllegalStateException("Radiance cascade parameter overflowed 32-bit integer range");
        }
        return (int) result;
    }

    private void disposeTargets() {
        for (RadianceCascadeLevel level : levels) {
            level.dispose();
        }
        levels.clear();

        if (resolvedLight != null) {
            resolvedLight.dispose();
            resolvedLight = null;
        }
    }

    public void resize(int width, int height) {
        if (width <= 0) throw new IllegalArgumentException("width must be > 0");
        if (height <= 0) throw new IllegalArgumentException("height must be > 0");
        if (this.width == width && this.height == height) return;

        this.width = width;
        this.height = height;
        rebuild();
    }

    public void render(RadianceSceneBuffer sceneBuffer) {
        if (sceneBuffer == null) throw new NullPointerException("sceneBuffer");
        if (sceneBuffer.getWidth() != width || sceneBuffer.getHeight() != height) {
            resize(sceneBuffer.getWidth(), sceneBuffer.getHeight());
        }

        traceIntervals(sceneBuffer);
        extendIntervals();
        mergeLevels();
        resolveLighting(sceneBuffer);
    }

    private void traceIntervals(RadianceSceneBuffer sceneBuffer) {
        traceShader.bind();

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sceneBuffer.getTextureID());
        traceShader.setUniform1i("u_sceneTexture", 0);
        traceShader.setUniform2i("u_sceneSize", width, height);
        traceShader.setUniform2i("u_internalSceneSize", solveWidth, solveHeight);
        traceShader.setUniform2f("u_sceneScale", width / (float) solveWidth, height / (float) solveHeight);
        traceShader.setUniform1f("u_traceLength", baseIntervalLength);
        traceShader.setUniform1f("u_hitThreshold", settings.getHitOpacityThreshold());

        for (RadianceCascadeLevel level : levels) {
            level.resetIntervalBuild();
            traceShader.setUniform2i("u_probeCount", level.getProbeCountX(), level.getProbeCountY());
            traceShader.setUniform1i("u_probeSpacing", level.getProbeSpacing());
            traceShader.setUniform1i("u_rayCount", level.getRayCount());
            traceShader.setUniform1f("u_intervalStart", level.getIntervalStart());

            glBindImageTexture(0, level.getCurrentIntervalTextureID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA16F);
            traceShader.dispatch(groupCount(level.getTextureWidth()), groupCount(level.getTextureHeight()), 1);
            ComputeShader.memoryBarrierAll();
        }

        traceShader.unbind();
    }

    private void extendIntervals() {
        extendShader.bind();
        extendShader.setUniform1i("u_linearSpatial", settings.isBilinearFix() ? 1 : 0);
        extendShader.setUniform1f("u_cutoff", settings.getTransmittanceCutoff());

        for (RadianceCascadeLevel level : levels) {
            for (int pass = 0; pass < level.getExtensionPassCount(); pass++) {
                float shiftDistance = baseIntervalLength * (1 << pass);

                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, level.getCurrentIntervalTextureID());
                extendShader.setUniform1i("u_intervalTexture", 0);
                extendShader.setUniform2i("u_probeCount", level.getProbeCountX(), level.getProbeCountY());
                extendShader.setUniform1i("u_probeSpacing", level.getProbeSpacing());
                extendShader.setUniform1i("u_rayCount", level.getRayCount());
                extendShader.setUniform1f("u_shiftDistance", shiftDistance);

                glBindImageTexture(0, level.getAlternateIntervalTextureID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA16F);
                extendShader.dispatch(groupCount(level.getTextureWidth()), groupCount(level.getTextureHeight()), 1);
                ComputeShader.memoryBarrierAll();
                level.swapIntervalTargets();
            }
        }

        extendShader.unbind();
    }

    private void mergeLevels() {
        mergeShader.bind();
        mergeShader.setUniform1i("u_linearSpatial", settings.isBilinearFix() ? 1 : 0);
        mergeShader.setUniform1f("u_cutoff", settings.getTransmittanceCutoff());

        for (int index = levels.size() - 1; index >= 0; index--) {
            RadianceCascadeLevel current = levels.get(index);
            RadianceCascadeLevel next = index + 1 < levels.size() ? levels.get(index + 1) : null;

            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, current.getCurrentIntervalTextureID());
            mergeShader.setUniform1i("u_currentIntervalTexture", 0);
            mergeShader.setUniform2i("u_currentProbeCount", current.getProbeCountX(), current.getProbeCountY());
            mergeShader.setUniform1i("u_currentProbeSpacing", current.getProbeSpacing());
            mergeShader.setUniform1i("u_currentRayCount", current.getRayCount());

            if (next != null) {
                glActiveTexture(GL_TEXTURE1);
                glBindTexture(GL_TEXTURE_2D, next.getMergedTextureID());
                mergeShader.setUniform1i("u_nextMergedTexture", 1);
                mergeShader.setUniform1i("u_hasNext", 1);
                mergeShader.setUniform2i("u_nextProbeCount", next.getProbeCountX(), next.getProbeCountY());
                mergeShader.setUniform1i("u_nextProbeSpacing", next.getProbeSpacing());
                mergeShader.setUniform1i("u_nextRayCount", next.getRayCount());
            } else {
                glActiveTexture(GL_TEXTURE1);
                glBindTexture(GL_TEXTURE_2D, 0);
                mergeShader.setUniform1i("u_nextMergedTexture", 1);
                mergeShader.setUniform1i("u_hasNext", 0);
                mergeShader.setUniform2i("u_nextProbeCount", 1, 1);
                mergeShader.setUniform1i("u_nextProbeSpacing", 1);
                mergeShader.setUniform1i("u_nextRayCount", 1);
            }

            glBindImageTexture(0, current.getMergedTextureID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA16F);
            mergeShader.dispatch(groupCount(current.getTextureWidth()), groupCount(current.getTextureHeight()), 1);
            ComputeShader.memoryBarrierAll();
        }

        mergeShader.unbind();
    }

    private void resolveLighting(RadianceSceneBuffer sceneBuffer) {
        RadianceCascadeLevel base = levels.get(0);

        resolveShader.bind();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, base.getMergedTextureID());
        resolveShader.setUniform1i("u_baseMergedTexture", 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, sceneBuffer.getTextureID());
        resolveShader.setUniform1i("u_sceneTexture", 1);
        resolveShader.setUniform2i("u_outputSize", width, height);
        resolveShader.setUniform2i("u_solveSize", solveWidth, solveHeight);
        resolveShader.setUniform2i("u_probeCount", base.getProbeCountX(), base.getProbeCountY());
        resolveShader.setUniform1i("u_probeSpacing", base.getProbeSpacing());
        resolveShader.setUniform1i("u_rayCount", base.getRayCount());
        resolveShader.setUniform1i("u_linearSpatial", settings.isBilinearFix() ? 1 : 0);
        resolveShader.setUniform1f("u_intensity", settings.getIntensity());
        resolveShader.setUniform1f("u_surfaceThreshold", settings.getHitOpacityThreshold());

        glBindImageTexture(0, resolvedLight.getTextureID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA16F);
        resolveShader.dispatch(groupCount(width), groupCount(height), 1);
        ComputeShader.memoryBarrierAll();
        resolveShader.unbind();
    }

    private int groupCount(int value) {
        return Math.max(1, (value + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE);
    }

    public Texture getLightTexture() {
        return resolvedLight.getTexture();
    }

    public List<RadianceCascadeLevel> getLevels() {
        return Collections.unmodifiableList(levels);
    }

    public RadianceCascadeSettings getSettings() {
        return settings;
    }

    public int getSolveWidth() {
        return solveWidth;
    }

    public int getSolveHeight() {
        return solveHeight;
    }

    public void dispose() {
        disposeTargets();
        traceShader.dispose();
        extendShader.dispose();
        mergeShader.dispose();
        resolveShader.dispose();
    }
}
