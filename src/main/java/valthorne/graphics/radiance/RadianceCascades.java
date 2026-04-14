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
import static org.lwjgl.opengl.GL13.GL_TEXTURE2;
import static org.lwjgl.opengl.GL13.GL_TEXTURE3;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.GL_RGBA16F;
import static org.lwjgl.opengl.GL42.GL_WRITE_ONLY;
import static org.lwjgl.opengl.GL43.glBindImageTexture;

public final class RadianceCascades {

    private static final int WORKGROUP_SIZE = 8;

    private static final String TRACE_INIT_COMPUTE = """
            #version 430 core
            layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

            layout(rgba16f, binding = 0) writeonly uniform image2D u_outputImage;
            uniform sampler2D u_sceneTexture;
            uniform ivec2 u_sceneSize;
            uniform ivec2 u_localSize;
            uniform int u_probeSpacing;
            uniform int u_traceCount;
            uniform int u_quadrant;
            uniform float u_rayStep;
            uniform float u_cutoff;

            vec2 localToWorld(vec2 localPos) {
                if (u_quadrant == 0) return localPos;
                if (u_quadrant == 1) return vec2(float(u_sceneSize.x) - 1.0 - localPos.y, localPos.x);
                if (u_quadrant == 2) return vec2(float(u_sceneSize.x) - 1.0 - localPos.x, float(u_sceneSize.y) - 1.0 - localPos.y);
                return vec2(localPos.y, float(u_sceneSize.y) - 1.0 - localPos.x);
            }

            bool insideScene(vec2 worldPos) {
                return worldPos.x >= 0.0 && worldPos.y >= 0.0 && worldPos.x < float(u_sceneSize.x) && worldPos.y < float(u_sceneSize.y);
            }

            vec4 readScene(vec2 worldPos) {
                if (!insideScene(worldPos)) return vec4(0.0);
                ivec2 pixel = ivec2(clamp(floor(worldPos), vec2(0.0), vec2(u_sceneSize) - vec2(1.0)));
                return texelFetch(u_sceneTexture, pixel, 0);
            }

            float applyOpacity(float opacity, float stepLength) {
                opacity = clamp(opacity, 0.0, 1.0);
                if (opacity <= 0.0) return 1.0;
                return pow(max(1.0 - opacity, 0.0), stepLength / max(u_rayStep, 0.0001));
            }

            vec4 traceSegment(vec2 localStart, vec2 localEnd) {
                vec2 worldStart = localToWorld(localStart + vec2(0.5));
                vec2 worldEnd = localToWorld(localEnd + vec2(0.5));
                vec2 delta = worldEnd - worldStart;
                float distanceToTravel = length(delta);
                if (distanceToTravel <= 0.0001) return vec4(0.0, 0.0, 0.0, 1.0);
                vec2 direction = delta / distanceToTravel;
                float travelled = 0.0;
                float transmittance = 1.0;
                vec3 radiance = vec3(0.0);
                while (travelled < distanceToTravel && transmittance > u_cutoff) {
                    float stepLength = min(u_rayStep, distanceToTravel - travelled);
                    vec2 samplePoint = worldStart + direction * (travelled + 0.5 * stepLength);
                    vec4 sceneValue = readScene(samplePoint);
                    float opacity = clamp(sceneValue.a, 0.0, 1.0);
                    radiance += transmittance * sceneValue.rgb * opacity * stepLength;
                    transmittance *= applyOpacity(opacity, stepLength);
                    travelled += stepLength;
                }
                return vec4(radiance, transmittance);
            }

            vec2 v(int spacing, int kIndex) {
                return vec2(float(spacing), float(2 * kIndex - spacing));
            }

            void main() {
                ivec2 texel = ivec2(gl_GlobalInvocationID.xy);
                if (texel.y >= u_localSize.y) return;
                int probeX = texel.x / u_traceCount;
                int kIndex = texel.x - probeX * u_traceCount;
                if (probeX >= u_localSize.x || kIndex >= u_traceCount) return;
                vec2 p = vec2(float(probeX * u_probeSpacing), float(texel.y));
                vec2 q = p + v(u_probeSpacing, kIndex);
                imageStore(u_outputImage, texel, traceSegment(p, q));
            }
            """;

    private static final String TRACE_RECUR_COMPUTE = """
            #version 430 core
            layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

            layout(rgba16f, binding = 0) writeonly uniform image2D u_outputImage;
            uniform sampler2D u_prevTraceTexture;
            uniform ivec2 u_outputProbeCount;
            uniform ivec2 u_prevProbeCount;
            uniform int u_prevProbeSpacing;
            uniform int u_prevTraceCount;
            uniform int u_outputTraceCount;

            vec4 mergePair(vec4 nearValue, vec4 farValue) {
                return vec4(nearValue.rgb + nearValue.a * farValue.rgb, nearValue.a * farValue.a);
            }

            vec4 readPrev(int probeX, int probeY, int kIndex) {
                probeX = clamp(probeX, 0, u_prevProbeCount.x - 1);
                probeY = clamp(probeY, 0, u_outputProbeCount.y - 1);
                kIndex = clamp(kIndex, 0, u_prevTraceCount - 1);
                return texelFetch(u_prevTraceTexture, ivec2(probeX * u_prevTraceCount + kIndex, probeY), 0);
            }

            ivec2 offsetFor(int spacing, int kIndex) {
                return ivec2(spacing, 2 * kIndex - spacing);
            }

            void main() {
                ivec2 texel = ivec2(gl_GlobalInvocationID.xy);
                if (texel.y >= u_outputProbeCount.y) return;
                int probeX = texel.x / u_outputTraceCount;
                int kIndex = texel.x - probeX * u_outputTraceCount;
                if (probeX >= u_outputProbeCount.x || kIndex >= u_outputTraceCount) return;

                int baseProbeX = probeX * 2;
                vec4 value;
                if ((kIndex & 1) == 0) {
                    int childK = kIndex >> 1;
                    vec4 nearValue = readPrev(baseProbeX, texel.y, childK);
                    ivec2 off = offsetFor(u_prevProbeSpacing, childK);
                    int farProbeX = (baseProbeX * u_prevProbeSpacing + off.x) / u_prevProbeSpacing;
                    int farProbeY = texel.y + off.y;
                    vec4 farValue = readPrev(farProbeX, farProbeY, childK);
                    value = mergePair(nearValue, farValue);
                } else {
                    int leftK = (kIndex - 1) >> 1;
                    int rightK = leftK + 1;
                    ivec2 offL = offsetFor(u_prevProbeSpacing, leftK);
                    ivec2 offR = offsetFor(u_prevProbeSpacing, rightK);
                    vec4 fMinus = mergePair(readPrev(baseProbeX, texel.y, leftK), readPrev((baseProbeX * u_prevProbeSpacing + offL.x) / u_prevProbeSpacing, texel.y + offL.y, rightK));
                    vec4 fPlus = mergePair(readPrev(baseProbeX, texel.y, rightK), readPrev((baseProbeX * u_prevProbeSpacing + offR.x) / u_prevProbeSpacing, texel.y + offR.y, leftK));
                    value = (fMinus + fPlus) * 0.5;
                }
                imageStore(u_outputImage, texel, value);
            }
            """;

    private static final String RADIANCE_BUILD_COMPUTE = """
            #version 430 core
            layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

            layout(rgba16f, binding = 0) writeonly uniform image2D u_outputImage;
            uniform sampler2D u_traceTexture;
            uniform sampler2D u_nextTraceTexture;
            uniform sampler2D u_nextRadianceTexture;
            uniform ivec2 u_outputProbeCount;
            uniform ivec2 u_nextProbeCount;
            uniform int u_levelSpacing;
            uniform int u_outputRayCount;
            uniform int u_traceCount;
            uniform int u_nextTraceCount;
            uniform int u_nextRayCount;
            uniform int u_hasNextRadiance;

            float coneAngle(int spacing, int edgeIndexA, int edgeIndexB) {
                float a0 = atan(float(2 * edgeIndexA - spacing), float(spacing));
                float a1 = atan(float(2 * edgeIndexB - spacing), float(spacing));
                return a1 - a0;
            }

            vec4 mergeRay(vec4 nearValue, vec4 farFluence) {
                return vec4(nearValue.rgb + nearValue.a * farFluence.rgb, nearValue.a * farFluence.a);
            }

            vec4 readTrace(sampler2D textureSampler, int traceCount, int probeX, int probeY, int kIndex, ivec2 probeCount) {
                probeX = clamp(probeX, 0, probeCount.x - 1);
                probeY = clamp(probeY, 0, probeCount.y - 1);
                kIndex = clamp(kIndex, 0, traceCount - 1);
                return texelFetch(textureSampler, ivec2(probeX * traceCount + kIndex, probeY), 0);
            }

            vec4 readRadiance(int probeX, int probeY, int rayIndex) {
                if (u_hasNextRadiance == 0) return vec4(0.0);
                probeX = clamp(probeX, 0, u_nextProbeCount.x - 1);
                probeY = clamp(probeY, 0, u_nextProbeCount.y - 1);
                rayIndex = clamp(rayIndex, 0, u_nextRayCount - 1);
                return texelFetch(u_nextRadianceTexture, ivec2(probeX * u_nextRayCount + rayIndex, probeY), 0);
            }

            ivec2 localOffset(int spacing, int edgeIndex) {
                return ivec2(spacing, 2 * edgeIndex - spacing);
            }

            void main() {
                ivec2 texel = ivec2(gl_GlobalInvocationID.xy);
                if (texel.y >= u_outputProbeCount.y) return;
                int probeX = texel.x / u_outputRayCount;
                int rayIndex = texel.x - probeX * u_outputRayCount;
                if (probeX >= u_outputProbeCount.x || rayIndex >= u_outputRayCount) return;


                int edge0 = rayIndex;
                int edge1 = rayIndex + 1;
                float childAngle0 = coneAngle(u_levelSpacing * 2, 2 * rayIndex, 2 * rayIndex + 1);
                float childAngle1 = coneAngle(u_levelSpacing * 2, 2 * rayIndex + 1, 2 * rayIndex + 2);
                int nextRay0 = 2 * rayIndex;
                int nextRay1 = 2 * rayIndex + 1;
                int localX = probeX;
                int localY = texel.y;

                vec4 fMinus;
                vec4 fPlus;

                if ((localX & 1) == 1) {
                    ivec2 off0 = localOffset(u_levelSpacing, edge0);
                    ivec2 off1 = localOffset(u_levelSpacing, edge1);
                    int qx0 = (localX * u_levelSpacing + off0.x) / (u_levelSpacing * 2);
                    int qx1 = (localX * u_levelSpacing + off1.x) / (u_levelSpacing * 2);
                    int qy0 = localY + off0.y;
                    int qy1 = localY + off1.y;
                    vec4 near0 = readTrace(u_traceTexture, u_traceCount, localX, localY, edge0, u_outputProbeCount);
                    vec4 near1 = readTrace(u_traceTexture, u_traceCount, localX, localY, edge1, u_outputProbeCount);
                    fMinus = mergeRay(vec4(near0.rgb * childAngle0, near0.a), readRadiance(qx0, qy0, nextRay0));
                    fPlus = mergeRay(vec4(near1.rgb * childAngle1, near1.a), readRadiance(qx1, qy1, nextRay1));
                } else {
                    vec4 f0Minus = readRadiance(localX >> 1, localY, nextRay0);
                    vec4 f0Plus = readRadiance(localX >> 1, localY, nextRay1);
                    vec4 near0 = readTrace(u_nextTraceTexture, u_nextTraceCount, localX >> 1, localY, 2 * rayIndex, u_nextProbeCount);
                    vec4 near1 = readTrace(u_nextTraceTexture, u_nextTraceCount, localX >> 1, localY, 2 * rayIndex + 2, u_nextProbeCount);
                    ivec2 off0 = localOffset(u_levelSpacing * 2, 2 * rayIndex);
                    ivec2 off1 = localOffset(u_levelSpacing * 2, 2 * rayIndex + 2);
                    int qx0 = ((localX >> 1) * (u_levelSpacing * 2) + off0.x) / (u_levelSpacing * 2);
                    int qx1 = ((localX >> 1) * (u_levelSpacing * 2) + off1.x) / (u_levelSpacing * 2);
                    int qy0 = localY + off0.y;
                    int qy1 = localY + off1.y;
                    vec4 f1Minus = mergeRay(vec4(near0.rgb * childAngle0, near0.a), readRadiance(qx0, qy0, nextRay0));
                    vec4 f1Plus = mergeRay(vec4(near1.rgb * childAngle1, near1.a), readRadiance(qx1, qy1, nextRay1));
                    fMinus = (f0Minus + f1Minus) * 0.5;
                    fPlus = (f0Plus + f1Plus) * 0.5;
                }

                imageStore(u_outputImage, texel, fMinus + fPlus);
            }
            """;

    private static final String RESOLVE_COMPUTE = """
            #version 430 core
            layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

            layout(rgba16f, binding = 0) writeonly uniform image2D u_outputImage;
            uniform sampler2D u_r0East;
            uniform sampler2D u_r0North;
            uniform sampler2D u_r0West;
            uniform sampler2D u_r0South;
            uniform ivec2 u_sceneSize;
            uniform ivec2 u_eastProbeCount;
            uniform ivec2 u_northProbeCount;
            uniform ivec2 u_westProbeCount;
            uniform ivec2 u_southProbeCount;
            uniform int u_eastRayCount;
            uniform int u_northRayCount;
            uniform int u_westRayCount;
            uniform int u_southRayCount;
            uniform int u_baseResolveOffset;
            uniform float u_intensity;

            vec3 sampleQuadrant(sampler2D textureSampler, ivec2 probeCount, int rayCount, ivec2 localPixel) {
                int probeX = clamp(localPixel.x + u_baseResolveOffset, 0, probeCount.x - 1);
                int probeY = clamp(localPixel.y, 0, probeCount.y - 1);
                vec3 total = vec3(0.0);
                for (int ray = 0; ray < 64; ray++) {
                    if (ray >= rayCount) break;
                    total += texelFetch(textureSampler, ivec2(probeX * rayCount + ray, probeY), 0).rgb;
                }
                return total;
            }

            void main() {
                ivec2 pixel = ivec2(gl_GlobalInvocationID.xy);
                if (pixel.x >= u_sceneSize.x || pixel.y >= u_sceneSize.y) return;

                ivec2 eastLocal = ivec2(pixel.x, pixel.y);
                ivec2 northLocal = ivec2(pixel.y, u_sceneSize.x - 1 - pixel.x);
                ivec2 westLocal = ivec2(u_sceneSize.x - 1 - pixel.x, u_sceneSize.y - 1 - pixel.y);
                ivec2 southLocal = ivec2(u_sceneSize.y - 1 - pixel.y, pixel.x);

                vec3 lightSum = vec3(0.0);
                lightSum += sampleQuadrant(u_r0East, u_eastProbeCount, u_eastRayCount, eastLocal);
                lightSum += sampleQuadrant(u_r0North, u_northProbeCount, u_northRayCount, northLocal);
                lightSum += sampleQuadrant(u_r0West, u_westProbeCount, u_westRayCount, westLocal);
                lightSum += sampleQuadrant(u_r0South, u_southProbeCount, u_southRayCount, southLocal);

                imageStore(u_outputImage, pixel, vec4(lightSum * u_intensity, 1.0));
            }
            """;

    private static final String BLUR_COMPUTE = """
            #version 430 core
            layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

            layout(rgba16f, binding = 0) writeonly uniform image2D u_outputImage;
            uniform sampler2D u_inputTexture;
            uniform sampler2D u_sceneTexture;
            uniform ivec2 u_sceneSize;
            uniform float u_opacityThreshold;

            vec4 readLight(ivec2 pixel) {
                pixel = clamp(pixel, ivec2(0), u_sceneSize - ivec2(1));
                return texelFetch(u_inputTexture, pixel, 0);
            }

            float readOpacity(ivec2 pixel) {
                pixel = clamp(pixel, ivec2(0), u_sceneSize - ivec2(1));
                return texelFetch(u_sceneTexture, pixel, 0).a;
            }

            void main() {
                ivec2 pixel = ivec2(gl_GlobalInvocationID.xy);
                if (pixel.x >= u_sceneSize.x || pixel.y >= u_sceneSize.y) return;

                float centerOpacity = readOpacity(pixel);
                vec3 total = readLight(pixel).rgb * 4.0;
                float weight = 4.0;
                ivec2 offsets[4] = ivec2[4](ivec2(-1, 0), ivec2(1, 0), ivec2(0, -1), ivec2(0, 1));
                for (int i = 0; i < 4; i++) {
                    ivec2 neighbor = clamp(pixel + offsets[i], ivec2(0), u_sceneSize - ivec2(1));
                    float neighborOpacity = readOpacity(neighbor);
                    if (abs(neighborOpacity - centerOpacity) <= u_opacityThreshold) {
                        total += readLight(neighbor).rgb;
                        weight += 1.0;
                    }
                }
                imageStore(u_outputImage, pixel, vec4(total / max(weight, 1.0), 1.0));
            }
            """;

    private final RadianceCascadeSettings settings;
    private final List<RadianceCascadeLevel> levels = new ArrayList<>();
    private final List<QuadrantResources> quadrants = new ArrayList<>();
    private final ComputeShader traceInitShader;
    private final ComputeShader traceRecurShader;
    private final ComputeShader radianceBuildShader;
    private final ComputeShader resolveShader;
    private final ComputeShader blurShader;
    private RadianceRenderTarget resolvedLight;
    private RadianceRenderTarget blurredLight;
    private int width;
    private int height;

    public RadianceCascades(int width, int height) {
        this(width, height, new RadianceCascadeSettings());
    }

    public RadianceCascades(int width, int height, RadianceCascadeSettings settings) {
        if (width <= 0) throw new IllegalArgumentException("width must be > 0");
        if (height <= 0) throw new IllegalArgumentException("height must be > 0");
        if (settings == null) throw new NullPointerException("settings");
        validateSettings(settings);
        this.width = width;
        this.height = height;
        this.settings = settings;
        this.traceInitShader = new ComputeShader(TRACE_INIT_COMPUTE);
        this.traceRecurShader = new ComputeShader(TRACE_RECUR_COMPUTE);
        this.radianceBuildShader = new ComputeShader(RADIANCE_BUILD_COMPUTE);
        this.resolveShader = new ComputeShader(RESOLVE_COMPUTE);
        this.blurShader = new ComputeShader(BLUR_COMPUTE);
        rebuild();
    }

    private void rebuild() {
        for (QuadrantResources quadrant : quadrants) quadrant.dispose();
        quadrants.clear();
        levels.clear();
        if (resolvedLight != null) resolvedLight.dispose();
        if (blurredLight != null) blurredLight.dispose();
        resolvedLight = new RadianceRenderTarget(width, height, false, true);
        blurredLight = new RadianceRenderTarget(width, height, false, true);

        quadrants.add(new QuadrantResources(0, width, height));
        quadrants.add(new QuadrantResources(1, height, width));
        quadrants.add(new QuadrantResources(2, width, height));
        quadrants.add(new QuadrantResources(3, height, width));
        if (!quadrants.isEmpty()) levels.addAll(quadrants.get(0).levels);
    }


    private void validateSettings(RadianceCascadeSettings settings) {
        if (settings.getBaseProbeSpacing() <= 0) throw new IllegalArgumentException("baseProbeSpacing must be > 0");
        if (settings.getBaseRayCount() <= 0) throw new IllegalArgumentException("baseRayCount must be > 0");
        if (settings.getBaseRayCount() > 64) throw new IllegalArgumentException("baseRayCount must be <= 64 for the current resolve shader");
        if ((settings.getBaseRayCount() & (settings.getBaseRayCount() - 1)) != 0) throw new IllegalArgumentException("baseRayCount must be a power of two");
        if (settings.getBranchFactor() != 2) throw new IllegalArgumentException("This HRC implementation currently requires branchFactor == 2");
        if (settings.getBaseIntervalLength() <= 0f) throw new IllegalArgumentException("baseIntervalLength must be > 0");
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
        if (sceneBuffer.getWidth() != width || sceneBuffer.getHeight() != height) resize(sceneBuffer.getWidth(), sceneBuffer.getHeight());

        for (QuadrantResources quadrant : quadrants) {
            buildTraceLevels(sceneBuffer, quadrant);
            buildRadianceLevels(quadrant);
        }

        resolveQuadrants();

        if (settings.isCrossBlur()) {
            applyCrossBlur(sceneBuffer);
        }
    }

    private void buildTraceLevels(RadianceSceneBuffer sceneBuffer, QuadrantResources quadrant) {
        traceInitShader.bind();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sceneBuffer.getTextureID());
        traceInitShader.setUniform1i("u_sceneTexture", 0);
        traceInitShader.setUniform2i("u_sceneSize", width, height);
        traceInitShader.setUniform1i("u_quadrant", quadrant.index);
        traceInitShader.setUniform1f("u_rayStep", settings.getRayStep());
        traceInitShader.setUniform1f("u_cutoff", settings.getTransmittanceCutoff());

        int directLevels = Math.min(2, quadrant.maxLevel);
        for (int n = 0; n <= directLevels; n++) {
            RadianceCascadeLevel level = quadrant.levels.get(n);
            traceInitShader.setUniform2i("u_localSize", level.getProbeCountX(), level.getProbeCountY());
            traceInitShader.setUniform1i("u_probeSpacing", level.getProbeSpacing());
            traceInitShader.setUniform1i("u_traceCount", level.getTraceCount());
            glBindImageTexture(0, level.getTraceTextureID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA16F);
            traceInitShader.dispatch(groupCount(level.getProbeCountX() * level.getTraceCount()), groupCount(level.getProbeCountY()), 1);
            ComputeShader.memoryBarrierAll();
        }
        traceInitShader.unbind();

        traceRecurShader.bind();
        for (int n = 3; n <= quadrant.maxLevel; n++) {
            RadianceCascadeLevel level = quadrant.levels.get(n);
            RadianceCascadeLevel prev = quadrant.levels.get(n - 1);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, prev.getTraceTextureID());
            traceRecurShader.setUniform1i("u_prevTraceTexture", 0);
            traceRecurShader.setUniform2i("u_outputProbeCount", level.getProbeCountX(), level.getProbeCountY());
            traceRecurShader.setUniform2i("u_prevProbeCount", prev.getProbeCountX(), prev.getProbeCountY());
            traceRecurShader.setUniform1i("u_prevProbeSpacing", prev.getProbeSpacing());
            traceRecurShader.setUniform1i("u_prevTraceCount", prev.getTraceCount());
            traceRecurShader.setUniform1i("u_outputTraceCount", level.getTraceCount());
            glBindImageTexture(0, level.getTraceTextureID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA16F);
            traceRecurShader.dispatch(groupCount(level.getProbeCountX() * level.getTraceCount()), groupCount(level.getProbeCountY()), 1);
            ComputeShader.memoryBarrierAll();
        }
        traceRecurShader.unbind();
    }

    private void buildRadianceLevels(QuadrantResources quadrant) {
        radianceBuildShader.bind();
        for (int n = quadrant.maxLevel - 1; n >= 0; n--) {
            RadianceCascadeLevel level = quadrant.levels.get(n);
            RadianceCascadeLevel next = quadrant.levels.get(n + 1);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, level.getTraceTextureID());
            radianceBuildShader.setUniform1i("u_traceTexture", 0);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, next.getTraceTextureID());
            radianceBuildShader.setUniform1i("u_nextTraceTexture", 1);
            glActiveTexture(GL_TEXTURE2);
            if (n + 1 < quadrant.maxLevel) {
                glBindTexture(GL_TEXTURE_2D, next.getRadianceTextureID());
                radianceBuildShader.setUniform1i("u_hasNextRadiance", 1);
            } else {
                glBindTexture(GL_TEXTURE_2D, 0);
                radianceBuildShader.setUniform1i("u_hasNextRadiance", 0);
            }
            radianceBuildShader.setUniform1i("u_nextRadianceTexture", 2);
            radianceBuildShader.setUniform2i("u_outputProbeCount", level.getProbeCountX(), level.getProbeCountY());
            radianceBuildShader.setUniform2i("u_nextProbeCount", next.getProbeCountX(), next.getProbeCountY());
            radianceBuildShader.setUniform1i("u_levelSpacing", level.getProbeSpacing());
            radianceBuildShader.setUniform1i("u_outputRayCount", level.getRayCount());
            radianceBuildShader.setUniform1i("u_traceCount", level.getTraceCount());
            radianceBuildShader.setUniform1i("u_nextTraceCount", next.getTraceCount());
            radianceBuildShader.setUniform1i("u_nextRayCount", next.getRayCount());
            glBindImageTexture(0, level.getRadianceTextureID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA16F);
            radianceBuildShader.dispatch(groupCount(level.getProbeCountX() * level.getRayCount()), groupCount(level.getProbeCountY()), 1);
            ComputeShader.memoryBarrierAll();
        }
        radianceBuildShader.unbind();
    }

    private void resolveQuadrants() {
        QuadrantResources east = quadrants.get(0);
        QuadrantResources north = quadrants.get(1);
        QuadrantResources west = quadrants.get(2);
        QuadrantResources south = quadrants.get(3);

        resolveShader.bind();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, east.levels.get(0).getRadianceTextureID());
        resolveShader.setUniform1i("u_r0East", 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, north.levels.get(0).getRadianceTextureID());
        resolveShader.setUniform1i("u_r0North", 1);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, west.levels.get(0).getRadianceTextureID());
        resolveShader.setUniform1i("u_r0West", 2);
        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_2D, south.levels.get(0).getRadianceTextureID());
        resolveShader.setUniform1i("u_r0South", 3);
        resolveShader.setUniform2i("u_sceneSize", width, height);
        resolveShader.setUniform2i("u_eastProbeCount", east.levels.get(0).getProbeCountX(), east.levels.get(0).getProbeCountY());
        resolveShader.setUniform2i("u_northProbeCount", north.levels.get(0).getProbeCountX(), north.levels.get(0).getProbeCountY());
        resolveShader.setUniform2i("u_westProbeCount", west.levels.get(0).getProbeCountX(), west.levels.get(0).getProbeCountY());
        resolveShader.setUniform2i("u_southProbeCount", south.levels.get(0).getProbeCountX(), south.levels.get(0).getProbeCountY());
        resolveShader.setUniform1i("u_eastRayCount", east.levels.get(0).getRayCount());
        resolveShader.setUniform1i("u_northRayCount", north.levels.get(0).getRayCount());
        resolveShader.setUniform1i("u_westRayCount", west.levels.get(0).getRayCount());
        resolveShader.setUniform1i("u_southRayCount", south.levels.get(0).getRayCount());
        resolveShader.setUniform1i("u_baseResolveOffset", Math.max(1, Math.round(settings.getBaseIntervalLength() / Math.max(1f, settings.getBaseProbeSpacing()))));
        resolveShader.setUniform1f("u_intensity", settings.getIntensity());
        glBindImageTexture(0, resolvedLight.getTextureID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA16F);
        resolveShader.dispatch(groupCount(width), groupCount(height), 1);
        ComputeShader.memoryBarrierAll();
        resolveShader.unbind();
    }

    private void applyCrossBlur(RadianceSceneBuffer sceneBuffer) {
        blurShader.bind();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, resolvedLight.getTextureID());
        blurShader.setUniform1i("u_inputTexture", 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, sceneBuffer.getTextureID());
        blurShader.setUniform1i("u_sceneTexture", 1);
        blurShader.setUniform2i("u_sceneSize", width, height);
        blurShader.setUniform1f("u_opacityThreshold", settings.getOpacitySimilarityThreshold());
        glBindImageTexture(0, blurredLight.getTextureID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA16F);
        blurShader.dispatch(groupCount(width), groupCount(height), 1);
        ComputeShader.memoryBarrierAll();
        blurShader.unbind();
    }

    private int groupCount(int value) {
        return Math.max(1, (value + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE);
    }

    public Texture getLightTexture() {
        return settings.isCrossBlur() ? blurredLight.getTexture() : resolvedLight.getTexture();
    }

    public List<RadianceCascadeLevel> getLevels() {
        return Collections.unmodifiableList(levels);
    }

    public RadianceCascadeSettings getSettings() {
        return settings;
    }

    public void dispose() {
        for (QuadrantResources quadrant : quadrants) quadrant.dispose();
        quadrants.clear();
        levels.clear();
        if (resolvedLight != null) {
            resolvedLight.dispose();
            resolvedLight = null;
        }
        if (blurredLight != null) {
            blurredLight.dispose();
            blurredLight = null;
        }
        traceInitShader.dispose();
        traceRecurShader.dispose();
        radianceBuildShader.dispose();
        resolveShader.dispose();
        blurShader.dispose();
    }

    private final class QuadrantResources {
        private final int index;
        private final int localWidth;
        private final int localHeight;
        private final int maxLevel;
        private final List<RadianceCascadeLevel> levels = new ArrayList<>();

        private QuadrantResources(int index, int localWidth, int localHeight) {
            this.index = index;
            this.localWidth = localWidth;
            this.localHeight = localHeight;
            int computedMaxLevel = 0;
            float coveredDistance = settings.getBaseIntervalLength();
            while (coveredDistance < localWidth) {
                coveredDistance *= 2f;
                computedMaxLevel++;
            }
            if (settings.getMaxLevels() > 0) computedMaxLevel = Math.min(computedMaxLevel, settings.getMaxLevels() - 1);
            this.maxLevel = computedMaxLevel;
            for (int n = 0; n <= maxLevel; n++) {
                int spacing = settings.getBaseProbeSpacing() << n;
                int probeCountX = Math.max(1, (int) Math.ceil(localWidth / (double) spacing) + 2);
                int probeCountY = Math.max(1, localHeight);
                int rayCount = settings.getBaseRayCount() << n;
                int traceCount = rayCount + 1;
                levels.add(new RadianceCascadeLevel(n, spacing, rayCount, traceCount, probeCountX, probeCountY));
            }
        }

        private void dispose() {
            for (RadianceCascadeLevel level : levels) level.dispose();
            levels.clear();
        }
    }
}
