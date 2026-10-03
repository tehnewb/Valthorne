package valthorne.graphics.lighting2d;

/**
 * Registration tying one borrowed light to a reserved atlas row and owned polar
 * shadow cache. Revision markers control reconsideration when the light is visible.
 * Removal releases the row for a subsequent registration.
 *
 * @author Albert Beaupre
 */
final class Lighting2DEntry {
    final PointLight2D light; // Borrowed registered light.
    final int slot; // Reserved atlas row.
    final PolarShadow2D shadow; // Owned reusable polar-shadow cache.
    long geometry = Long.MIN_VALUE; // Last geometry revision considered.
    long lightRevision = Long.MIN_VALUE; // Last light-shadow revision considered.

    /**
     * Retains registration state with initially invalid revision markers so a visible
     * pass considers the light's shadow geometry.
     *
     * @param light  borrowed light
     * @param slot   reserved atlas row
     * @param shadow owned CPU shadow cache
     */
    Lighting2DEntry(PointLight2D light, int slot, PolarShadow2D shadow) {
        this.light = light;
        this.slot = slot;
        this.shadow = shadow;
    }
}
