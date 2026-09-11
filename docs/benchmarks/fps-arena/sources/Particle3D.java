package valthorne.graphics.particle;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import valthorne.graphics.model.BillboardMode3D;
import valthorne.graphics.model.BillboardSprite3D;
import valthorne.graphics.model.Material3D;
import valthorne.graphics.model.Model3D;
import valthorne.graphics.model.ModelInstance3D;
import valthorne.graphics.model.RenderPass3D;
import valthorne.graphics.model.Scene3D;
import valthorne.graphics.model.PointLight3D;
import valthorne.math.physics.RigidBody3D;

/**
 * Pooled 3D particle with a centered spherical billboard and an optional mesh.
 * Position, velocity and acceleration use world units and seconds. Models,
 * materials and textures are borrowed; the emitter owns an optional Jolt body.
 *
 * <p>Configure position through {@link #getPosition()}, including mesh particles.
 * The emitter copies that position to the mesh after initialization and updates.
 * With physics enabled, Jolt controls position, velocity and mesh rotation after
 * creation: use {@link #getBody()} to teleport, apply impulses or change velocity.
 * Acceleration remains editable and adds force on top of native world gravity.</p>
 *
 * <p>Live objects may be reset and reused after expiration. Never use a retained
 * particle reference as a permanent identity in delayed contact callbacks.</p>
 *
 * @author Albert Beaupre
 */
public final class Particle3D {
    /**
     * Shared identity transform copied into particle meshes when resetting parent state.
     */
    private static final Matrix4f IDENTITY = new Matrix4f();
    private final BillboardSprite3D sprite = new BillboardSprite3D()
            .setMode(BillboardMode3D.SPHERICAL).setAnchor(.5f, .5f); // Owned billboard and canonical position storage.
    private final Material3D defaultSpriteMaterial = sprite.getMaterial(); // Owned original billboard material restored on reuse.
    private final Vector3f velocity = new Vector3f(); // Live world velocity in units per second.
    private final Vector3f acceleration = new Vector3f(); // Live world acceleration in units per second squared.
    private ModelInstance3D model; // Lazily allocated mesh instance borrowing its geometry.
    private Material3D defaultModelMaterial; // Owned original mesh material, retained for pool reset.
    private Quaternionf rotation; // Lazy scratch quaternion for body-to-mesh synchronization.
    private RigidBody3D body; // Emitter-owned physical body, null for cosmetic or released particles.
    private Scene3D scene; // Borrowed destination for mesh and enabled light membership.
    private boolean inScene; // Whether this particle has registered its mesh in the scene.
    private AttachedLight attachedLight; // Optional owned light state allocated only on demand.
    private float age, lifetime = 1f; // Elapsed age and expiration threshold in simulation seconds.
    float physicsMass; // Body mass used by the emitter when applying acceleration force.
    long physicsStep; // Step number whose completion should advance this particle's age.

    // All optional light configuration and state is allocated only when requested.
    /**
     * Lazily allocated point-light configuration and scene-registration state.
     * The particle owns this light independently of borrowed render materials;
     * pooled reuse restores its defaults without replacing the object.
     *
     * @author Albert Beaupre
     */
    private static final class AttachedLight {
        final PointLight3D light = new PointLight3D(); // Owned point light reused across particle lifetimes.
        float x, y, z; // World-space position offset independent of body rotation.
        boolean enabled, inScene; // Requested enablement and actual scene-registration flags.
    }

    /**
     * Lazily creates and returns this particle's owned point light without enabling
     * it. Defaults are white, range 10, intensity 1, and no shadow maps. An enabled
     * light follows the canonical position plus a world-space offset; it does not
     * make the particle material emissive. Pooled reuse resets its parameters.
     *
     * @return live point light, reused for this pooled particle
     */
    public PointLight3D getLight() {
        if (attachedLight == null) {
            attachedLight = new AttachedLight();
            attachedLight.light.getPosition().set(getPosition());
        }
        return attachedLight.light;
    }

    /**
     * Enables or removes the optional light in the attached scene immediately.
     * Enabling lazily allocates the light; disabling an unallocated light is a no-op.
     * The particle's render material is unchanged.
     *
     * @param enabled whether the light should belong to the attached scene
     * @return this particle
     */
    public Particle3D setLightEnabled(boolean enabled) {
        if (enabled) getLight();
        if (attachedLight != null) {
            attachedLight.enabled = enabled;
            syncLight();
        }
        return this;
    }

    /**
     * Reads the requested light-enabled state, which can be true without a scene.
     *
     * @return true if optional light state exists and is enabled
     */
    public boolean isLightEnabled() {return attachedLight != null && attachedLight.enabled;}

    /**
     * Sets a finite world-space offset and immediately synchronizes light position
     * and membership. It does not rotate with the body. A zero offset avoids
     * allocation when no light exists; a nonzero offset does not enable the light.
     *
     * @param x world X offset
     * @param y world Y offset
     * @param z world Z offset
     * @return this particle
     * @throws IllegalArgumentException if any component is non-finite
     */
    public Particle3D setLightOffset(float x, float y, float z) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
            throw new IllegalArgumentException("Light offset must be finite");
        if (attachedLight == null && x == 0 && y == 0 && z == 0) return this;
        getLight();
        attachedLight.x = x;
        attachedLight.y = y;
        attachedLight.z = z;
        syncLight();
        return this;
    }

    /**
     * Returns the live centered billboard used when no mesh geometry is assigned.
     * Its position remains canonical even while a mesh is selected. Pooled reuse
     * resets billboard state, so retained references are not permanent particles.
     *
     * @return owned mutable billboard
     */
    public BillboardSprite3D getSprite() {return sprite;}

    /**
     * Exposes the canonical world position stored by the billboard. Cosmetic
     * updates integrate it directly; physical updates overwrite it from the body.
     * For physical teleports, change the body rather than this cached vector.
     *
     * @return live position in world units
     */
    public Vector3f getPosition() {return sprite.getPosition();}

    /**
     * Exposes world velocity used for cosmetic integration and initial body setup.
     * After body creation, physics synchronization overwrites this vector; change
     * the body to alter physical velocity.
     *
     * @return live velocity in world units per second
     */
    public Vector3f getVelocity() {return velocity;}

    /**
     * Exposes constant world acceleration for cosmetic motion. In physics mode
     * the emitter multiplies this vector by mass and applies force before each
     * tick, in addition to the world's configured gravity.
     *
     * @return live acceleration in world units per second squared
     */
    public Vector3f getAcceleration() {return acceleration;}

    /**
     * Lazily creates a reusable mesh instance at the canonical position. Merely
     * requesting it does not enable mesh rendering; assign geometry with setModel.
     * Scale affects rendering only, while physics settings define collision size.
     * Physical creation clears parent transforms because body poses are world-space.
     *
     * @return live reusable mesh instance
     */
    public ModelInstance3D getModelInstance() {
        if (model == null) {
            model = new ModelInstance3D().setPosition(getPosition());
            defaultModelMaterial = model.getMaterial();
        }
        return model;
    }

    /**
     * Selects borrowed mesh geometry and synchronizes position and scene membership.
     * Null restores billboard rendering and removes the mesh from the scene.
     * Geometry is not disposed or copied, and physics collision shape is unchanged.
     *
     * @param geometry borrowed mesh, or null for billboard rendering
     * @return this particle
     */
    public Particle3D setModel(Model3D geometry) {
        if (geometry != null || model != null) getModelInstance().setModel(geometry);
        syncModel();
        return this;
    }

    /**
     * Exposes the emitter-owned body for physical forces, velocities, and teleports.
     * Do not retain it beyond particle expiration or treat pooled reuse as identity.
     *
     * @return live body, or null for a cosmetic or released particle
     */
    public RigidBody3D getBody() {return body;}

    /**
     * Reads elapsed simulation time, which may exceed lifetime after a large step.
     *
     * @return age in seconds
     */
    public float getAge() {return age;}

    /**
     * Reads the configured expiration threshold without changing age.
     *
     * @return positive finite lifetime in seconds
     */
    public float getLifetime() {return lifetime;}

    /**
     * Changes the expiration threshold without resetting age. Shortening it below
     * the current age makes the particle eligible for retirement at the next update.
     *
     * @param seconds positive finite lifetime
     * @return this particle
     * @throws IllegalArgumentException if seconds is nonpositive or non-finite
     */
    public Particle3D setLifetime(float seconds) {
        if (!Float.isFinite(seconds) || seconds <= 0f)
            throw new IllegalArgumentException("Lifetime must be positive and finite");
        lifetime = seconds;
        return this;
    }

    /**
     * Returns age divided by lifetime with an upper clamp of one. Normal emitter
     * updates keep age nonnegative; this accessor does not impose a lower clamp.
     *
     * @return normalized lifetime progress
     */
    public float getProgress() {return Math.min(1f, age / lifetime);}

    /**
     * Selects the existing instance only when it has geometry assigned. Does not
     * allocate the lazy instance or alter scene membership.
     *
     * @return renderable mesh instance, or null for billboard selection
     */
    ModelInstance3D mesh() {return model != null && model.getModel() != null ? model : null;}

    /**
     * Transfers owned mesh/light membership between borrowed scenes and synchronizes
     * position. When reattaching to the same scene, detects externally removed
     * entries so synchronization can restore them. Billboards are never added here.
     *
     * @param destination scene receiving mesh and enabled light, or null to detach
     */
    void attach(Scene3D destination) {
        if (scene == destination && inScene && !scene.getRenderables().contains(model)) inScene = false;
        if (attachedLight != null && scene == destination && attachedLight.inScene
                && !scene.getLights().contains(attachedLight.light)) attachedLight.inScene = false;
        if (scene != destination) {
            if (inScene) scene.remove(model);
            if (attachedLight != null && attachedLight.inScene) {
                scene.removeLight(attachedLight.light);
                attachedLight.inScene = false;
            }
            inScene = false;
            scene = destination;
        }
        syncModel();
    }

    /**
     * Copies canonical position into an existing mesh and updates any optional light.
     * Adds or removes mesh membership according to geometry presence in the
     * attached scene. Does not read the physics body or allocate a mesh.
     */
    void syncModel() {
        if (model != null) model.setPosition(getPosition());
        if (attachedLight != null) syncLight();
        if (scene == null) return;
        boolean hasMesh = mesh() != null;
        if (hasMesh && !inScene) {
            scene.add(model);
            inScene = true;
        } else if (!hasMesh && inScene) {
            scene.remove(model);
            inScene = false;
        }
    }

    /**
     * Synchronizes the already allocated light's position and requested membership.
     * Requires non-null attached-light state; world offsets are added directly
     * without rotation. No scene means position updates only.
     */
    private void syncLight() {
        var state = attachedLight;
        var position = getPosition();
        state.light.setPosition(position.x + state.x, position.y + state.y, position.z + state.z);
        if (scene == null) return;
        if (state.enabled && !state.inScene) {
            scene.addLight(state.light);
            state.inScene = true;
        } else if (!state.enabled && state.inScene) {
            scene.removeLight(state.light);
            state.inScene = false;
        }
    }

    /**
     * Associates an emitter-owned body and its integration metadata, removes any
     * mesh parent transform, and immediately synchronizes the body's world pose.
     * The caller must release any previous body before replacing it.
     *
     * @param body non-null live body controlled by the emitter
     * @param mass mass used to convert acceleration into force
     * @param firstStep first physics step whose time contributes to age
     */
    void setBody(RigidBody3D body, float mass, long firstStep) {
        this.body = body;
        physicsMass = mass;
        physicsStep = firstStep;
        if (model != null) model.setParentTransform(IDENTITY);
        syncBody();
    }

    /**
     * Copies the live body's world position and velocity into particle state and
     * its rotation into an existing mesh, allocating quaternion scratch lazily.
     * Then updates mesh/light placement. Requires a non-null, usable body.
     */
    void syncBody() {
        body.getPosition(getPosition());
        body.getLinearVelocity(velocity);
        if (model != null) {
            if (rotation == null) rotation = new Quaternionf();
            model.setRotation(body.getRotation(rotation));
        }
        syncModel();
    }

    /**
     * Closes the emitter-owned body when present and detaches mesh and light from
     * the scene. Borrowed models, materials, and textures are not disposed. Reset
     * is a separate step performed before a pooled particle is initialized again.
     */
    void release() {
        if (body != null) {
            body.close();
            body = null;
        }
        attach(null);
    }

    /**
     * Restores age, motion, billboard, optional light, and optional mesh defaults
     * for pool reuse. Reuses owned objects and resets only their original default
     * materials, leaving borrowed replacement materials untouched. Call release
     * first: this routine does not close a body or remove old scene membership.
     */
    void reset() {
        age = 0f;
        lifetime = 1f;
        velocity.zero();
        acceleration.zero();
        physicsMass = 0f;
        physicsStep = 0;
        resetMaterial(defaultSpriteMaterial, RenderPass3D.TRANSLUCENT);
        sprite.setPosition(0, 0, 0).setTextureRegion(null).setMaterial(defaultSpriteMaterial)
                .setSize(1, 1).setMode(BillboardMode3D.SPHERICAL).setAnchor(.5f, .5f).setVisible(true);
        sprite.getColor().set(1, 1, 1, 1);
        if (attachedLight != null) {
            attachedLight.enabled = attachedLight.inScene = false;
            attachedLight.x = attachedLight.y = attachedLight.z = 0;
            attachedLight.light.setPosition(0, 0, 0).setRange(10).setIntensity(1).setCastsShadows(false);
            attachedLight.light.getColor().set(1, 1, 1, 1);
        }
        if (model != null) {
            resetMaterial(defaultModelMaterial, RenderPass3D.OPAQUE);
            model.setModel(null).setMaterial(defaultModelMaterial).setPosition(0, 0, 0)
                    .setScale(1).setRotation(0, 0, 0).setParentTransform(IDENTITY).setVisible(true);
        }
    }

    /**
     * Restores the owned default material's rendering, surface, texture, and color
     * settings for a new particle lifetime. Borrowed textures are detached, not
     * disposed; callers must pass only a particle-owned default material.
     *
     * @param material owned mutable material to reset
     * @param pass render pass appropriate to billboard or mesh defaults
     */
    private static void resetMaterial(Material3D material, RenderPass3D pass) {
        material.setRenderPass(pass).setLightingMix(1).setFogMix(1).setRadianceMix(1)
                .setDepthTest(true).setCullBackFaces(false).setTexture(null).setAlphaCutoff(.001f)
                .setCastsShadow(true).setReceivesShadow(true).setRoughness(.65f).setMetallic(0)
                .setTransmission(0).setIndexOfRefraction(1.5f).setEmissionStrength(1);
        material.getTint().set(1, 1, 1, 1);
        material.getEmissive().set(0, 0, 0, 0);
    }

    /**
     * Integrates cosmetic constant-acceleration motion up to expiration, updates
     * render placement, then advances age by the entire delta. Physical particles
     * must instead synchronize their bodies and advance age through physics ticks.
     *
     * @param delta finite nonnegative elapsed seconds supplied by the emitter
     * @return true while age remains below lifetime
     */
    boolean update(float delta) {
        float dt = Math.min(delta, Math.max(0, lifetime - age));
        getPosition().add(velocity.x() * dt + .5f * acceleration.x() * dt * dt,
                velocity.y() * dt + .5f * acceleration.y() * dt * dt,
                velocity.z() * dt + .5f * acceleration.z() * dt * dt);
        velocity.add(acceleration.x() * dt, acceleration.y() * dt, acceleration.z() * dt);
        if (model != null || attachedLight != null) syncModel();
        return advanceAge(delta);
    }

    /**
     * Adds elapsed time without moving geometry or clamping the stored age.
     * Used by both cosmetic and physics clocks under emitter validation.
     *
     * @param delta nonnegative elapsed seconds
     * @return true when the updated age is strictly below lifetime
     */
    boolean advanceAge(float delta) {
        age += delta;
        return age < lifetime;
    }
}
