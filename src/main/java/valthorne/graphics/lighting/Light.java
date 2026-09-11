package valthorne.graphics.lighting;

import valthorne.graphics.Color;

import java.util.Collections;
import java.util.List;

/**
 * Shared mutable configuration and ray results for two-dimensional lights.
 * Derived classes choose endpoint directions and rebuild active, dirty lights.
 * The handler is borrowed and construction does not register the light with it.
 * Position, range, ray count, occlusion mask, and x-ray changes dirty geometry;
 * color and soft-fringe settings affect drawing without recasting endpoints.
 *
 * <p>Colors are copied on input but exposed directly on access, as are endpoint
 * arrays. Callers must coordinate mutations with update and render operations on
 * the owning thread. This base class allocates no GPU resources.</p>
 *
 * @author Albert Beaupre
 */
public abstract class Light {

    /**
     * Membership mask with all category bits enabled.
     */
    public static final int ALL_MASK_BITS = ~0;

    protected final RayHandler rayHandler; // Borrowed world and render integration handler.
    protected final Color color; // Owned mutable light color.
    private final float[] scratchRayEnd = new float[2]; // Reusable unoccluded endpoint XY.
    private final RayCastHit scratchHit = new RayCastHit(); // Reusable hit output.
    protected int rays; // Configured base ray count.
    protected float distance; // Radial extent in world units.
    protected float x; // World center X.
    protected float y; // World center Y.
    protected boolean active = true; // Whether the light participates in updates and rendering.
    protected boolean xray; // Whether world occlusion is bypassed.
    protected boolean soft = true; // Whether soft-fringe rendering is enabled.
    protected float softnessLength = 16f; // Soft-fringe extent in world units.
    protected float[] endX; // Current endpoint X values.
    protected float[] endY; // Current endpoint Y values.
    protected float[] fractions; // Current segment fractions, one for unoccluded rays.
    protected int categoryBits = ALL_MASK_BITS; // Application light category mask.
    protected int occlusionMaskBits = ALL_MASK_BITS; // Accepted occluder category mask.
    protected boolean dirty = true; // Whether endpoints need rebuilding.

    /**
     * Retains the handler, copies color, and allocates ray arrays. Starts active and
     * dirty with soft shadows enabled. Position and distance are stored unchecked.
     *
     * @param rayHandler associated handler
     * @param rays base ray count, at least three
     * @param color color to copy
     * @param distance radial extent
     * @param x world center X
     * @param y world center Y
     * @throws NullPointerException if handler or color is null
     * @throws IllegalArgumentException if rays is below three
     */
    protected Light(RayHandler rayHandler, int rays, Color color, float distance, float x, float y) {
        if (rayHandler == null) throw new NullPointerException("rayHandler cannot be null");
        if (color == null) throw new NullPointerException("color cannot be null");
        if (rays < 3) throw new IllegalArgumentException("rays must be >= 3");

        this.rayHandler = rayHandler;
        this.rays = rays;
        this.color = color.copy();
        this.distance = distance;
        this.x = x;
        this.y = y;

        ensureRayCapacity(rays);
    }

    /**
     * Refreshes endpoint geometry according to the derived light's direction pattern
     * and active/dirty policy. Does not itself submit GPU drawing.
     */
    public abstract void update();

    /**
     * Writes one unoccluded endpoint into reusable XY output for the base rebuild.
     *
     * @param index base ray index
     * @param output destination with at least two elements
     */
    protected abstract void computeRayEnd(int index, float[] output);

    /**
     * Recasts base rays and sorts endpoints by polar angle, clearing dirty state after
     * successful completion.
     */
    protected void rebuild() {
        rebuild(true);
    }

    /**
     * Computes and casts each base ray into existing storage, optionally sorts results,
     * then clears dirty state. The caller ensures sufficient endpoint-array length.
     *
     * @param sortEndpoints whether to order endpoints by polar angle
     */
    protected final void rebuild(boolean sortEndpoints) {
        for (int i = 0; i < rays; i++) {
            computeRayEnd(i, scratchRayEnd);

            float targetX = scratchRayEnd[0];
            float targetY = scratchRayEnd[1];
            applyRayResult(i, targetX, targetY);
        }

        if (sortEndpoints) {
            sortEndpointsByAngle();
        }
        dirty = false;
    }

    /**
     * Orders endpoint arrays by atan2 around the center while keeping hit fractions
     * paired with coordinates. Allocates temporary angle, index, and sorted arrays.
     */
    protected void sortEndpointsByAngle() {
        int count = endX.length;
        if (count < 2) return;

        float[] angles = new float[count];
        int[] indices = new int[count];

        for (int i = 0; i < count; i++) {
            angles[i] = (float) Math.atan2(endY[i] - y, endX[i] - x);
            indices[i] = i;
        }

        quickSort(indices, angles, 0, count - 1);

        float[] sortedEndX = new float[count];
        float[] sortedEndY = new float[count];
        float[] sortedFractions = new float[count];

        for (int i = 0; i < count; i++) {
            int index = indices[i];
            sortedEndX[i] = endX[index];
            sortedEndY[i] = endY[index];
            sortedFractions[i] = fractions[index];
        }

        System.arraycopy(sortedEndX, 0, endX, 0, count);
        System.arraycopy(sortedEndY, 0, endY, 0, count);
        System.arraycopy(sortedFractions, 0, fractions, 0, count);
    }

    /**
     * Sorts a permutation by referenced angles over an inclusive range. Recurses into
     * the smaller partition and iterates over the larger to limit stack growth.
     *
     * @param indices permutation to reorder
     * @param angles angle lookup array
     * @param low inclusive first position
     * @param high inclusive last position
     */
    private void quickSort(int[] indices, float[] angles, int low, int high) {
        while (low < high) {
            int pivotIndex = partition(indices, angles, low, high);

            if (pivotIndex - low < high - pivotIndex) {
                quickSort(indices, angles, low, pivotIndex - 1);
                low = pivotIndex + 1;
            } else {
                quickSort(indices, angles, pivotIndex + 1, high);
                high = pivotIndex - 1;
            }
        }
    }

    /**
     * Partitions indices around the last entry's angle using less-than-or-equal ordering.
     * Angle values remain unchanged.
     *
     * @param indices permutation to mutate
     * @param angles angle lookup array
     * @param low inclusive range start
     * @param high inclusive range end and pivot source
     * @return pivot's final index
     */
    private int partition(int[] indices, float[] angles, int low, int high) {
        float pivot = angles[indices[high]];
        int i = low - 1;

        for (int j = low; j < high; j++) {
            if (angles[indices[j]] <= pivot) {
                i++;
                int temp = indices[i];
                indices[i] = indices[j];
                indices[j] = temp;
            }
        }

        int temp = indices[i + 1];
        indices[i + 1] = indices[high];
        indices[high] = temp;

        return i + 1;
    }

    /**
     * Stores a hit endpoint and fraction or the unobstructed endpoint with fraction one.
     * Reuses the light's scratch hit object.
     *
     * @param index destination ray slot
     * @param targetX unoccluded X
     * @param targetY unoccluded Y
     */
    protected final void applyRayResult(int index, float targetX, float targetY) {
        if (rayCast(targetX, targetY, scratchHit)) {
            endX[index] = scratchHit.getX();
            endY[index] = scratchHit.getY();
            fractions[index] = scratchHit.getFraction();
            return;
        }

        endX[index] = targetX;
        endY[index] = targetY;
        fractions[index] = 1f;
    }

    /**
     * Casts from the center using this light's filtering context. X-ray mode or an absent
     * world yields false and clears non-null output.
     *
     * @param targetX endpoint X
     * @param targetY endpoint Y
     * @param outHit optional reusable result
     * @return whether a hit occurred
     */
    protected final boolean rayCast(float targetX, float targetY, RayCastHit outHit) {
        RayCastWorld world = rayHandler.getRayCastWorld();
        if (xray || world == null) {
            if (outHit != null) {
                outHit.clear();
            }
            return false;
        }
        return world.rayCast(this, x, y, targetX, targetY, outHit);
    }

    /**
     * Queries the associated world, or returns null when occlusion is bypassed. The
     * world may reuse its result object, so it is not necessarily a durable snapshot.
     *
     * @param targetX endpoint X
     * @param targetY endpoint Y
     * @return world result, or null when bypassed
     */
    protected final RayCastHit rayCast(float targetX, float targetY) {
        RayCastWorld world = rayHandler.getRayCastWorld();
        if (xray || world == null) {
            return null;
        }
        return world.rayCast(this, x, y, targetX, targetY);
    }

    /**
     * Returns world-provided occluders without copying or additional filtering. An absent
     * world supplies an empty immutable list.
     *
     * @return available world occluders
     */
    protected final List<LightOccluder> getLightOccluders() {
        RayCastWorld world = rayHandler.getRayCastWorld();
        return (world == null) ? Collections.emptyList() : world.getLightOccluders();
    }

    /**
     * Returns the handler retained at construction without changing light registration.
     *
     * @return borrowed associated handler
     */
    public RayHandler getRayHandler() {
        return rayHandler;
    }

    /**
     * Returns the live owned color. Direct changes affect rendering without recasting rays.
     *
     * @return mutable light color
     */
    public Color getColor() {
        return color;
    }

    /**
     * Copies replacement color without changing geometric dirty state.
     *
     * @param color color to copy
     * @throws NullPointerException if color is null
     */
    public void setColor(Color color) {
        if (color == null) throw new NullPointerException("color cannot be null");
        this.color.set(color);
    }

    /**
     * Replaces RGBA components without changing geometric dirty state.
     *
     * @param r red component
     * @param g green component
     * @param b blue component
     * @param a alpha component
     */
    public void setColor(float r, float g, float b, float a) {
        this.color.set(r, g, b, a);
    }

    /**
     * Returns the stored base ray count without updating endpoints.
     *
     * @return base ray count
     */
    public int getRays() {
        return rays;
    }

    /**
     * Changes base ray count, replaces endpoint storage, and dirties geometry. An equal
     * count does nothing; vertex-cast lights can expand storage during their update.
     *
     * @param rays base count, at least three
     * @throws IllegalArgumentException if rays is below three
     */
    public void setRays(int rays) {
        if (rays < 3) throw new IllegalArgumentException("rays must be >= 3");
        if (this.rays == rays) return;

        this.rays = rays;
        ensureRayCapacity(rays);
        dirty = true;
    }

    /**
     * Returns the stored radial extent without updating endpoints.
     *
     * @return radial extent
     */
    public float getDistance() {
        return distance;
    }

    /**
     * Stores the radial extent without range validation.
     * Marks endpoints dirty for a later update.
     *
     * @param distance replacement radial extent
     */
    public void setDistance(float distance) {
        this.distance = distance;
        dirty = true;
    }

    /**
     * Returns the stored world center X without updating endpoints.
     *
     * @return world center X
     */
    public float getX() {
        return x;
    }

    /**
     * Stores the world center X without range validation.
     * Marks endpoints dirty for a later update.
     *
     * @param x replacement world center X
     */
    public void setX(float x) {
        this.x = x;
        dirty = true;
    }

    /**
     * Returns the stored world center Y without updating endpoints.
     *
     * @return world center Y
     */
    public float getY() {
        return y;
    }

    /**
     * Stores the world center Y without range validation.
     * Marks endpoints dirty for a later update.
     *
     * @param y replacement world center Y
     */
    public void setY(float y) {
        this.y = y;
        dirty = true;
    }

    /**
     * Replaces both world coordinates and marks endpoints dirty for the next update.
     *
     * @param x world center X
     * @param y world center Y
     */
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        dirty = true;
    }

    /**
     * Returns the stored light membership mask without updating endpoints.
     *
     * @return light membership mask
     */
    public int getCategoryBits() {
        return categoryBits;
    }

    /**
     * Stores the light membership mask without range validation.
     * Leaves geometric dirty state unchanged.
     *
     * @param categoryBits replacement light membership mask
     */
    public void setCategoryBits(int categoryBits) {
        this.categoryBits = categoryBits;
    }

    /**
     * Returns the stored accepted occluder mask without updating endpoints.
     *
     * @return accepted occluder mask
     */
    public int getOcclusionMaskBits() {
        return occlusionMaskBits;
    }

    /**
     * Stores the accepted occluder mask and dirties geometry if its value changed.
     *
     * @param occlusionMaskBits replacement accepted-category mask
     */
    public void setOcclusionMaskBits(int occlusionMaskBits) {
        if (this.occlusionMaskBits == occlusionMaskBits) return;
        this.occlusionMaskBits = occlusionMaskBits;
        dirty = true;
    }

    /**
     * Returns active state without recomputing geometry.
     *
     * @return current active flag
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Changes active state.
     * Does not independently dirty or rebuild endpoints.
     *
     * @param active replacement flag
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Returns occlusion bypass without recomputing geometry.
     *
     * @return current xray flag
     */
    public boolean isXray() {
        return xray;
    }

    /**
     * Changes occlusion bypass.
     * Marks endpoints dirty so a later update applies the changed policy.
     *
     * @param xray replacement flag
     */
    public void setXray(boolean xray) {
        this.xray = xray;
        dirty = true;
    }

    /**
     * Returns soft-fringe enablement without recomputing geometry.
     *
     * @return current soft flag
     */
    public boolean isSoft() {
        return soft;
    }

    /**
     * Changes soft-fringe enablement.
     * Does not independently dirty or rebuild endpoints.
     *
     * @param soft replacement flag
     */
    public void setSoft(boolean soft) {
        this.soft = soft;
    }

    /**
     * Returns the stored soft-fringe length without updating endpoints.
     *
     * @return soft-fringe length
     */
    public float getSoftnessLength() {
        return softnessLength;
    }

    /**
     * Stores the soft-fringe length without range validation.
     * Leaves geometric dirty state unchanged.
     *
     * @param softnessLength replacement soft-fringe length
     */
    public void setSoftnessLength(float softnessLength) {
        this.softnessLength = softnessLength;
    }

    /**
     * Returns live endpoint X coordinates. Capacity changes replace this array; copy values
     * when retaining a snapshot outside the update/render cycle.
     *
     * @return current endpoint X coordinates
     */
    public float[] getEndX() {
        return endX;
    }

    /**
     * Returns live endpoint Y coordinates. Capacity changes replace this array; copy values
     * when retaining a snapshot outside the update/render cycle.
     *
     * @return current endpoint Y coordinates
     */
    public float[] getEndY() {
        return endY;
    }

    /**
     * Returns live hit fractions. Capacity changes replace this array; copy values
     * when retaining a snapshot outside the update/render cycle.
     *
     * @return current hit fractions
     */
    public float[] getFractions() {
        return fractions;
    }

    /**
     * Replaces all three result arrays if their exact length differs from count. New
     * coordinates start at zero and fractions at one; previous results are discarded.
     *
     * @param count required endpoint-array length
     */
    protected final void ensureRayCapacity(int count) {
        if (endX != null && endX.length == count) {
            return;
        }

        endX = new float[count];
        endY = new float[count];
        fractions = new float[count];
        for (int i = 0; i < count; i++) {
            fractions[i] = 1f;
        }
    }

    /**
     * Returns pending rebuild state without recomputing geometry.
     *
     * @return current dirty flag
     */
    public boolean isDirty() {
        return dirty;
    }
}
