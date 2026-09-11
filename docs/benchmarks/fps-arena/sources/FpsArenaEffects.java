package valthorne.examples;

import java.util.Random;
import org.joml.Vector3f;
import org.joml.primitives.Rayf;
import valthorne.graphics.Color;
import valthorne.graphics.model.*;
import valthorne.graphics.particle.*;
import valthorne.math.physics.*;

/** Shared effect geometry, bounded native bodies and a separate small light budget. */
final class FpsArenaEffects implements AutoCloseable {
    private final Scene3D scene;
    private final PhysicsWorld3D world;
    private final Random random = new Random(911);
    private final Model3D chip = ModelBuilder3D.box(.055f, .055f, .055f);
    private final Model3D bead = ModelBuilder3D.sphere(.075f, 8, 4);
    private final Material3D brass = material(new Color(.95f, .52f, .1f, 1), .65f);
    private final Color[] colors = {new Color(1, .45f, .08f, 1), new Color(.12f, .65f, 1, 1), new Color(.8f, .15f, 1, 1)};
    private final Material3D[] flareMaterials = new Material3D[3];
    private final Vector3f origin = new Vector3f(), direction = new Vector3f(0, 0, 1);
    private final Rayf launchRay = new Rayf();
    private ParticleEmitter3D debris, flashes, flares;
    private float burstSpeed = 3;
    boolean physical = true, lights = true;
    float lightGain = 1;
    int palette;

    FpsArenaEffects(Scene3D scene, PhysicsWorld3D world, boolean physical, boolean lights) {
        this.scene = scene;
        this.world = world;
        this.physical = physical;
        this.lights = lights;
        for (int i = 0; i < colors.length; i++) flareMaterials[i] = material(colors[i], .35f);
        rebuild();
    }

    private static Material3D material(Color color, float metal) {
        return new Material3D().setTint(color).setMetallic(metal).setRoughness(.3f).setCastsShadow(false);
    }

    void rebuild() {
        close();
        debris = new ParticleEmitter3D(160, p -> {
            p.setModel(chip).setLifetime(.7f + random.nextFloat() * 1.1f);
            p.getModelInstance().setMaterial(brass);
            p.getPosition().set(origin);
            p.getVelocity().set(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1,
                    random.nextFloat() * 1.5f).mul(burstSpeed).fma(1.2f, direction);
            if (!physical) p.getAcceleration().set(0, 0, -9.81f);
        }).attach(scene);
        flares = new ParticleEmitter3D(12, p -> {
            p.setModel(bead).setLifetime(6).setLightEnabled(lights);
            p.getModelInstance().setMaterial(flareMaterials[palette]);
            p.getPosition().set(origin);
            p.getVelocity().set(direction).mul(8).add(0, 0, 2);
            p.getLight().setColor(colors[palette]).setRange(7).setIntensity(18 * lightGain);
            if (!physical) p.getAcceleration().set(0, 0, -5);
        }).attach(scene);
        if (physical) {
            var debrisBody = new BodySettings3D(CollisionShape3D.sphere(.03f), MotionType3D.DYNAMIC)
                    .setLayer(3).setMass(.015f).setRestitution(.48f).setFriction(.5f).setContinuousCollision(true);
            var flareBody = new BodySettings3D(CollisionShape3D.sphere(.075f), MotionType3D.DYNAMIC)
                    .setLayer(3).setMass(.08f).setRestitution(.55f).setGravityFactor(.5f).setContinuousCollision(true);
            debris.setPhysics(world, p -> debrisBody);
            flares.setPhysics(world, p -> flareBody);
        }
        flashes = new ParticleEmitter3D(8, p -> {
            p.setModel(bead).setLifetime(.12f).setLightEnabled(lights);
            p.getModelInstance().setMaterial(flareMaterials[palette]).setScale(.45f);
            p.getPosition().set(origin);
            p.getLight().setColor(colors[palette]).setRange(6).setIntensity(45 * lightGain);
        }).attach(scene);
    }

    void impact(FpsArenaWorld.Impact hit) {
        origin.set(hit.position()).fma(.08f, hit.normal());
        direction.set(hit.normal());
        burstSpeed = hit.explosion() ? 7 : 3;
        debris.burst(hit.explosion() ? 48 : 12);
        flashes.burst(1);
    }

    void muzzle(Vector3f eye, Vector3f desiredPosition, RigidBody3D ignoredBody) {
        if (flashes.getParticleCount() == flashes.getCapacity()) return;
        direction.set(desiredPosition).sub(eye);
        float offset = direction.length();
        if (offset <= 0) return;
        direction.div(offset);
        if (placeOrigin(eye, offset, .075f * .45f + .01f, ignoredBody)) flashes.burst(1);
    }

    boolean flare(Vector3f eye, Vector3f forward, RigidBody3D ignoredBody) {
        if (flares.getParticleCount() == flares.getCapacity()) return false;
        direction.set(forward).normalize();
        return placeOrigin(eye, .65f, .085f, ignoredBody) && flares.burst(1) == 1;
    }

    private boolean placeOrigin(Vector3f eye, float offset, float clearance, RigidBody3D ignoredBody) {
        launchRay.oX = eye.x; launchRay.oY = eye.y; launchRay.oZ = eye.z;
        launchRay.dX = direction.x; launchRay.dY = direction.y; launchRay.dZ = direction.z;
        // Leave the sphere radius and a small gap in front of the surface.
        // CCD cannot repair a body that was initially born inside or beyond a wall.
        var hit = world.raycast(launchRay, offset + clearance, ignoredBody);
        if (hit != null) {
            float approach = -hit.normal().dot(direction);
            if (approach <= 0) return false;
            offset = Math.min(offset, hit.distance() - clearance / approach);
            if (offset <= 0) return false;
        }
        origin.set(eye).fma(offset, direction);
        return true;
    }

    void update(float delta) {
        if (!physical) {
            debris.update(delta);
            flares.update(delta);
        }
        flashes.update(delta);
        updateLights(flares, 18);
        updateLights(flashes, 45);
    }

    private void updateLights(ParticleEmitter3D emitter, float power) {
        var active = emitter.getParticles();
        for (int i = 0; i < active.size(); i++) {
            var particle = active.get(i);
            particle.setLightEnabled(lights);
            particle.getLight().setIntensity(power * lightGain * Math.min(1, (1 - particle.getProgress()) * 4));
        }
    }

    int particleCount() { return debris.getParticleCount() + flashes.getParticleCount() + flares.getParticleCount(); }
    int flareCount() { return flares.getParticleCount(); }
    int attachedLightCount() { return lights ? flares.getParticleCount() + flashes.getParticleCount() : 0; }

    @Override public void close() {
        if (debris != null) {debris.close(); debris = null;}
        if (flares != null) {flares.close(); flares = null;}
        if (flashes != null) {flashes.close(); flashes = null;}
    }
}
