package valthorne.graphics.scene;

import valthorne.graphics.render.BillboardBatch3D;

/**
 * Controls the camera-derived basis used to expand a {@link BillboardSprite3D}
 * into a world-space quad. {@link BillboardBatch3D} applies the selected basis
 * to the sprite's width, height and anchor offsets around its position.
 * Orientation follows camera axes rather than a direction computed separately
 * from each sprite toward the camera's position.
 *
 * <p>The cylindrical mode preserves the engine's Z-up vertical axis. The
 * spherical mode follows both camera right and camera up, including camera roll.
 * Sprite bounds are computed conservatively for the selected orientation mode.</p>
 *
 * @author Albert Beaupre
 */
public enum BillboardMode3D {
    /**
     * Keeps the quad upright along world +Z and projects camera right onto the
     * XY plane. If that horizontal vector degenerates, the batch tries a vector
     * perpendicular to the camera's horizontal direction, then world +X.
     * This is the default mode for {@link BillboardSprite3D}.
     */
    CYLINDRICAL,
    /**
     * Uses normalized camera right and up vectors directly, allowing the quad
     * to tilt and roll with the view. Suitable for freely camera-facing particles.
     */
    SPHERICAL
}
