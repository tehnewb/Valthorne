package valthorne.examples;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.primitives.Rayf;
import valthorne.graphics.Color;
import valthorne.graphics.model.Material3D;
import valthorne.graphics.model.Model3D;
import valthorne.graphics.model.ModelBuilder3D;
import valthorne.graphics.model.ModelInstance3D;
import valthorne.graphics.model.Scene3D;
import valthorne.math.physics.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * CPU-side gameplay for the FPS arena. Owns its Jolt world and its scene membership;
 * the scene, imported models and impact callback are borrowed. All gameplay clocks
 * use fixed physics ticks. Reset replaces the world, so callers must reattach any
 * borrowed-world effects afterward. A null gallery selects simple CPU-only cover.
 */
public final class FpsArenaWorld implements AutoCloseable {
    public static final int MAGAZINE_SIZE = 24;
    public static final float PLAYER_RADIUS = .34f, PLAYER_HEIGHT = 1.75f;
    public static final float WALK_SPEED = 4.4f, SPRINT_SPEED = 7.2f;
    public static final float SHOT_INTERVAL = .12f, RELOAD_SECONDS = 1.5f, GRENADE_FUSE = 2.4f;
    private static final int MAX_DRONES = 18, MAX_GRENADES = 4;
    private static final float EYE_OFFSET = .76f, SHOT_RANGE = 65f, BLAST_RADIUS = 5.5f;
    private static final int ENVIRONMENT_LAYER = 0, PLAYER_LAYER = 1, DRONE_LAYER = 2, EFFECT_LAYER = 3, GRENADE_LAYER = 4;

    /** Immutable event values; vector accessors return defensive copies. */
    public record Impact(Vector3f position, Vector3f normal, boolean explosion) {
        public Impact {position = new Vector3f(position); normal = new Vector3f(normal);}
        @Override public Vector3f position() {return new Vector3f(position);}
        @Override public Vector3f normal() {return new Vector3f(normal);}
    }

    private final Scene3D scene;
    private final PhysicsStudioModels gallery;
    private final Consumer<Impact> impacts;
    private final Model3D cube = ModelBuilder3D.box(1, 1, 1);
    private final Model3D sphere = ModelBuilder3D.sphere(1, 16, 10);
    private final Model3D ramp = rampModel();
    private final ArrayList<ModelInstance3D> ownedModels = new ArrayList<>();
    private final ArrayList<Drone> drones = new ArrayList<>();
    private final ArrayList<Prop> props = new ArrayList<>();
    private final ArrayList<Grenade> grenades = new ArrayList<>();
    private final Vector3f playerPosition = new Vector3f(), velocity = new Vector3f();
    private final Vector3f a = new Vector3f(), b = new Vector3f(), direction = new Vector3f();
    private final Rayf ray = new Rayf();
    private final Quaternionf identity = new Quaternionf();
    private final Material3D concrete = material(.24f, .27f, .30f, .83f, .08f);
    private final Material3D wall = material(.13f, .17f, .22f, .58f, .32f);
    private final Material3D steel = material(.30f, .36f, .40f, .32f, .72f);
    private final Material3D dark = material(.055f, .07f, .09f, .43f, .55f);
    private final Material3D yellow = material(.95f, .49f, .075f, .40f, .28f);
    private final Material3D cyan = accent(.06f, .65f, 1f);
    private final Material3D droneShell = material(.10f, .13f, .17f, .25f, .8f);
    private final Material3D droneEye = accent(1f, .10f, .025f);
    private final Material3D grenadeMaterial = material(.27f, .36f, .09f, .35f, .6f);
    private PhysicsWorld3D physics;
    private RigidBody3D player;
    private int health, ammo, reserve, score, wave, kills, grenadesRemaining, destroyedProps;
    private float shotCooldown, reloadRemaining, grenadeCooldown, waveCountdown;
    private float forward, strafe, yaw, jumpBuffer;
    private boolean sprint, jumpHeld, grounded, closed, wavesEnabled = true;
    private double elapsed;

    public FpsArenaWorld(Scene3D scene, PhysicsStudioModels gallery, Consumer<Impact> impacts) {
        this.scene = Objects.requireNonNull(scene, "scene");
        this.gallery = gallery;
        this.impacts = Objects.requireNonNull(impacts, "impacts");
        reset();
    }

    private void check() {
        if (closed) throw new IllegalStateException("FPS arena is closed");
    }

    /** Rebuilds a fresh arena and match, preserving unrelated scene entries. */
    public void reset() {
        check();
        releaseWorld();
        CollisionLayers3D layers = new CollisionLayers3D()
                .setCollision(EFFECT_LAYER, EFFECT_LAYER, false)
                .setCollision(PLAYER_LAYER, EFFECT_LAYER, false)
                .setCollision(DRONE_LAYER, DRONE_LAYER, false)
                .setCollision(PLAYER_LAYER, GRENADE_LAYER, false)
                .setCollision(GRENADE_LAYER, GRENADE_LAYER, false);
        physics = new PhysicsWorld3D(new PhysicsWorld3D.Settings(1f / 60, 8, 1024, 32768, 16384, 0), layers);
        health = 100; ammo = MAGAZINE_SIZE; reserve = 120; score = kills = wave = destroyedProps = 0;
        grenadesRemaining = 4;
        shotCooldown = reloadRemaining = grenadeCooldown = waveCountdown = jumpBuffer = 0;
        forward = strafe = yaw = 0;
        sprint = jumpHeld = grounded = false;
        elapsed = 0;
        wavesEnabled = true;
        try {
            buildArena();
            player = physics.createBody(new BodySettings3D(CollisionShape3D.capsule(PLAYER_RADIUS, PLAYER_HEIGHT), MotionType3D.DYNAMIC)
                    .setPosition(0, -12, .90f).setMass(80).setFriction(0).setDamping(0, 0)
                    .setLayer(PLAYER_LAYER).setRotationLocked(true).setAllowSleeping(false).setContinuousCollision(true));
            player.getPosition(playerPosition);
            startWave();
            physics.addBeforeStepListener(this::beforeStep);
            physics.addAfterStepListener(this::afterStep);
            physics.optimizeBroadPhase();
        } catch (RuntimeException | Error failure) {
            releaseWorld();
            throw failure;
        }
    }

    /** Yaw zero faces +Y; positive yaw turns toward +X. Positive strafe moves right. */
    public void update(float dt, float forward, float strafe, boolean sprint, boolean jump, float yawRadians) {
        check();
        finite(dt, "dt"); finite(forward, "forward"); finite(strafe, "strafe"); finite(yawRadians, "yaw");
        if (dt < 0) throw new IllegalArgumentException("dt must be nonnegative");
        this.forward = Math.clamp(forward, -1f, 1f);
        this.strafe = Math.clamp(strafe, -1f, 1f);
        this.sprint = sprint;
        this.yaw = yawRadians;
        if (jump && !jumpHeld && !isDead()) jumpBuffer = .15f;
        jumpHeld = jump;
        physics.update(dt);
    }

    private void beforeStep(PhysicsWorld3D world) {
        float dt = world.getFixedTimeStep();
        elapsed += dt;
        shotCooldown = Math.max(0, shotCooldown - dt);
        grenadeCooldown = Math.max(0, grenadeCooldown - dt);
        if (reloadRemaining > 0 && (reloadRemaining = Math.max(0, reloadRemaining - dt)) == 0) {
            int loaded = Math.min(MAGAZINE_SIZE - ammo, reserve);
            ammo += loaded; reserve -= loaded;
        }
        player.getPosition(playerPosition);
        movePlayer(dt);
        for (int i = 0; i < drones.size(); i++) updateDrone(drones.get(i), dt);
    }

    private void movePlayer(float dt) {
        player.getLinearVelocity(velocity);
        PhysicsRayHit3D support = physics.raycast(queryRay(playerPosition.x, playerPosition.y, playerPosition.z, 0, 0, -1), PLAYER_HEIGHT / 2 + .14f, player);
        grounded = support != null && support.normal().z > .55f && velocity.z < 1f;
        float f = isDead() ? 0 : forward, s = isDead() ? 0 : strafe;
        float length = (float) Math.sqrt(f * f + s * s);
        if (length > 1) {f /= length; s /= length;}
        float sin = (float) Math.sin(yaw), cos = (float) Math.cos(yaw);
        float speed = sprint ? SPRINT_SPEED : WALK_SPEED;
        float targetX = (sin * f + cos * s) * speed, targetY = (cos * f - sin * s) * speed;
        float change = (grounded ? 28f : 9f) * dt;
        velocity.x = approach(velocity.x, targetX, change);
        velocity.y = approach(velocity.y, targetY, change);
        if (grounded && jumpBuffer > 0 && !isDead()) {
            velocity.z = 5.7f;
            jumpBuffer = 0;
            grounded = false;
        } else jumpBuffer = Math.max(0, jumpBuffer - dt);
        player.setLinearVelocity(velocity);
    }

    private void afterStep(PhysicsWorld3D world) {
        float dt = world.getFixedTimeStep();
        player.getPosition(playerPosition);
        if (playerPosition.z < -5) health = 0;
        for (int i = 0; i < drones.size(); i++) syncDrone(drones.get(i));
        for (int i = grenades.size() - 1; i >= 0; i--) {
            Grenade grenade = grenades.get(i);
            grenade.remaining -= dt;
            if (grenade.remaining <= 0 || grenade.body.getPosition(a).z < -5) explode(grenade);
        }
        for (int i = props.size() - 1; i >= 0; i--)
            if (props.get(i).body.getPosition(a).z < -5) removeProp(props.get(i), false);
        if (!isDead() && wavesEnabled && drones.isEmpty()) {
            waveCountdown -= dt;
            if (waveCountdown <= 0) {
                health = Math.min(100, health + 15);
                reserve = Math.min(240, reserve + 48);
                grenadesRemaining = Math.min(4, grenadesRemaining + 1);
                score += 150;
                startWave();
            }
        }
    }

    /** Fires one hitscan round; false means dead, cooling down, empty or reloading. */
    public boolean fire(Vector3f eye, Vector3f aim) {
        check(); validateAim(eye, aim);
        if (isDead() || shotCooldown > 0 || reloadRemaining > 0 || ammo == 0) return false;
        ammo--;
        shotCooldown = SHOT_INTERVAL;
        PhysicsRayHit3D hit = physics.raycast(queryRay(eye.x, eye.y, eye.z, direction.x, direction.y, direction.z), SHOT_RANGE, player);
        if (hit != null) {
            RigidBody3D body = hit.body();
            Vector3f point = hit.position(), normal = hit.normal();
            Object target = body.getUserData();
            if (body.getMotionType() == MotionType3D.DYNAMIC) body.addImpulse(new Vector3f(direction).mul(4.8f), point);
            impacts.accept(new Impact(point, normal, false));
            if (target instanceof Drone drone) damageDrone(drone, 36);
            else if (target instanceof Prop prop) {prop.health -= 34; if (prop.health <= 0) removeProp(prop, true);}
            else if (target instanceof Grenade grenade) explode(grenade);
        }
        return true;
    }

    public void reload() {
        check();
        if (!isDead() && ammo < MAGAZINE_SIZE && reserve > 0 && reloadRemaining == 0) reloadRemaining = RELOAD_SECONDS;
    }

    public boolean throwGrenade(Vector3f eye, Vector3f aim) {
        check(); validateAim(eye, aim);
        if (isDead() || grenadesRemaining == 0 || grenadeCooldown > 0 || grenades.size() >= MAX_GRENADES) return false;
        PhysicsRayHit3D obstruction = physics.raycast(queryRay(eye.x, eye.y, eye.z, direction.x, direction.y, direction.z), .7f, player);
        float offset = obstruction == null ? .55f : Math.max(.04f, Math.min(.55f, obstruction.distance() - .17f));
        Vector3f start = new Vector3f(direction).mul(offset).add(eye);
        ModelInstance3D model = visual(sphere, grenadeMaterial, start.x, start.y, start.z, .14f, .14f, .14f);
        RigidBody3D body;
        try {
            player.getLinearVelocity(velocity);
            body = physics.createBody(new BodySettings3D(CollisionShape3D.sphere(.14f), MotionType3D.DYNAMIC)
                    .setPosition(start).setMass(.45f).setFriction(.65f).setRestitution(.48f).setDamping(.06f, .2f)
                    .setLayer(GRENADE_LAYER).setContinuousCollision(true)
                    .setLinearVelocity(direction.x * 13 + velocity.x * .35f, direction.y * 13 + velocity.y * .35f,
                            direction.z * 13 + 2.4f + velocity.z * .2f)).bind(model);
        } catch (RuntimeException | Error failure) {removeVisual(model); throw failure;}
        body.setAngularVelocity(new Vector3f(8, 4, 3));
        Grenade grenade = new Grenade(body, model);
        body.setUserData(grenade);
        grenades.add(grenade);
        grenadesRemaining--;
        grenadeCooldown = .65f;
        return true;
    }

    private void explode(Grenade grenade) {
        if (!grenades.remove(grenade)) return;
        Vector3f center = grenade.body.getPosition();
        grenade.body.close();
        removeVisual(grenade.model);
        // Snapshot: damage callbacks may remove targets while impulses affect other live bodies.
        for (RigidBody3D body : physics.getBodies()) {
            if (body.isDestroyed() || body.getMotionType() != MotionType3D.DYNAMIC) continue;
            body.getPosition(a);
            float distance = a.distance(center);
            if (distance >= BLAST_RADIUS) continue;
            b.set(a).sub(center);
            if (distance > .02f) {
                PhysicsRayHit3D blocker = physics.raycast(queryRay(center.x, center.y, center.z, b.x, b.y, b.z), distance);
                if (blocker != null && blocker.body() != body && blocker.distance() < distance - .15f) continue;
                b.div(distance);
            } else b.set(0, 0, 1);
            float strength = 1 - distance / BLAST_RADIUS;
            b.z += .3f;
            body.addImpulse(b.mul(strength * (body == player ? 220f : 32f)));
            int damage = Math.round(110 * strength);
            Object target = body.getUserData();
            if (body == player) hurtPlayer(Math.round(damage * .55f));
            else if (target instanceof Drone drone) damageDrone(drone, damage);
            else if (target instanceof Prop prop) {prop.health -= damage; if (prop.health <= 0) removeProp(prop, true);}
        }
        impacts.accept(new Impact(center, new Vector3f(0, 0, 1), true));
    }

    /** Spawns a physical target drone; used by gameplay and deterministic headless validation. */
    public RigidBody3D spawnDrone(float x, float y, float z) {
        check(); finite(x, "x"); finite(y, "y"); finite(z, "z");
        if (drones.size() >= MAX_DRONES) throw new IllegalStateException("Drone capacity reached");
        ModelInstance3D core = visual(sphere, droneShell, x, y, z, .57f, .57f, .36f);
        RigidBody3D body;
        try {
            body = physics.createBody(new BodySettings3D(CollisionShape3D.sphere(.58f), MotionType3D.DYNAMIC)
                    .setPosition(x, y, z).setMass(8).setGravityFactor(0).setRotationLocked(true)
                    .setDamping(.1f, 0).setFriction(.15f).setLayer(DRONE_LAYER).setAllowSleeping(false)).bind(core);
        } catch (RuntimeException | Error failure) {removeVisual(core); throw failure;}
        Drone drone = new Drone(body, core, z, drones.size());
        drone.eye = visual(sphere, droneEye, x, y - .49f, z, .20f, .13f, .13f);
        drone.left = visual(cube, steel, x - .67f, y, z, .7f, .18f, .10f);
        drone.right = visual(cube, steel, x + .67f, y, z, .7f, .18f, .10f);
        body.setUserData(drone);
        drones.add(drone);
        return body;
    }

    private void updateDrone(Drone drone, float dt) {
        drone.body.getPosition(a);
        float dx = playerPosition.x - a.x, dy = playerPosition.y - a.y;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float inverse = distance > .01f ? 1 / distance : 0;
        float seekX = dx * inverse, seekY = dy * inverse;
        drone.attackCooldown = Math.max(0, drone.attackCooldown - dt);
        drone.steerClock -= dt;
        if (drone.steerClock <= 0) {
            drone.steerClock = .22f;
            PhysicsRayHit3D blocker = distance > 1.9f ? physics.raycast(queryRay(a.x, a.y, a.z, seekX, seekY, 0), 2f, drone.body) : null;
            if (blocker != null && blocker.body() != player && !(blocker.body().getUserData() instanceof Drone)) {
                Vector3f normal = blocker.normal();
                float side = (drone.index & 1) == 0 ? 1 : -1;
                drone.avoidX = -normal.y * side;
                drone.avoidY = normal.x * side;
            } else drone.avoidX = drone.avoidY = 0;
        }
        float speed = isDead() ? 0 : Math.min(2.4f, 1.1f + wave * .12f);
        if (distance < 1.4f) speed = 0;
        float vx = seekX + drone.avoidX * 1.6f, vy = seekY + drone.avoidY * 1.6f;
        float magnitude = (float) Math.sqrt(vx * vx + vy * vy);
        if (magnitude > 0) {vx *= speed / magnitude; vy *= speed / magnitude;}
        // Physical hover stabilizes height; impulses still move drones before they recover.
        float targetHeight = drone.hoverHeight + .10f * (float) Math.sin(elapsed * 2 + drone.index);
        drone.body.getLinearVelocity(velocity);
        drone.body.setLinearVelocity(approach(velocity.x, vx, 3.8f * dt), approach(velocity.y, vy, 3.8f * dt),
                approach(velocity.z, Math.clamp((targetHeight - a.z) * 3, -2f, 2f), 5 * dt));
        if (!isDead() && distance < 12 && drone.attackCooldown == 0) {
            b.set(playerPosition).sub(a);
            PhysicsRayHit3D sight = physics.raycast(queryRay(a.x, a.y, a.z, b.x, b.y, b.z), b.length() + .1f, drone.body);
            if (sight != null && sight.body() == player) {
                hurtPlayer(distance < 1.8f ? 12 : 6);
                drone.attackCooldown = distance < 1.8f ? 1.1f : 2.8f;
            } else drone.attackCooldown = .3f;
        }
    }

    private void syncDrone(Drone drone) {
        drone.body.getPosition(a);
        float angle = (float) Math.atan2(playerPosition.x - a.x, playerPosition.y - a.y);
        float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
        drone.core.setRotation(0, 0, -angle);
        drone.eye.setPosition(a.x + sin * .5f, a.y + cos * .5f, a.z).setRotation(0, 0, -angle);
        drone.left.setPosition(a.x - cos * .66f, a.y + sin * .66f, a.z).setRotation(0, 0, -angle);
        drone.right.setPosition(a.x + cos * .66f, a.y - sin * .66f, a.z).setRotation(0, 0, -angle);
    }

    private void damageDrone(Drone drone, int damage) {
        if (drone.body.isDestroyed()) return;
        drone.health -= damage;
        if (drone.health > 0) return;
        drones.remove(drone);
        Vector3f point = drone.body.getPosition();
        drone.body.close();
        removeVisual(drone.core); removeVisual(drone.eye); removeVisual(drone.left); removeVisual(drone.right);
        kills++; score += 100;
        if (drones.isEmpty()) waveCountdown = 3;
        impacts.accept(new Impact(point, new Vector3f(0, 0, 1), false));
    }

    private void hurtPlayer(int damage) {health = Math.max(0, health - Math.max(0, damage));}

    private void startWave() {
        wave++;
        waveCountdown = 0;
        int count = Math.min(MAX_DRONES, 3 + wave * 2);
        for (int i = 0; i < count; i++) {
            float x = i == 0 && wave == 1 ? 0 : (i % 3 - 1) * 7f;
            float y = i == 0 && wave == 1 ? -1 : 10 + (i / 3) * 1.7f;
            spawnDrone(x, Math.min(15.8f, y), 1.25f + (i % 2) * .15f);
        }
    }

    private void buildArena() {
        box(0, 0, -.30f, 28.6f, 36.6f, .60f, concrete, true);
        box(-14, 0, 2.4f, .6f, 36.6f, 4.8f, wall, true);
        box(14, 0, 2.4f, .6f, 36.6f, 4.8f, wall, true);
        box(0, -18, 2.4f, 28, .6f, 4.8f, wall, true);
        box(0, 18, 2.4f, 28, .6f, 4.8f, wall, true);
        // A solid industrial roof frames the view and catches high grenade throws.
        box(0, 0, 8.2f, 28.6f, 36.6f, .3f, concrete, true);
        for (int xSide : new int[]{-1, 1})
            for (int ySide : new int[]{-1, 1})
                box(xSide * 13.4f, ySide * 17.4f, 4.025f, .85f, .85f, 8.05f, steel, true);
        for (float y : new float[]{-12, -2, 8, 16.5f})
            box(0, y, 7.825f, 27.4f, .35f, .45f, steel, true);
        for (int side : new int[]{-1, 1})
            box(side * 9.5f, 0, 7.825f, .28f, 35.4f, .45f, dark, true);
        // Ribbed bays, overhead crossbeams and illuminated perimeter rails.
        for (int side : new int[]{-1, 1}) {
            for (int i = 0; i < 5; i++) {
                float y = -14 + i * 7;
                box(side * 13.4f, y, 2.35f, .65f, .8f, 4.7f, steel, true);
                box(side * 13.03f, y, 1.25f, .07f, .84f, .12f, yellow, false);
            }
            box(side * 13.1f, 0, 4.15f, .12f, 34, .09f, cyan, false);
        }
        for (float y : new float[]{-9, 4, 16}) box(0, y, 4.55f, 27, .28f, .30f, steel, false);
        // Safe entry markings and three readable traversal lanes.
        for (float x : new float[]{-11.5f, -3.5f, 3.5f, 11.5f})
            box(x, -1, .014f, .07f, 31, .016f, yellow, false);
        for (int i = 0; i < 8; i++) box(-2.5f + i * .7f, -15, .018f, .35f, .75f, .018f, yellow, false);
        for (float y : new float[]{-6, 4}) {
            box(-6.5f, y, .60f, 4, 1.2f, 1.2f, wall, true);
            box(-6.5f, y, 1.25f, 4.2f, 1.3f, .10f, steel, true);
            box(-6.5f, y - .615f, .8f, 3.5f, .04f, .12f, yellow, false);
        }
        box(4.7f, -7, .65f, 2.4f, 1.2f, 1.3f, steel, true);
        box(8, 6, .7f, 6, 6, 1.4f, wall, true);
        ModelInstance3D slope = visual(ramp, concrete, 8, 0, 0, 1, 1, 1);
        physics.createBody(new BodySettings3D(CollisionShape3D.mesh(ramp), MotionType3D.STATIC).setPosition(8, 0, 0).setFriction(.65f)).bind(slope);
        box(11.15f, 6, 2.0f, .12f, 6, 1.2f, steel, true);
        box(-2.5f, 8, 1.75f, .9f, .9f, 3.5f, concrete, true);
        box(3, 11, 1.75f, .9f, .9f, 3.5f, concrete, true);
        for (int i = 0; i < 10; i++) {
            float x = i < 5 ? -10.8f + (i % 2) * 1.05f : 3.4f + (i % 2) * 1.05f;
            float y = i < 5 ? -8 + (i / 2) * 1.2f : 11 + ((i - 5) / 2) * 1.2f;
            addProp(x, y, .52f, i);
        }
        if (gallery != null) {
            addExhibit(gallery.entries().get(1), -9.5f, 1, 0);
            box(-10, 11, .3f, 2.2f, 2.2f, .6f, dark, true);
            addExhibit(gallery.entries().get(0), -10, 11, .6f);
            box(10, 13, .3f, 2.3f, 2.3f, .6f, dark, true);
            addExhibit(gallery.entries().get(2), 10, 13, .6f);
        } else box(-9.5f, 1, .8f, 4.3f, 2.85f, 1.6f, yellow, true);
    }

    private void addExhibit(PhysicsStudioModels.Entry entry, float x, float y, float z) {
        ModelInstance3D model = visual(entry.model(), entry.material(), x, y, z, 1, 1, 1);
        physics.createBody(new BodySettings3D(CollisionShape3D.mesh(entry.model()), MotionType3D.STATIC)
                .setPosition(x, y, z).setRotation(entry.rotation()).setFriction(.6f)).bind(model);
    }

    private void addProp(float x, float y, float z, int index) {
        ModelInstance3D model = visual(cube, index % 2 == 0 ? yellow : steel, x, y, z, .9f, .9f, 1f);
        RigidBody3D body = physics.createBody(new BodySettings3D(CollisionShape3D.box(.9f, .9f, 1f), MotionType3D.DYNAMIC)
                .setPosition(x, y, z).setMass(6).setFriction(.55f).setRestitution(.12f).setContinuousCollision(true)).bind(model);
        Prop prop = new Prop(body, model);
        body.setUserData(prop);
        props.add(prop);
    }

    private void removeProp(Prop prop, boolean destroyed) {
        if (!props.remove(prop)) return;
        Vector3f position = prop.body.getPosition();
        prop.body.close(); removeVisual(prop.model);
        if (destroyed) {
            score += 15; destroyedProps++;
            impacts.accept(new Impact(position, new Vector3f(0, 0, 1), false));
        }
    }

    private void box(float x, float y, float z, float width, float depth, float height, Material3D material, boolean collision) {
        ModelInstance3D model = visual(cube, material, x, y, z, width, depth, height);
        if (collision) physics.createBody(new BodySettings3D(CollisionShape3D.box(width, depth, height), MotionType3D.STATIC)
                .setPosition(x, y, z).setFriction(.6f)).bind(model);
    }

    private ModelInstance3D visual(Model3D geometry, Material3D material, float x, float y, float z, float sx, float sy, float sz) {
        ModelInstance3D model = new ModelInstance3D().setModel(geometry).setMaterial(material)
                .setPosition(x, y, z).setScale(sx, sy, sz);
        scene.add(model); ownedModels.add(model);
        return model;
    }

    private void removeVisual(ModelInstance3D model) {scene.remove(model); ownedModels.remove(model);}

    private static Material3D material(float r, float g, float b, float roughness, float metallic) {
        return new Material3D().setTint(new Color(r, g, b, 1)).setRoughness(roughness).setMetallic(metallic);
    }

    private static Material3D accent(float r, float g, float b) {
        return material(r, g, b, .25f, .25f).setCastsShadow(false);
    }

    private static Model3D rampModel() {
        Vector3f a = new Vector3f(-3, -3, 0), b = new Vector3f(3, -3, 0), c = new Vector3f(3, 3, 0), d = new Vector3f(-3, 3, 0);
        Vector3f e = new Vector3f(-3, -3, .045f), f = new Vector3f(3, -3, .045f), g = new Vector3f(3, 3, 1.4f), h = new Vector3f(-3, 3, 1.4f);
        return new ModelBuilder3D().quad(e, f, g, h, Color.WHITE).quad(d, c, b, a, Color.WHITE)
                .quad(a, b, f, e, Color.WHITE).quad(b, c, g, f, Color.WHITE)
                .quad(c, d, h, g, Color.WHITE).quad(d, a, e, h, Color.WHITE).build();
    }

    private void validateAim(Vector3f eye, Vector3f aim) {
        Objects.requireNonNull(eye, "eye"); Objects.requireNonNull(aim, "direction");
        finite(eye.x, "eye x"); finite(eye.y, "eye y"); finite(eye.z, "eye z");
        double length = Math.sqrt((double) aim.x * aim.x + (double) aim.y * aim.y + (double) aim.z * aim.z);
        if (!Double.isFinite(length) || length == 0) throw new IllegalArgumentException("Aim must be finite and nonzero");
        direction.set((float) (aim.x / length), (float) (aim.y / length), (float) (aim.z / length));
    }

    private Rayf queryRay(float ox, float oy, float oz, float dx, float dy, float dz) {
        ray.oX = ox; ray.oY = oy; ray.oZ = oz;
        ray.dX = dx; ray.dY = dy; ray.dZ = dz;
        return ray;
    }

    private static void finite(float value, String name) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }

    private static float approach(float value, float target, float step) {return value + Math.clamp(target - value, -step, step);}

    public Vector3f eyePosition(Vector3f destination) {check(); return player.getPosition(destination).add(0, 0, EYE_OFFSET);}
    public int getHealth() {return health;}
    public int getAmmo() {return ammo;}
    public int getReserve() {return reserve;}
    public int getScore() {return score;}
    public int getWave() {return wave;}
    public int getEnemies() {return drones.size();}
    public int getKills() {return kills;}
    public int getDestroyedProps() {return destroyedProps;}
    public int getGrenadesRemaining() {return grenadesRemaining;}
    public int getGrenadeCount() {return grenades.size();}
    public boolean isReloading() {return reloadRemaining > 0;}
    public boolean isDead() {return health == 0;}
    public boolean isGrounded() {return grounded;}
    public float getReloadProgress() {return reloadRemaining > 0 ? 1 - reloadRemaining / RELOAD_SECONDS : 1;}
    public float getWaveCountdown() {return waveCountdown;}
    public int getBodyCount() {return physics == null || physics.isClosed() ? 0 : physics.getBodyCount();}
    public PhysicsWorld3D getPhysics() {return physics;}
    public RigidBody3D getPlayerBody() {return player;}
    public List<RigidBody3D> getDroneBodies() {return drones.stream().map(drone -> drone.body).toList();}
    public List<RigidBody3D> getDynamicPropBodies() {return props.stream().map(prop -> prop.body).toList();}
    public List<RigidBody3D> getGrenadeBodies() {return grenades.stream().map(grenade -> grenade.body).toList();}
    public int getDroneHealth(RigidBody3D body) {return body.getUserData() instanceof Drone drone ? Math.max(0, drone.health) : 0;}

    void setWavesEnabled(boolean enabled) {check(); wavesEnabled = enabled;}
    void clearDrones() {
        check();
        for (Drone drone : drones) {
            drone.body.close(); removeVisual(drone.core); removeVisual(drone.eye); removeVisual(drone.left); removeVisual(drone.right);
        }
        drones.clear(); waveCountdown = 3;
    }

    private void releaseWorld() {
        for (ModelInstance3D model : ownedModels) scene.remove(model);
        ownedModels.clear(); drones.clear(); props.clear(); grenades.clear();
        if (physics != null) physics.close();
        physics = null; player = null;
    }

    @Override public void close() {
        if (closed) return;
        releaseWorld();
        closed = true;
    }

    private static final class Drone {
        final RigidBody3D body;
        final ModelInstance3D core;
        final float hoverHeight;
        final int index;
        ModelInstance3D eye, left, right;
        int health = 72;
        float attackCooldown = 2.4f, steerClock, avoidX, avoidY;
        Drone(RigidBody3D body, ModelInstance3D core, float hoverHeight, int index) {
            this.body = body; this.core = core; this.hoverHeight = hoverHeight; this.index = index;
        }
    }
    private static final class Prop {
        final RigidBody3D body;
        final ModelInstance3D model;
        int health = 90;
        Prop(RigidBody3D body, ModelInstance3D model) {this.body = body; this.model = model;}
    }
    private static final class Grenade {
        final RigidBody3D body;
        final ModelInstance3D model;
        float remaining = GRENADE_FUSE;
        Grenade(RigidBody3D body, ModelInstance3D model) {this.body = body; this.model = model;}
    }
}
