package valthorne.graphics.lighting2d;

import valthorne.camera.OrthographicCamera3D;
import valthorne.graphics.Color;
import valthorne.graphics.model.*;

/**
 * Physically lit orthographic 2D scenes using the same GPU path tracer as 3D.
 * Surfaces lie on XY; walls have real height so they cast soft shadows and bounce light.
 * Use consistent world units (e.g. one unit per tile). This is an alternative to
 * Lighting2D's fast color-capture pipeline, not a filter for already flattened sprites.
 * Materials may borrow sprite textures; alpha cutouts are supported.
 * The facade owns its tracer and retains a mutable scene and orthographic camera.
 * Closing releases tracer resources; caller-supplied material textures remain borrowed.
 *
 * <pre>{@code
 * PathTracer2D lighting = new PathTracer2D();
 * lighting.setView(0, 0, 12, 100);
 * lighting.addSurface(-5, -5, 10, 10, 0, new Material3D());
 * lighting.addAreaLight(0, 0, 3, 0.4f, Color.WHITE, 8);
 * lighting.render();
 * // Close on the graphics thread when finished.
 * lighting.close();
 * }</pre>
 *
 * @author Albert Beaupre
 */
public final class PathTracer2D implements AutoCloseable {
    private final Scene3D scene = new Scene3D(); // Retained scene containing generated surface and light instances.
    private final OrthographicCamera3D camera = new OrthographicCamera3D(); // Owned top-down camera exposed for customization.
    private final PathTracer3D tracer = new PathTracer3D(); // Owned GPU tracing implementation.

    /**
     * Creates an empty scene with view center (0,0), world height twelve, and camera
     * elevation one hundred, using the underlying tracer's normal resource lifecycle.
     */
    public PathTracer2D() {setView(0, 0, 12, 100);}

    /**
     * Places the camera above XY facing negative Z with positive Y up. Sets world height
     * and clip planes to 0.01 and twice camera height; aspect ratio later determines
     * visible width. Camera elevation should exceed scene geometry. Does not rebuild
     * matrices itself or reset a caller-customized orthographic zoom.
     *
     * @param centerX world-space horizontal center
     * @param centerY world-space vertical center
     * @param worldHeight positive unzoomed visible height in world units
     * @param cameraHeight positive elevation above XY
     * @return this facade
     * @throws IllegalArgumentException if the combined input check or clip-plane constraints fail
     */
    public PathTracer2D setView(float centerX, float centerY, float worldHeight, float cameraHeight) {
        if (!Float.isFinite(centerX + centerY + worldHeight + cameraHeight) || worldHeight <= 0 || cameraHeight <= 0)
            throw new IllegalArgumentException("Invalid view");
        camera.setPosition(centerX, centerY, cameraHeight);
        camera.lookAt(centerX, centerY, 0, 0, 1, 0);
        camera.setWorldHeight(worldHeight);
        camera.setClipPlanes(.01f, cameraHeight * 2);
        return this;
    }

    /**
     * Creates a white XY plane anchored at its lower-left corner and adds it to the
     * scene at the requested elevation. Material and any texture remain shared.
     *
     * @param x lower-left world X
     * @param y lower-left world Y
     * @param width finite positive X extent
     * @param height finite positive Y extent
     * @param elevation world Z coordinate
     * @param material non-null shared surface material
     * @return mutable placed instance retained by the scene
     */
    public ModelInstance3D addSurface(float x, float y, float width, float height, float elevation, Material3D material) {
        return add(ModelBuilder3D.plane(width, height), x + width * .5f, y + height * .5f, elevation, material);
    }

    /**
     * Creates a box from Z zero to the supplied height, anchored at its lower-left XY
     * corner. Adds it to the scene for surface shading, occlusion, and light transport.
     *
     * @param x lower-left world X
     * @param y lower-left world Y
     * @param width finite positive X extent
     * @param depth finite positive Y extent
     * @param height finite positive Z extent
     * @param material non-null shared wall material
     * @return mutable wall instance retained by the scene
     */
    public ModelInstance3D addWall(float x, float y, float width, float depth, float height, Material3D material) {
        return add(ModelBuilder3D.box(width, depth, height), x + width * .5f, y + depth * .5f, height * .5f, material);
    }

    /**
     * Adds a 16-segment, eight-stack emissive sphere centered at the supplied position.
     * Its finite geometry participates in shadows and reflections; this creates an
     * emissive material rather than a compatibility point-light entry.
     *
     * @param x world X center
     * @param y world Y center
     * @param elevation world Z center
     * @param radius finite positive sphere radius
     * @param color emission color copied by the material
     * @param radiance emission-strength setting
     * @return mutable emissive instance retained by the scene
     */
    public ModelInstance3D addAreaLight(float x, float y, float elevation, float radius, Color color, float radiance) {
        return add(ModelBuilder3D.sphere(radius, 16, 8), x, y, elevation, new Material3D().setEmissive(color).setEmissionStrength(radiance));
    }

    /**
     * Creates a placed instance sharing model and material, then appends it to the scene.
     * No GPU upload is performed by this helper.
     *
     * @param model geometry to reference
     * @param x world X translation
     * @param y world Y translation
     * @param z world Z translation
     * @param material non-null material to share
     * @return appended instance
     */
    private ModelInstance3D add(Model3D model, float x, float y, float z, Material3D material) {
        ModelInstance3D instance = new ModelInstance3D().setModel(model).setPosition(x, y, z).setMaterial(material);
        scene.add(instance);
        return instance;
    }

    /**
     * Returns the live scene for adding, removing, or updating geometry. Callers must
     * coordinate changes with rendering on the owning thread.
     *
     * @return retained scene
     */
    public Scene3D getScene() {return scene;}

    /**
     * Returns the owned tracer for quality and output configuration. Do not dispose it
     * independently while this facade is still used.
     *
     * @return underlying tracer
     */
    public PathTracer3D getTracer() {return tracer;}

    /**
     * Returns the live orthographic camera for additional pose or projection settings.
     * Direct changes affect subsequent rendering according to the tracer's camera path.
     *
     * @return owned camera
     */
    public OrthographicCamera3D getCamera() {return camera;}

    /**
     * Delegates the current scene and camera to the GPU path tracer. Requires the
     * appropriate current graphics context and follows the tracer's viewport, accumulation,
     * and output behavior; this facade adds no separate compositing pass.
     */
    public void render() {tracer.render(scene, camera);}

    /**
     * Releases the underlying tracer's resources on the graphics thread. Scene geometry
     * and caller-owned textures are not disposed by this facade.
     */
    @Override
    public void close() {tracer.close();}
}
