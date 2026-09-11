package valthorne.camera;

import org.joml.Vector3f;

/**
 * Maintains a Z-up editor camera's orbit center, angular pose, and viewing distance.
 * Input integration is left to the caller: drag deltas rotate or pan, wheel deltas
 * adjust distance, and {@link #apply(PerspectiveCamera)} copies the pose to a camera.
 * Operations reuse stored vectors and do not allocate per-frame controller state.
 *
 * <pre>{@code
 * OrbitCameraController controller = new OrbitCameraController();
 * PerspectiveCamera camera = new PerspectiveCamera();
 * controller.orbit(20, -5);
 * controller.apply(camera);
 * camera.rebuild(1280, 720);
 * }</pre>
 *
 * <p>Applying a pose does not rebuild camera matrices. Use finite input deltas and
 * access the controller on the same thread as camera updates; it is unsynchronized.</p>
 *
 * @author Albert Beaupre
 */
public final class OrbitCameraController {
    private final Vector3f target = new Vector3f(0, .5f, 1.5f); // Mutable world-space orbit center shared by the accessor.
    private float azimuth = -1.45f, elevation = .33f, distance = 12; // Angles in radians and distance from the orbit center in world units.

    /**
     * Returns the live orbit center. Mutations change subsequent pan and apply
     * operations; copy the vector when an independent snapshot is required.
     *
     * @return mutable world-space target owned by this controller
     */
    public Vector3f getTarget() {return target;}

    /**
     * Returns the current distance from the target, initially twelve world units.
     * Zoom operations constrain this value to the inclusive range one to one hundred.
     *
     * @return camera-to-target distance in world units
     */
    public float getDistance() {return distance;}

    /**
     * Restores target {@code (0, 0.5, 1.5)}, azimuth -1.45 radians, elevation 0.33
     * radians, and distance twelve. An already configured camera is unaffected until
     * the next apply call.
     */
    public void reset() {
        target.set(0, .5f, 1.5f);
        azimuth = -1.45f;
        elevation = .33f;
        distance = 12;
    }

    /**
     * Subtracts drag deltas scaled by 0.006 radians from the orbit angles. Elevation
     * is clamped to plus or minus 1.45 radians; azimuth is not wrapped or clamped.
     *
     * @param dx horizontal drag delta, typically pixels
     * @param dy vertical drag delta, typically pixels
     */
    public void orbit(float dx, float dy) {
        azimuth -= dx * .006f;
        elevation = Math.max(-1.45f, Math.min(1.45f, elevation - dy * .006f));
    }

    /**
     * Multiplies distance by {@code exp(-wheel * 0.12)} and clamps the result to
     * one through one hundred world units. Positive wheel deltas move closer to
     * the target; zero preserves the current distance.
     *
     * @param wheel signed scroll delta
     */
    public void zoom(float wheel) {distance = Math.max(1, Math.min(100, distance * (float) Math.exp(-wheel * .12f)));}

    /**
     * Translates the target horizontally in the azimuth-aligned XY plane and
     * vertically along world Z. Sensitivity is {@code distance * 0.8} divided by
     * viewport height, with heights below one treated as one. This is a navigation
     * sensitivity heuristic rather than an exact projection-based screen conversion.
     *
     * @param dx             horizontal drag delta in the same units as viewport height
     * @param dy             vertical drag delta; positive values raise the target
     * @param viewportHeight viewport height, typically pixels
     */
    public void pan(float dx, float dy, float viewportHeight) {
        float units = distance * .8f / Math.max(1, viewportHeight), sin = (float) Math.sin(azimuth), cos = (float) Math.cos(azimuth);
        target.add(-dx * units * (-sin), -dx * units * cos, dy * units);
    }

    /**
     * Places a camera on the orbit sphere and aims it at the target using positive
     * Z as world up. Projection settings are preserved and matrices remain unchanged
     * until the camera is rebuilt.
     *
     * @param camera camera whose position and orientation will be replaced
     * @throws NullPointerException if camera is null
     */
    public void apply(PerspectiveCamera camera) {
        float horizontal = distance * (float) Math.cos(elevation);
        camera.setPosition(target.x() + horizontal * (float) Math.cos(azimuth), target.y() + horizontal * (float) Math.sin(azimuth), target.z() + distance * (float) Math.sin(elevation));
        camera.lookAt(target.x(), target.y(), target.z(), 0, 0, 1);
    }
}
