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
 */
public final class Particle3D {
    private static final Matrix4f IDENTITY = new Matrix4f();
    private final BillboardSprite3D sprite = new BillboardSprite3D()
            .setMode(BillboardMode3D.SPHERICAL).setAnchor(.5f, .5f);
    private final Material3D defaultSpriteMaterial = sprite.getMaterial();
    private final Vector3f velocity = new Vector3f();
    private final Vector3f acceleration = new Vector3f();
    private ModelInstance3D model;
    private Material3D defaultModelMaterial;
    private Quaternionf rotation;
    private RigidBody3D body;
    private Scene3D scene;
    private boolean inScene;
    private AttachedLight attachedLight;
    private float age, lifetime = 1f;
    float physicsMass;
    long physicsStep;

    // All optional light configuration and state is allocated only when requested.
    private static final class AttachedLight {
        final PointLight3D light = new PointLight3D();
        float x, y, z;
        boolean enabled, inScene;
    }

    /**
     * Lazily returns this particle's independently owned point light, without enabling
     * it. Use setLightEnabled(true) to attach it to the emitter's scene. Defaults are
     * white, range 10, intensity 1 (1000 Filament lumens), and no shadow maps. The
     * enabled light follows the canonical particle position with a world-space offset.
     * It does not make the particle mesh emissive. Pooled reuse resets its parameters.
     */
    public PointLight3D getLight() {
        if (attachedLight == null) {
            attachedLight = new AttachedLight();
            attachedLight.light.getPosition().set(getPosition());
        }
        return attachedLight.light;
    }

    /** Enables or removes this particle's point light without changing its mesh. */
    public Particle3D setLightEnabled(boolean enabled) {
        if (enabled) getLight();
        if (attachedLight != null) {
            attachedLight.enabled = enabled;
            syncLight();
        }
        return this;
    }

    public boolean isLightEnabled() {return attachedLight != null && attachedLight.enabled;}

    /** Sets a finite world-space offset; it does not rotate with a physical particle. */
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

    /** Returns the live billboard; particles without a model use it for rendering. */
    public BillboardSprite3D getSprite() {return sprite;}

    /** Returns the live, canonical world position, stored by the billboard. */
    public Vector3f getPosition() {return sprite.getPosition();}

    /** Returns live velocity; after physical creation, use the body to change velocity. */
    public Vector3f getVelocity() {return velocity;}

    /** Returns live constant acceleration, additional to world gravity in physics mode. */
    public Vector3f getAcceleration() {return acceleration;}

    /**
     * Lazily creates a reusable mesh instance for configuring borrowed material,
     * geometry, scale and rotation. Merely requesting it does not enable mesh
     * rendering: assign geometry with {@link #setModel(Model3D)}. Its local scale
     * affects rendering only; the physics settings define collision dimensions.
     * Physical creation clears any parent transform because Jolt poses are world-space.
     */
    public ModelInstance3D getModelInstance() {
        if (model == null) {
            model = new ModelInstance3D().setPosition(getPosition());
            defaultModelMaterial = model.getMaterial();
        }
        return model;
    }

    /** Selects borrowed mesh geometry; null restores billboard rendering. */
    public Particle3D setModel(Model3D geometry) {
        if (geometry != null || model != null) getModelInstance().setModel(geometry);
        syncModel();
        return this;
    }

    /** Returns the live emitter-owned body, or null for a cosmetic/released particle. */
    public RigidBody3D getBody() {return body;}

    /** Returns elapsed simulation age in seconds, which may exceed the lifetime. */
    public float getAge() {return age;}

    public float getLifetime() {return lifetime;}

    /** Sets a positive finite lifetime without resetting the current age. */
    public Particle3D setLifetime(float seconds) {
        if (!Float.isFinite(seconds) || seconds <= 0f)
            throw new IllegalArgumentException("Lifetime must be positive and finite");
        lifetime = seconds;
        return this;
    }

    public float getProgress() {return Math.min(1f, age / lifetime);}

    ModelInstance3D mesh() {return model != null && model.getModel() != null ? model : null;}

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

    void setBody(RigidBody3D body, float mass, long firstStep) {
        this.body = body;
        physicsMass = mass;
        physicsStep = firstStep;
        if (model != null) model.setParentTransform(IDENTITY);
        syncBody();
    }

    void syncBody() {
        body.getPosition(getPosition());
        body.getLinearVelocity(velocity);
        if (model != null) {
            if (rotation == null) rotation = new Quaternionf();
            model.setRotation(body.getRotation(rotation));
        }
        syncModel();
    }

    /** Releases only emitter-owned native state and scene membership. */
    void release() {
        if (body != null) {
            body.close();
            body = null;
        }
        attach(null);
    }

    /** Restores independently owned defaults without allocating replacement sprites. */
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

    private static void resetMaterial(Material3D material, RenderPass3D pass) {
        material.setRenderPass(pass).setLightingMix(1).setFogMix(1).setRadianceMix(1)
                .setDepthTest(true).setCullBackFaces(false).setTexture(null).setAlphaCutoff(.001f)
                .setCastsShadow(true).setReceivesShadow(true).setRoughness(.65f).setMetallic(0)
                .setTransmission(0).setIndexOfRefraction(1.5f).setEmissionStrength(1);
        material.getTint().set(1, 1, 1, 1);
        material.getEmissive().set(0, 0, 0, 0);
    }

    /** Integrates cosmetic motion only, capping movement at expiration. */
    boolean update(float delta) {
        float dt = Math.min(delta, Math.max(0, lifetime - age));
        getPosition().add(velocity.x() * dt + .5f * acceleration.x() * dt * dt,
                velocity.y() * dt + .5f * acceleration.y() * dt * dt,
                velocity.z() * dt + .5f * acceleration.z() * dt * dt);
        velocity.add(acceleration.x() * dt, acceleration.y() * dt, acceleration.z() * dt);
        if (model != null || attachedLight != null) syncModel();
        return advanceAge(delta);
    }

    boolean advanceAge(float delta) {
        age += delta;
        return age < lifetime;
    }
}
