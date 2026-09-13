package valthorne.math.physics;

import org.joml.primitives.Rayf;
import org.joml.Vector3f;

import java.util.*;
import org.teavm.jso.*;
import java.util.function.Consumer;

/** Build-selected Jolt WASM backend. Each instance owns an independent Z-up world. */
public final class PhysicsWorld3D implements AutoCloseable {
    final PhysicsBridge bodies;
    private final Thread owner = Thread.currentThread();
    private final Settings settings;
    private static final ContactEvent3D.Type[] CONTACT_TYPES=ContactEvent3D.Type.values();

    private final LinkedHashMap<Integer, RigidBody3D> bodyMap = new LinkedHashMap<>();
    private final HashMap<Integer, RigidBody3D> retiredBodies = new HashMap<>();
    private final ArrayList<DistanceJoint3D> joints = new ArrayList<>();
    private final ArrayList<Consumer<ContactEvent3D>> contactListeners = new ArrayList<>();
    private final ArrayList<Consumer<PhysicsWorld3D>> beforeStepListeners = new ArrayList<>();
    private final ArrayList<Consumer<PhysicsWorld3D>> afterStepListeners = new ArrayList<>();
    private List<Consumer<PhysicsWorld3D>> beforeStepSnapshot = List.of(), afterStepSnapshot = List.of();
    private final ArrayDeque<RawContact> pendingContacts = new ArrayDeque<>();

    private boolean closed, advancing, inNative;
    private double accumulator, droppedTime;
    private long stepCount;

    public PhysicsWorld3D() {this(Settings.defaults(), new CollisionLayers3D());}

    public PhysicsWorld3D(Settings settings) {this(settings, new CollisionLayers3D());}

    public PhysicsWorld3D(Settings settings, CollisionLayers3D layers) {
        this.settings = Objects.requireNonNull(settings);
        Objects.requireNonNull(layers);
        int[] collisions=new int[256];
        for(int a=0;a<16;a++)for(int b=0;b<16;b++)collisions[a*16+b]=layers.collides(a,b)?1:0;
        bodies=new PhysicsBridge(settings,collisions,(type,a,b,sa,sb,x,y,z,depth)->
            pendingContacts.add(new RawContact(CONTACT_TYPES[type],a,b,sa,sb,new Vector3f(x,y,z),depth)));
    }

    void check() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("PhysicsWorld3D must be accessed on its creating thread");
        if (closed) throw new IllegalStateException("Physics world is closed");
        if (inNative) throw new IllegalStateException("Cannot access the world during a native physics update");
    }

    public float getFixedTimeStep() {return settings.fixedTimeStep;}

    public boolean isClosed() {return closed;}

    public long getStepCount() {return stepCount;}

    public double getDroppedTime() {return droppedTime;}

    public float getInterpolationAlpha() {return (float) (accumulator / settings.fixedTimeStep);}

    public List<RigidBody3D> getBodies() {
        check();
        return List.copyOf(bodyMap.values());
    }

    public int getBodyCount() {
        check();
        return bodyMap.size();
    }

    public Vector3f getGravity() {
        check();
        return bodies.getGravity();
    }

    public PhysicsWorld3D setGravity(Vector3f gravity) {
        check();
        bodies.setGravity(PhysicsMath3D.check(gravity));
        return this;
    }

    public void addContactListener(Consumer<ContactEvent3D> listener) {
        check();
        contactListeners.add(Objects.requireNonNull(listener));
    }

    public void removeContactListener(Consumer<ContactEvent3D> listener) {
        check();
        contactListeners.remove(listener);
    }

    public void addBeforeStepListener(Consumer<PhysicsWorld3D> listener) {
        check();
        beforeStepListeners.add(Objects.requireNonNull(listener));
        beforeStepSnapshot = null;
    }

    public void removeBeforeStepListener(Consumer<PhysicsWorld3D> listener) {
        check();
        beforeStepListeners.remove(listener);
        beforeStepSnapshot = null;
    }

    public void addAfterStepListener(Consumer<PhysicsWorld3D> listener) {
        check();
        afterStepListeners.add(Objects.requireNonNull(listener));
        afterStepSnapshot = null;
    }

    public void removeAfterStepListener(Consumer<PhysicsWorld3D> listener) {
        check();
        afterStepListeners.remove(listener);
        afterStepSnapshot = null;
    }

    public RigidBody3D createBody(BodySettings3D settings) {
        check();
        Objects.requireNonNull(settings);
        if (bodyMap.size() >= this.settings.maxBodies) throw new IllegalStateException("Physics body capacity reached");
        int id=bodies.create(settings);
        try {
            RigidBody3D body=new RigidBody3D(this,id,settings);
            bodyMap.put(id,body);return body;
        } catch(RuntimeException | Error failure){bodies.destroyBody(id);throw failure;}
    }

    public void destroyBody(RigidBody3D body) {
        check();
        requireOwn(body);
        if (body.destroyed) return;
        for (DistanceJoint3D joint : List.copyOf(joints)) if (joint.a == body || joint.b == body) destroyJoint(joint);
        bodies.destroyBody(body.id);
        bodyMap.remove(body.id);
        retiredBodies.put(body.id, body);
        body.invalidate();
    }

    private void requireOwn(RigidBody3D body) {
        if (body == null || body.world != this) throw new IllegalArgumentException("Body belongs to another world");
    }

    public int update(float elapsedSeconds) {
        check();
        PhysicsMath3D.nonnegative(elapsedSeconds, "elapsed time");
        if (advancing) throw new IllegalStateException("Recursive physics stepping is not allowed");
        accumulator += elapsedSeconds;
        int steps = 0;
        advancing = true;
        try {
            double tolerance = settings.fixedTimeStep * 1e-6;
            while (accumulator + tolerance >= settings.fixedTimeStep && steps < settings.maxSubSteps) {
                integrate(true);
                steps++;
            }
            if (accumulator >= settings.fixedTimeStep) {
                double remainder = accumulator % settings.fixedTimeStep;
                droppedTime += accumulator - remainder;
                accumulator = remainder;
            }
            syncModels(false);
            return steps;
        } finally {advancing = false;}
    }

    public void step() {
        check();
        if (advancing) throw new IllegalStateException("Recursive physics stepping is not allowed");
        advancing = true;
        try {integrate(false);} finally {advancing = false;}
    }

    private void integrate(boolean consumeTime) {
        List<Consumer<PhysicsWorld3D>> before = beforeStepSnapshot;
        if (before == null) beforeStepSnapshot = before = List.copyOf(beforeStepListeners);
        for (int i = 0; i < before.size(); i++) before.get(i).accept(this);
        for (RigidBody3D body : bodyMap.values()) body.remember();
        inNative = true;
        // Jolt's single-threaded WASM Step binding returns void. Allocation is
        // checked at creation; native update error flags are not exposed by it.
        try {bodies.step(settings.fixedTimeStep);} finally {inNative = false;}

        if (consumeTime) accumulator = Math.max(0, accumulator - settings.fixedTimeStep);
        stepCount++;
        for (RigidBody3D body : bodyMap.values()) body.capture();
        syncModels(false);
        List<Consumer<PhysicsWorld3D>> after = afterStepSnapshot;
        if (after == null) afterStepSnapshot = after = List.copyOf(afterStepListeners);
        for (int i = 0; i < after.size(); i++) after.get(i).accept(this);
        dispatchContacts();
    }

    private void dispatchContacts() {
        ArrayList<ContactEvent3D> events = new ArrayList<>();
        RawContact raw;
        while ((raw = pendingContacts.poll()) != null) {
            RigidBody3D a = bodyMap.getOrDefault(raw.a, retiredBodies.get(raw.a));
            RigidBody3D b = bodyMap.getOrDefault(raw.b, retiredBodies.get(raw.b));
            if (a != null && b != null)
                events.add(new ContactEvent3D(raw.type, a, b, raw.subA, raw.subB, raw.normal, raw.penetration));
        }
        retiredBodies.clear();

        List<Consumer<ContactEvent3D>> listeners = List.copyOf(contactListeners);
        for (ContactEvent3D event : events) for (Consumer<ContactEvent3D> listener : listeners) listener.accept(event);
    }

    public void syncModels(boolean interpolate) {
        check();
        float alpha = interpolate ? getInterpolationAlpha() : 1f;
        for (RigidBody3D body : bodyMap.values()) body.sync(alpha);
    }

    public void optimizeBroadPhase() {
        check();
        bodies.optimize();
    }

    public PhysicsRayHit3D raycast(Rayf ray, float maxDistance) {
        return raycast(ray, maxDistance, null);
    }

    public PhysicsRayHit3D raycast(Rayf ray, float maxDistance, RigidBody3D ignoredBody) {
        check();
        if (ignoredBody != null) {
            requireOwn(ignoredBody);
            ignoredBody.check();
        }
        Objects.requireNonNull(ray);
        PhysicsMath3D.positive(maxDistance, "ray distance");
        Vector3f direction = PhysicsMath3D.check(new Vector3f(ray.dX, ray.dY, ray.dZ));
        double length = Math.sqrt((double) ray.dX * ray.dX + (double) ray.dY * ray.dY + (double) ray.dZ * ray.dZ);
        if (length == 0) throw new IllegalArgumentException("Ray direction must be nonzero");
        direction.set((float) (ray.dX / length), (float) (ray.dY / length), (float) (ray.dZ / length)).mul(maxDistance);
        PhysicsMath3D.check(new Vector3f(ray.oX,ray.oY,ray.oZ));
        PhysicsBridge.Hit hit=bodies.raycast(ray.oX,ray.oY,ray.oZ,direction.x,direction.y,direction.z,ignoredBody);
        return hit==null?null:new PhysicsRayHit3D(bodyMap.get(hit.getId()),hit.getF()*maxDistance,
            new Vector3f(hit.getX(),hit.getY(),hit.getZ()),new Vector3f(hit.getNx(),hit.getNy(),hit.getNz()));
    }

    public DistanceJoint3D createDistanceJoint(RigidBody3D a, RigidBody3D b, Vector3f anchorA, Vector3f anchorB, float minDistance, float maxDistance) {
        check();
        requireOwn(a);
        requireOwn(b);
        a.check();
        b.check();
        if (a == b || a.motion != MotionType3D.DYNAMIC && b.motion != MotionType3D.DYNAMIC)
            throw new IllegalArgumentException("Joint requires distinct bodies and at least one dynamic body");
        PhysicsMath3D.nonnegative(minDistance, "minimum distance");
        PhysicsMath3D.nonnegative(maxDistance, "maximum distance");
        if (maxDistance < minDistance)
            throw new IllegalArgumentException("Maximum distance must be at least the minimum");
        PhysicsMath3D.check(anchorA);PhysicsMath3D.check(anchorB);
        JSObject nativeJoint=bodies.joint(a.id,b.id,anchorA,anchorB,minDistance,maxDistance);
        DistanceJoint3D joint=new DistanceJoint3D(this,a,b,nativeJoint);
        joints.add(joint);return joint;
    }

    void destroyJoint(DistanceJoint3D joint) {
        check();
        if (joint.world != this) throw new IllegalArgumentException("Joint belongs to another world");
        if (joint.destroyed) return;
        bodies.destroyJoint(joint.constraint);
        joints.remove(joint);
        joint.destroyed = true;
    }

    public void dispose() {close();}

    @Override
    public void close() {
        if (closed) return;
        check();
        if (advancing) throw new IllegalStateException("Cannot close a world inside a step callback");
        bodies.detachContacts();
        for (DistanceJoint3D joint : List.copyOf(joints)) destroyJoint(joint);
        for (RigidBody3D body : List.copyOf(bodyMap.values())) destroyBody(body);
        pendingContacts.clear();
        retiredBodies.clear();
        contactListeners.clear();
        beforeStepListeners.clear();
        afterStepListeners.clear();
        beforeStepSnapshot = afterStepSnapshot = List.of();
        releaseResources();
        closed = true;
    }

    private void releaseResources(){bodies.close();}

    public record Settings(float fixedTimeStep, int maxSubSteps, int maxBodies,
            int maxBodyPairs, int maxContacts, int workerThreads) {

        public Settings {
            PhysicsMath3D.positive(fixedTimeStep, "fixed time step");
            if (maxSubSteps < 1 || maxBodies < 1 || maxBodyPairs < 1 || maxContacts < 1 || workerThreads < 0)
                throw new IllegalArgumentException("Capacities must be positive and worker count nonnegative");
        }

        public static Settings defaults() {return new Settings(1f / 60f, 8, 4096, 65536, 20480, 0);}
    }

    private record RawContact(ContactEvent3D.Type type, int a, int b, int subA, int subB, Vector3f normal, float penetration) {
    }
}
