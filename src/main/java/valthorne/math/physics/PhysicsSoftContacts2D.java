package valthorne.math.physics;

import com.github.stephengold.joltjni.Vec3;
import org.joml.Vector2f;

/**
 * Owner-thread continuous planar disk contacts against deforming perimeter edges.
 * Jolt soft contacts sample vertices and cannot prevent a disk crossing the gap
 * between vertices. This supplemental pass checks the complete boundary after
 * native integration, corrects penetration and distributes contact impulses to
 * the two native endpoints using their barycentric weights and inverse masses.
 * Frozen meshes and pinned vertices therefore provide genuine rigid support.
 *
 * <p>Native broadphase queries prune pairs. Swept bounds include the previous
 * mesh and maximum disk travel, so a disk that traverses a thin mesh is still
 * considered. Query storage grows only when a denser local contact set requires
 * it; rigid-only worlds never allocate this subsystem. Native vertex contacts
 * still handle other rigid shapes. All scratch belongs to the world's thread.</p>
 */
final class PhysicsSoftContacts2D {
    /**
     * Meter-scale skin avoids repeatedly correcting floating-point tangency.
     */
    private static final float SKIN = 0.0001f;
    /**
     * Matches Jolt's default one-meter-per-second restitution threshold.
     */
    private static final float MIN_RESTITUTION_SPEED = 1;
    private final PhysicsWorld2D world; // Native owner and scratch resources.
    private final Vector2f velocity = new Vector2f(); // Reused rigid linear velocity destination.
    private RigidBody2D[] candidates = new RigidBody2D[32]; // Local broadphase output, grown only on overflow.
    private int edge; // First endpoint of the selected perimeter contact.
    private float weight; // Second endpoint barycentric contact weight.
    private float normalX; // Outward contact normal horizontal component.
    private float normalY; // Outward contact normal vertical component.
    private float penetration; // Required relative separation in meters.

    /**
     * Creates only retained owner-thread contact scratch.
     * @param world owning native simulation
     */
    PhysicsSoftContacts2D(PhysicsWorld2D world) {
        this.world = world;
    }

    /**
     * Resolves complete disk boundaries after native mesh and rigid pose capture.
     * @param meshes dense live soft-body storage
     * @param meshCount live soft-body prefix
     * @param bodies dense live rigid-body storage
     * @param bodyCount live rigid-body prefix
     */
    void solve(SoftBody2D[] meshes, int meshCount, RigidBody2D[] bodies, int bodyCount) {
        float travel = 0;
        boolean disks = false;
        for (int i = 0; i < bodyCount; i++) {
            RigidBody2D body = bodies[i];
            if (body.shape.circleRadius() == 0 || body.isSensor()) continue;
            disks = true;
            travel = Math.max(travel, Math.max(Math.abs(body.getX() - body.previousX), Math.abs(body.getY() - body.previousY)));
        }
        if (!disks) return;
        for (int i = 0; i < meshCount; i++) {
            SoftBody2D mesh = meshes[i];
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            for (int j = 0; j < mesh.current.length - 2; j += 2) {
                minX = Math.min(minX, Math.min(mesh.current[j], mesh.previous[j]));
                minY = Math.min(minY, Math.min(mesh.current[j + 1], mesh.previous[j + 1]));
                maxX = Math.max(maxX, Math.max(mesh.current[j], mesh.previous[j]));
                maxY = Math.max(maxY, Math.max(mesh.current[j + 1], mesh.previous[j + 1]));
            }
            int count = world.queryBounds(minX - travel - SKIN, minY - travel - SKIN, maxX + travel + SKIN, maxY + travel + SKIN, candidates);
            if (count > candidates.length) {
                candidates = new RigidBody2D[Math.max(count, candidates.length * 2)];
                count = world.queryBounds(minX - travel - SKIN, minY - travel - SKIN, maxX + travel + SKIN, maxY + travel + SKIN, candidates);
            }
            for (int j = 0; j < count; j++) {
                RigidBody2D body = candidates[j];
                candidates[j] = null;
                float radius = body.shape.circleRadius();
                if (radius == 0 || body.isSensor()) continue;
                if (body.getMotionType() != MotionType2D.DYNAMIC && mesh.getMotionType() == MotionType2D.STATIC) continue;
                if (contact(mesh, body, radius + SKIN)) resolve(mesh, body, radius);
            }
        }
    }

    /**
     * Finds a closest boundary contact or the first swept edge crossing.
     * Winding supplies the outward normal; parity handles concave deformation.
     * @param mesh native deformable boundary
     * @param body rigid disk with captured previous and current centers
     * @param radius effective disk radius including the numerical skin
     * @return whether a separating contact was found
     */
    private boolean contact(SoftBody2D mesh, RigidBody2D body, float radius) {
        float x = body.getX();
        float y = body.getY();
        float nearest = Float.POSITIVE_INFINITY;
        int nearestEdge = 0;
        float nearestWeight = 0;
        float nearestX = 0;
        float nearestY = 0;
        float firstTime = Float.POSITIVE_INFINITY;
        boolean inside = false;
        int count = mesh.vertices.length - 1;
        double area = 0;
        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            area += (double) mesh.current[i * 2] * mesh.current[next * 2 + 1] - (double) mesh.current[next * 2] * mesh.current[i * 2 + 1];
        }
        float winding = area >= 0 ? 1 : -1;
        for (int i = 0; i < count; i++) {
            int a = i * 2;
            int b = ((i + 1) % count) * 2;
            float ax = mesh.current[a];
            float ay = mesh.current[a + 1];
            float dx = mesh.current[b] - ax;
            float dy = mesh.current[b + 1] - ay;
            float squared = dx * dx + dy * dy;
            if (squared < 1e-12f) continue;
            float t = Math.clamp(((x - ax) * dx + (y - ay) * dy) / squared, 0, 1);
            float pointX = ax + dx * t;
            float pointY = ay + dy * t;
            float distance = (x - pointX) * (x - pointX) + (y - pointY) * (y - pointY);
            if (distance < nearest) {
                nearest = distance;
                nearestEdge = i;
                nearestWeight = t;
                nearestX = pointX;
                nearestY = pointY;
            }
            if ((ay > y) != (mesh.current[b + 1] > y) && x < ax + (y - ay) * dx / dy) inside = !inside;
            float inverseLength = 1 / (float) Math.sqrt(squared);
            float nx = dy * inverseLength * winding;
            float ny = -dx * inverseLength * winding;
            float start = (body.previousX - mesh.previous[a]) * nx + (body.previousY - mesh.previous[a + 1]) * ny;
            float end = (x - ax) * nx + (y - ay) * ny;
            if (start < radius || end >= radius) continue;
            float time = (start - radius) / (start - end);
            if (time >= firstTime) continue;
            float hitX = body.previousX + (x - body.previousX) * time;
            float hitY = body.previousY + (y - body.previousY) * time;
            float hitAX = mesh.previous[a] + (ax - mesh.previous[a]) * time;
            float hitAY = mesh.previous[a + 1] + (ay - mesh.previous[a + 1]) * time;
            float hitDX = mesh.previous[b] + (mesh.current[b] - mesh.previous[b]) * time - hitAX;
            float hitDY = mesh.previous[b + 1] + (mesh.current[b + 1] - mesh.previous[b + 1]) * time - hitAY;
            float length = hitDX * hitDX + hitDY * hitDY;
            if (length < 1e-12f) continue;
            float barycentric = ((hitX - hitAX) * hitDX + (hitY - hitAY) * hitDY) / length;
            if (barycentric < 0 || barycentric > 1) continue;
            firstTime = time;
            edge = i;
            weight = barycentric;
            normalX = nx;
            normalY = ny;
            penetration = radius - end;
        }
        // End caps close the swept gaps around vertices when a disk grazes a corner.
        for (int i = 0; i < count; i++) {
            int offset = i * 2;
            float sx = body.previousX - mesh.previous[offset];
            float sy = body.previousY - mesh.previous[offset + 1];
            float dx = x - mesh.current[offset] - sx;
            float dy = y - mesh.current[offset + 1] - sy;
            float a = dx * dx + dy * dy;
            float b = sx * dx + sy * dy;
            float c = sx * sx + sy * sy - radius * radius;
            if (a < 1e-12f || c < 0 || b >= 0) continue;
            float discriminant = b * b - a * c;
            if (discriminant < 0) continue;
            float time = (-b - (float) Math.sqrt(discriminant)) / a;
            if (time < 0 || time > 1 || time >= firstTime) continue;
            float nx = (sx + dx * time) / radius;
            float ny = (sy + dy * time) / radius;
            float depth = radius - (x - mesh.current[offset]) * nx - (y - mesh.current[offset + 1]) * ny;
            if (depth <= 0) continue;
            firstTime = time;
            edge = i;
            weight = 0;
            normalX = nx;
            normalY = ny;
            penetration = depth;
        }
        if (firstTime != Float.POSITIVE_INFINITY) return true;
        if (!inside && nearest >= radius * radius) return false;
        edge = nearestEdge;
        weight = nearestWeight;
        float distance = (float) Math.sqrt(nearest);
        if (distance > 1e-6f) {
            float sign = inside ? -1 : 1;
            normalX = (x - nearestX) / distance * sign;
            normalY = (y - nearestY) / distance * sign;
        } else {
            int next = (edge + 1) % count;
            float dx = mesh.current[next * 2] - mesh.current[edge * 2];
            float dy = mesh.current[next * 2 + 1] - mesh.current[edge * 2 + 1];
            float inverse = winding / (float) Math.sqrt(dx * dx + dy * dy);
            normalX = dy * inverse;
            normalY = -dx * inverse;
        }
        penetration = inside ? radius + distance : radius - distance;
        return true;
    }

    /**
     * Shares separation, restitution and Coulomb friction with native endpoints.
     * Circle normal contacts act through its center; tangential contacts include
     * the disk's rotational effective mass and transfer angular impulse.
     * @param mesh native mesh with a selected edge
     * @param body contacting rigid disk
     * @param radius exact disk radius in meters
     */
    private void resolve(SoftBody2D mesh, RigidBody2D body, float radius) {
        int next = (edge + 1) % (mesh.vertices.length - 1);
        float wa = 1 - weight;
        float wb = weight;
        float ma = mesh.vertices[edge].getInvMass();
        float mb = mesh.vertices[next].getInvMass();
        float rigidMass = body.getMotionType() == MotionType2D.DYNAMIC ? body.inverseMass : 0;
        float inverse = rigidMass + ma * wa * wa + mb * wb * wb;
        if (inverse == 0) return;
        body.getLinearVelocity(velocity);
        float relativeX = velocity.x;
        float relativeY = velocity.y;
        if (ma != 0) {
            Vec3 va = mesh.vertices[edge].getVelocity();
            relativeX -= va.getX() * wa;
            relativeY -= va.getY() * wa;
        }
        if (mb != 0) {
            Vec3 vb = mesh.vertices[next].getVelocity();
            relativeX -= vb.getX() * wb;
            relativeY -= vb.getY() * wb;
        }
        float speed = relativeX * normalX + relativeY * normalY;
        float restitution = speed < -MIN_RESTITUTION_SPEED ? Math.max(world.bodies.getRestitution(body.id), world.bodies.getRestitution(mesh.id)) : 0;
        float normalImpulse = Math.max(0, -(1 + restitution) * speed / inverse);
        float tangentX = -normalY;
        float tangentY = normalX;
        float angular = body.getMotionType() == MotionType2D.STATIC ? 0 : body.getAngularVelocity();
        float tangentSpeed = relativeX * tangentX + relativeY * tangentY - angular * radius;
        float tangentMass = inverse + (body.isFixedRotation() ? 0 : 2 * rigidMass);
        float friction = (float) Math.sqrt(world.bodies.getFriction(body.id) * world.bodies.getFriction(mesh.id));
        float tangentImpulse = Math.clamp(-tangentSpeed / tangentMass, -friction * normalImpulse, friction * normalImpulse);
        float impulseX = normalX * normalImpulse + tangentX * tangentImpulse;
        float impulseY = normalY * normalImpulse + tangentY * tangentImpulse;
        float correction = penetration / inverse;
        float contactX = body.getX() - normalX * radius;
        float contactY = body.getY() - normalY * radius;
        if (rigidMass != 0) {
            body.correctPosition(normalX * correction * rigidMass, normalY * correction * rigidMass);
            body.addImpulseAt(impulseX, impulseY, contactX, contactY);
        }
        if (ma != 0) mesh.correctContact(edge, -normalX * correction * ma * wa, -normalY * correction * ma * wa, -impulseX * ma * wa, -impulseY * ma * wa);
        if (mb != 0) mesh.correctContact(next, -normalX * correction * mb * wb, -normalY * correction * mb * wb, -impulseX * mb * wb, -impulseY * mb * wb);
    }
}
