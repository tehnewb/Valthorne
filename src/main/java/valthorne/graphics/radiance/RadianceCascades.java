package valthorne.graphics.radiance;

import valthorne.graphics.shader.ComputeShader;
import valthorne.graphics.shader.ShaderSources;
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
 *
 * <pre>{@code
 * RadianceSceneBuffer scene = new RadianceSceneBuffer(640, 360);
 * RadianceCascades lighting = new RadianceCascades(640, 360);
 * // Capture scene data into scene before solving.
 * lighting.render(scene);
 * Texture light = lighting.getLightTexture();
 * // Consume light before resizing or disposing lighting.
 * lighting.dispose();
 * scene.dispose();
 * }</pre>
 *
 * <p>Construction, solving, resizing, and disposal require a compute-capable OpenGL
 * context. The solver owns its programs and textures, borrows the captured scene,
 * and retains settings by reference. Configure hierarchy settings before creation:
 * changing them does not automatically rebuild targets. Rendering leaves texture
 * and image bindings changed and unbinds its compute program to program zero.</p>
 *
 * @author Albert Beaupre
 */
public final class RadianceCascades {

    /**
     * Square compute workgroup extent matching the shader local size.
     */
    private static final int WORKGROUP_SIZE = 8;
    /**
     * Factor used to derive automatic base interval length from probe spacing.
     */
    private static final float SQRT_TWO_OVER_TWO = 0.70710677f;

    /**
     * Source for tracing base-length intervals from captured scene data.
     */
    private static final String TRACE_COMPUTE = ShaderSources.load("radiance/trace.comp");

    /**
     * Source for extending intervals through alternating textures.
     */
    private static final String EXTEND_COMPUTE = ShaderSources.load("radiance/extend.comp");

    /**
     * Source for combining adjacent hierarchy levels.
     */
    private static final String MERGE_COMPUTE = ShaderSources.load("radiance/merge.comp");

    /**
     * Source for integrating base-level radiance into the output image.
     */
    private static final String RESOLVE_COMPUTE = ShaderSources.load("radiance/resolve.comp");

    private final RadianceCascadeSettings settings; // Shared configuration retained from construction.
    private final List<RadianceCascadeLevel> levels = new ArrayList<>(); // Owned levels ordered from finest to coarsest.
    private final ComputeShader traceShader; // Owned interval tracing program.
    private final ComputeShader extendShader; // Owned interval extension program.
    private final ComputeShader mergeShader; // Owned hierarchy merge program.
    private final ComputeShader resolveShader; // Owned final lighting resolve program.

    private RadianceRenderTarget resolvedLight; // Owned full-resolution lighting output.
    private int width; // Captured scene and output width in pixels.
    private int height; // Captured scene and output height in pixels.
    private int solveWidth; // Reduced internal solve width in pixels.
    private int solveHeight; // Reduced internal solve height in pixels.
    private float baseIntervalLength; // Resolved base interval length in internal solver units.

    /**
     * Creates a solver with default settings, compiling four compute programs and
     * allocating its hierarchy immediately on the current graphics context.
     *
     * @param width positive capture width in pixels
     * @param height positive capture height in pixels
     * @throws IllegalArgumentException if a dimension is nonpositive
     */
    public RadianceCascades(int width, int height) {
        this(width, height, new RadianceCascadeSettings());
    }

    /**
     * Validates Flatland settings, retains their reference, compiles compute programs,
     * and allocates hierarchy and output textures. Resource allocation occurs immediately;
     * configure structural settings before construction.
     *
     * @param width positive capture width in pixels
     * @param height positive capture height in pixels
     * @param settings configuration retained without copying
     * @throws NullPointerException if settings is null
     * @throws IllegalArgumentException if dimensions or checked settings are invalid
     * @throws IllegalStateException if shader compilation or hierarchy construction fails
     */
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

    /**
     * Disposes current targets and rebuilds reduced solve dimensions, base interval,
     * cascade levels, and full-resolution output using current settings. Old borrowed
     * wrappers become invalid; failures can leave partially reconstructed state.
     */
    private void rebuild() {
        disposeTargets();

        solveWidth = Math.max(1, (width + settings.getInternalScale() - 1) / settings.getInternalScale());
        solveHeight = Math.max(1, (height + settings.getInternalScale() - 1) / settings.getInternalScale());
        baseIntervalLength = resolveBaseIntervalLength();

        buildLevels();
        resolvedLight = new RadianceRenderTarget(width, height, false, true);
    }

    /**
     * Uses a positive explicit interval or derives one as base probe spacing times
     * sqrt(2)/2. Resolution does not modify the shared settings object.
     *
     * @return base interval length in solver units
     */
    private float resolveBaseIntervalLength() {
        float configured = settings.getBaseIntervalLength();
        if (configured > 0f) {
            return configured;
        }
        return settings.getBaseProbeSpacing() * SQRT_TWO_OVER_TWO;
    }

    /**
     * Allocates progressively coarser levels with doubled spacing and ray count and
     * contiguous growing intervals. Stops at configured count, covered padded scene
     * distance, or texture cap. Exceeding the cap at the base level is an error.
     *
     * @throws IllegalStateException if base geometry exceeds the cap or a parameter overflows
     */
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

    /**
     * Computes a coverage distance from the diagonal of the solve rectangle enlarged
     * by one probe spacing in each dimension.
     *
     * @param spacing current level's probe spacing
     * @return padded diagonal in solver units
     */
    private float maxSceneDistanceForSpacing(int spacing) {
        float paddedWidth = solveWidth + spacing;
        float paddedHeight = solveHeight + spacing;
        return (float) Math.hypot(paddedWidth, paddedHeight);
    }

    /**
     * Shifts a hierarchy parameter using a long intermediate and rejects results above
     * the positive integer range. Intended for small nonnegative level indices; this
     * helper does not validate all possible signed inputs or Java shift-distance wrapping.
     *
     * @param value positive base parameter
     * @param shift nonnegative level index
     * @return shifted parameter as an integer
     * @throws IllegalArgumentException if shift is negative
     * @throws IllegalStateException if the intermediate exceeds Integer.MAX_VALUE
     */
    private int checkedShift(int value, int shift) {
        if (shift < 0) throw new IllegalArgumentException("shift must be >= 0");
        long result = (long) value << shift;
        if (result > Integer.MAX_VALUE) {
            throw new IllegalStateException("Radiance cascade parameter overflowed 32-bit integer range");
        }
        return (int) result;
    }

    /**
     * Disposes all level textures and the resolved output, clearing retained references.
     * Compute programs and configuration remain available for a subsequent rebuild.
     */
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

    /**
     * Rebuilds textures for changed positive capture dimensions, discarding previous
     * lighting and invalidating borrowed wrappers. Equal dimensions are a no-op, so
     * this method does not refresh changed structural settings at the same size.
     *
     * @param width positive capture width in pixels
     * @param height positive capture height in pixels
     * @throws IllegalArgumentException if either dimension is nonpositive
     */
    public void resize(int width, int height) {
        if (width <= 0) throw new IllegalArgumentException("width must be > 0");
        if (height <= 0) throw new IllegalArgumentException("height must be > 0");
        if (this.width == width && this.height == height) return;

        this.width = width;
        this.height = height;
        rebuild();
    }

    /**
     * Solves captured scene lighting through tracing, extension, back-to-front merging,
     * and final resolve. Resizes targets if capture dimensions differ. GPU barriers
     * separate dependent passes; texture and image bindings are not restored afterward.
     * The scene buffer remains owned by its caller.
     *
     * @param sceneBuffer populated capture buffer, not currently being written
     * @throws NullPointerException if sceneBuffer is null
     */
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

    /**
     * Writes fresh base-length intervals for every level, resetting alternating targets
     * and applying the current surface-hit threshold. Issues a memory barrier after each
     * dispatch and unbinds the compute program when finished.
     *
     * @param sceneBuffer captured scene texture sampled on texture unit zero
     */
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

    /**
     * Extends each level's initial interval once per level index, doubling the shift
     * distance each pass. Reads the current interval, writes the alternate, issues a
     * barrier, and swaps their logical roles. Uses current interpolation and cutoff settings.
     */
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

    /**
     * Combines levels from coarsest to finest so each merged image includes its farther
     * neighbor. The terminal level uses no next image. Each dispatch is followed by a
     * barrier before a finer level samples the result.
     */
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

    /**
     * Integrates the finest merged level into the full-resolution output with current
     * intensity and surface threshold. Samples scene data and base radiance, writes
     * the output image, and issues a barrier before returning.
     *
     * @param sceneBuffer captured scene used to distinguish surfaces during resolve
     */
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

    /**
     * Rounds a positive image extent up to eight-wide workgroups, with a minimum of
     * one group. The caller supplies image dimensions small enough to avoid addition overflow.
     *
     * @param value texture extent in pixels
     * @return dispatch group count for one axis
     */
    private int groupCount(int value) {
        return Math.max(1, (value + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE);
    }

    /**
     * Returns the borrowed resolved-light wrapper. Its contents are produced by render;
     * resize invalidates earlier wrappers. Do not dispose this texture separately.
     *
     * @return current lighting texture
     * @throws NullPointerException if targets have been disposed
     */
    public Texture getLightTexture() {
        return resolvedLight.getTexture();
    }

    /**
     * Returns an unmodifiable live view of the owned level list. Rebuilding changes
     * its contents and invalidates texture resources in previously retained levels.
     *
     * @return live level view ordered finest to coarsest
     */
    public List<RadianceCascadeLevel> getLevels() {
        return Collections.unmodifiableList(levels);
    }

    /**
     * Returns the retained mutable settings reference. Structural changes are not
     * automatically reflected in existing targets; configure them before solver creation.
     *
     * @return shared configuration
     */
    public RadianceCascadeSettings getSettings() {
        return settings;
    }

    /**
     * Returns the reduced internal width chosen at the latest target rebuild.
     *
     * @return internal solve width in pixels
     */
    public int getSolveWidth() {
        return solveWidth;
    }

    /**
     * Returns the reduced internal height chosen at the latest target rebuild.
     *
     * @return internal solve height in pixels
     */
    public int getSolveHeight() {
        return solveHeight;
    }

    /**
     * Releases owned targets and all four compute programs on the current context.
     * Borrowed output wrappers become invalid. The solver has no closed-state guard
     * and must not be rendered after disposal.
     */
    public void dispose() {
        disposeTargets();
        traceShader.dispose();
        extendShader.dispose();
        mergeShader.dispose();
        resolveShader.dispose();
    }
}
