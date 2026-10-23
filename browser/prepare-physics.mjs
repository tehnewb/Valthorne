import { readFile, writeFile, mkdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const directory = "valthorne/math/physics";
const output = path.join(root, "browser/build/generated/platform", directory);
await mkdir(output, { recursive: true });

// Adapt only the native boundary. Timing, ownership, geometry, interpolation,
// contact buffering and planar soft/rigid collision algorithms stay shared.
function replace(source, before, after) {
    if (!source.includes(before)) throw new Error("Review browser physics adaptation: " + before);
    return source.replace(before, after);
}
function bounds(source, signature) {
    const start = source.indexOf(signature);
    if (start < 0) throw new Error("Missing physics method: " + signature);
    const open = source.indexOf("{", start);
    const masked = source.replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'/g, (value) =>
        value.replace(/[^\n]/g, " "),
    );
    let depth = 1,
        end = open + 1;
    while (end < masked.length && depth) {
        if (masked[end] === "{") depth++;
        else if (masked[end] === "}") depth--;
        end++;
    }
    if (depth) throw new Error("Unbalanced physics method: " + signature);
    return { start, open, end };
}
function body(source, signature, replacement) {
    const { open, end } = bounds(source, signature);
    return source.slice(0, open + 1) + "\n" + replacement + "\n    }" + source.slice(end);
}
function removeMethod(source, signature) {
    const { start, end } = bounds(source, signature);
    const doc = source.lastIndexOf("    /**", start);
    return source.slice(0, doc) + source.slice(end + 1);
}
function imports(source, names) {
    return source.replace(
        "package valthorne.math.physics;",
        "package valthorne.math.physics;\n\n" +
            names.map((name) => "import valthorne.browser.physics." + name + ";").join("\n"),
    );
}
function removeField(source, name) {
    const position = source.indexOf(" " + name + "; //");
    if (position < 0) throw new Error("Missing physics field: " + name);
    const start = source.lastIndexOf("\n", position) + 1;
    return source.slice(0, start) + source.slice(source.indexOf("\n", position) + 1);
}
for (const name of [
    "JoltRuntime",
    "CollisionShape2D",
    "PhysicsWorld2D",
    "PhysicsWorldSettings2D",
    "RigidBody2D",
    "SoftBody2D",
    "PhysicsSoftContacts2D",
    "PhysicsContacts2D",
    "PhysicsQueries2D",
    "PhysicsQueryCollector2D",
    "PhysicsJoints2D",
    "PhysicsJoint2D",
]) {
    let source = (await readFile(path.join(root, "src/main/java", directory, name + ".java"), "utf8")).replaceAll(
        "\r\n",
        "\n",
    );
    source = source.replace(/^import com\.github\.stephengold\.joltjni\.[^;]+;\n/gm, "");
    if (name === "JoltRuntime") {
        source = `package valthorne.math.physics;

import valthorne.browser.physics.BrowserPhysics;

/**
 * Initializes optional browser Jolt without loading a JVM native library.
 * Loading suspends the calling Java coroutine; failures propagate to that caller.
 * Each world owns an independent native module because Jolt's browser interface
 * owns global factory/type registration. Worlds may close in any order, and
 * application teardown releases remaining worlds. No graphics context is needed.
 */
public final class JoltRuntime {
    /**
     * Prevents construction of the shared browser physics entry point.
     */
    private JoltRuntime() {
    }

    /**
     * Loads the optional native physics factory for the active browser session.
     */
    public static void initialize() {
        /*
         * The host coalesces concurrent module requests while Java's continuation
         * preserves the calling world's thread identity across the async import.
         */
        BrowserPhysics.initialize();
    }
}
`;
    }
    if (name === "CollisionShape2D") {
        source = imports(source, ["BrowserPhysics"]);
        source = source
            .replace("import org.lwjgl.system.MemoryUtil;", "import org.teavm.jso.JSObject;")
            .replace("import java.nio.FloatBuffer;\n", "");
        source = body(
            source,
            "Shape createNative()",
            "        return BrowserPhysics.shape(circle, width, height, vertices);",
        );
        source = replace(source, "Shape createNative()", "JSObject createNative()");
        source = removeMethod(source, "private Shape createPolygon()");
        source = replace(
            source,
            "Allocates a native shape for one body. The caller must close its reference\n     * after passing it to body creation; Jolt retains the body's own reference.",
            "Borrows an immutable descriptor for synchronous native body creation.\n     * The native bridge copies geometry and releases temporary settings itself.",
        );
        source = source.replace("@return newly owned native shape", "@return borrowed browser collision descriptor");
    }
    if (name === "PhysicsWorld2D") {
        source = imports(source, ["BrowserPhysics", "BrowserPhysicsWorld", "PhysicsVector"]);
        source = source
            .replace(/^    private final JoltPhysicsObject\[\].*\n/m, "")
            .replace(/^    private int resourceCount;.*\n/m, "");
        for (const field of ["poses", "allocator", "jobs"]) source = removeField(source, field);
        source = source
            .replaceAll("PhysicsSystem system", "BrowserPhysicsWorld system")
            .replaceAll("BodyInterface bodies", "BrowserPhysicsWorld bodies");
        source = source.replace(
            /final (RVec3|Quat|Vec3) (\w+) = new \1\(\);/g,
            "final PhysicsVector $2 = BrowserPhysics.vector();",
        );
        source = removeMethod(source, "private <T extends JoltPhysicsObject> T own(");
        const constructor = bounds(source, "public PhysicsWorld2D(PhysicsWorldSettings2D settings)");
        const init = source.indexOf("        JoltRuntime.initialize();", constructor.open);
        source =
            source.slice(0, init) +
            `        JoltRuntime.initialize();
        try {
            system = BrowserPhysics.createWorld(settings.maxBodies, settings.maxBodyPairs, settings.maxContacts);
            bodies = system;
            system.setGravity(gravityX, gravityY, 0);
        } catch (RuntimeException | Error failure) {
            try {
                releaseResources();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }` +
            source.slice(constructor.end);
        source = replace(source, "threaded = settings.workerThreads != 0;", "threaded = false;");
        const first = source.indexOf("        int id = Jolt.cInvalidBodyId;");
        const end = source.indexOf("        if (body.geometry != null) geometryCount++;", first);
        if (first < 0 || end < 0) throw new Error("Review rigid body creation");
        source =
            source.slice(0, first) +
            `        int id = -1;
        RigidBody2D body;
        try {
            float[] values = {settings.motion.ordinal(), settings.positionInPixels ? worldX(settings.x) : settings.x,
                    settings.positionInPixels ? worldY(settings.y) : settings.y, settings.angle, settings.fixedRotation ? 1 : 0,
                    settings.velocityX, settings.velocityY, settings.angularVelocity, settings.friction, settings.restitution,
                    settings.linearDamping, settings.angularDamping, settings.gravityFactor, settings.sleeping ? 1 : 0,
                    settings.sensor ? 1 : 0, settings.continuous ? 1 : 0, settings.mass};
            id = bodies.createBody(settings.shape.createNative(), values);
            body = new RigidBody2D(this, id, bodyCount, settings);
            if (body.geometry != null) body.geometry.sync(body, false);
        } catch (RuntimeException | Error failure) {
            if (id != -1) {
                bodies.removeBody(id);
                bodies.destroyBody(id);
            }
            throw failure;
        }
` +
            source.slice(end);
        source = source.replace(/^        if \(poses != null\) poses.invalidate\(\);\n/gm, "");
        const batch = source.indexOf("            if (capture && movingBodyCount >= PhysicsPoses2D.MIN_BODIES)");
        const captureEnd = source.indexOf("            capturePreviousActivity = active;", batch);
        if (batch < 0 || captureEnd < 0) throw new Error("Review physics capture");
        source =
            source.slice(0, batch) +
            `            if (movingBodyCount != 0) {
                for (int i = 0; i < bodyCount; i++) handles[i].captureStep(capture);
            }
` +
            source.slice(captureEnd);
        source = replace(
            source,
            "            int errors = system.update(fixedTimeStep, collisionSteps, allocator, jobs);",
            "            system.update(fixedTimeStep, collisionSteps);",
        );
        source = replace(source, "system.getNumActiveBodies(EBodyType.RigidBody)", "system.getNumActiveBodies()");
        source = replace(
            source,
            `            if (errors != 0)
                throw new IllegalStateException("Jolt simulation capacity exhausted (error flags " + errors + ")");`,
            "",
        );
        const release = bounds(source, "private void releaseResources()");
        let releaseBody = source.slice(release.open + 1, release.end - 1);
        const poseStart = releaseBody.indexOf("        if (poses != null)");
        const jointStart = releaseBody.indexOf("        if (joints != null)", poseStart);
        if (poseStart < 0 || jointStart < 0) throw new Error("Review physics release");
        releaseBody = releaseBody.slice(0, poseStart) + releaseBody.slice(jointStart);
        const forget = releaseBody.indexOf("        if (system != null)");
        const thrown = releaseBody.indexOf("        if (failure instanceof RuntimeException", forget);
        releaseBody =
            releaseBody.slice(0, forget) +
            `        if (system != null) {
            try {
                system.close();
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure == null) failure = cleanupFailure;
                else failure.addSuppressed(cleanupFailure);
            }
            system = null;
            bodies = null;
        }
` +
            releaseBody.slice(thrown);
        source = source.slice(0, release.open + 1) + releaseBody + source.slice(release.end - 1);
        // The desktop JNI batch buffers are unnecessary across the JS/WASM boundary.
        while (source.includes("        if (poses != null) {")) {
            const block = bounds(source, "        if (poses != null) {");
            source = source.slice(0, block.start) + source.slice(block.end + 1);
        }
        source = source.replace(
            / \* Worlds with at least 128 moving bodies[\s\S]*? \* releases them\./,
            " * Browser pose reads reuse numeric scratch without native buffer copies.",
        );
        source = source.replace(
            / \* Point\/bounds queries allocate no steady Java objects\. Jolt JNI 6\.0\.0 requires[\s\S]*? \* contact queue uses/,
            " * Point, bounds and ray queries reuse native filters and collectors. The\n * contact queue uses",
        );
        source = source.replace(
            "Optional native workers complete before stepping returns.",
            "Browser physics runs synchronously with no shared-memory workers.",
        );
    }
    if (name === "PhysicsWorld2D") {
        source = source.replace(
            "reusable arena with malloc fallback.",
            "fixed ten-megabyte arena owned by Jolt's browser interface.",
        );
        source = source.replace(
            "If native solver capacities are exhausted, the world is\n * marked failed because Jolt may already have advanced an incomplete step;\n * inspect or dispose it, then create a suitably sized replacement.",
            "The upstream browser Step binding does not expose native pair/contact\n * capacity error flags. Size these capacities conservatively before advancing;\n * native exceptions fail the world because its state may already have advanced.",
        );
    }
    if (name === "PhysicsWorldSettings2D") {
        source = source.replace(
            "a four-megabyte reusable native scratch arena, and no worker threads. Increase",
            "a synchronous WASM solver with its fixed ten-megabyte scratch arena. Increase",
        );
        source = source.replace(
            "pair/contact capacities for dense scenes; exhausted solver capacities make\n * the world fail explicitly rather than silently accept incomplete contacts.",
            "pair/contact capacities for dense scenes. The upstream browser Step binding\n * does not expose candidate-pair or contact-capacity error flags; allocate\n * sufficient capacity before advancing dense scenes.",
        );
        source = source.replace(
            "Optional subsystems default to",
            "Browser workerThreads and scratchBytes values are retained for source\n * compatibility; the native browser job system and arena are fixed.\n * Optional subsystems default to",
        );
    }
    if (name === "RigidBody2D") {
        source = replace(
            source,
            `world.bodies.setMotionType(id, switch (motion) {
            case STATIC -> EMotionType.Static;
            case KINEMATIC -> EMotionType.Kinematic;
            case DYNAMIC -> EMotionType.Dynamic;
        },`,
            "world.bodies.setMotionType(id, motion.ordinal(),",
        );
    }
    if (name === "PhysicsSoftContacts2D") {
        source = imports(source, ["PhysicsVector"]);
        source = source.replaceAll("Vec3 ", "PhysicsVector ");
    }
    if (name === "SoftBody2D") {
        source = imports(source, ["PhysicsSoftMesh", "PhysicsVector", "PhysicsVertex"]);
        source = source
            .replace("import org.lwjgl.system.MemoryUtil;\n", "")
            .replace("import java.nio.FloatBuffer;\n", "");
        for (const field of ["nativeBody", "properties", "edges"]) source = removeField(source, field);
        source = source
            .replaceAll("SoftBodyVertex", "PhysicsVertex")
            .replace("SoftBodySharedSettings mesh", "PhysicsSoftMesh mesh")
            .replace("FloatBuffer positions", "Float32Array positions");
        source = source.replace(
            "import java.util.Objects;",
            "import java.util.Objects;\nimport org.teavm.jso.typedarrays.Float32Array;",
        );
        const first = source.indexOf("        int createdId = -1;");
        const end = bounds(source, "SoftBody2D(PhysicsWorld2D world, Shape shape, int index)").end;
        source =
            source.slice(0, first) +
            `        Vector2f[] rotated = shape.getRotatedPoints();
        double centerX = 0;
        double centerY = 0;
        for (int i = 0; i < count; i++) {
            current[i * 2] = world.worldX(rotated[i].x);
            current[i * 2 + 1] = world.worldY(rotated[i].y);
            centerX += current[i * 2];
            centerY += current[i * 2 + 1];
        }
        current[count * 2] = (float) (centerX / count);
        current[count * 2 + 1] = (float) (centerY / count);
        mesh = world.bodies.createSoftBody(current, originX, originY, DEFAULT_COMPLIANCE, DEFAULT_ITERATIONS, world.toMeters(1));
        id = mesh.getId();
        try {
            vertices = new PhysicsVertex[count + 1];
            for (int i = 0; i < vertices.length; i++) vertices[i] = mesh.getVertex(i);
            positions = Float32Array.create(vertices.length * 3);
            System.arraycopy(current, 0, previous, 0, current.length);
            geometry.setRotation(0);
            sync(false);
        } catch (RuntimeException | Error failure) {
            try {
                world.bodies.removeBody(id);
                world.bodies.destroyBody(id);
            } finally {
                release();
            }
            throw failure;
        }
    }` +
            source.slice(end);
        source = source.replace(
            "for (Edge edge : edges) edge.setCompliance(compliance);",
            "mesh.setCompliance(compliance);",
        );
        source = source.replaceAll("properties.set", "mesh.set").replaceAll("nativeBody.set", "mesh.set");
        source = replace(
            source,
            "        positions.clear();\n        properties.putVertexLocations(world.positionScratch, positions);",
            "        mesh.capture(positions);",
        );
        source = source
            .replace(/positions.get\((i \* 3(?: \+ [12])?)\)/g, "positions.get($1)")
            .replaceAll("Vec3 ", "PhysicsVector ");
        source = source
            .replace("        if (positions != null) MemoryUtil.memFree(positions);\n", "")
            .replace("SoftBodySharedSettings owned", "PhysicsSoftMesh owned");
        source = source.replace(
            "One packed native read captures all mesh positions per step.",
            "Cached native vertex views capture mesh positions into one reusable array.",
        );
    }
    if (name === "PhysicsContacts2D") {
        source = imports(source, ["PhysicsContactCallback"]);
        source = source.replaceAll("PhysicsContactBridge2D", "PhysicsContactCallback");
        source = replace(source, "bridge = new PhysicsContactCallback(this);", "bridge = this::captureBrowser;");
        source = removeMethod(source, "void capture(ContactType2D type,");
        source = removeMethod(source, "void captureRemoved(");
        source = source.replace(
            "    /**\n     * Serializes event publication",
            `    /**
     * Copies native callback primitives without retaining borrowed WASM storage.
     * @param type begin, persist or end transition ordinal
     * @param first first complete native body ID
     * @param second second complete native body ID
     * @param firstSubShape first native subshape ID
     * @param secondSubShape second native subshape ID
     * @param x normal X projection
     * @param y normal Y projection
     * @param depth signed penetration in meters
     */
    private void captureBrowser(int type, int first, int second, int firstSubShape, int secondSubShape, float x, float y, float depth) {
        if (!listening) return;
        try {
            record(CONTACT_TYPES[type], resolve(first), resolve(second), firstSubShape, secondSubShape, x, y, depth);
        } catch (RuntimeException | Error failure) {
            recordFailure(failure);
        }
    }

    /**
     * Serializes event publication`,
        );
        source = replace(source, "        bridge.close();", "");
        source = source.replace(
            / \* <p>Jolt JNI 6\.0\.0 requires[\s\S]*? \* body-ID lookups\./,
            " * <p>Native callbacks copy primitives directly into the fixed Java arrays; no\n * per-event owners or boxed body-ID lookups are created.",
        );
    }
    if (name === "PhysicsQueryCollector2D") {
        source = replace(source, " extends CustomCollideShapeBodyCollector", "");
        source = replace(
            source,
            "    @Override\n    public void addHit",
            "    /**\n     * Copies one native candidate and applies exact planar filtering if needed.\n     * @param id complete native body identifier\n     */\n    public void addHit",
        );
    }
    if (name === "PhysicsQueries2D") {
        const first = source.indexOf("    private final ConstBroadPhaseQuery");
        const last = source.indexOf("    /**", first);
        source =
            source.slice(0, first) +
            "    private final PhysicsQueryCollector2D collector; // Reusable primitive native-hit callback.\n\n" +
            source.slice(last);
        source = body(
            source,
            "PhysicsQueries2D(PhysicsWorld2D world)",
            `        this.world = world;
        collector = new PhysicsQueryCollector2D(world);
        world.bodies.prepareQueries(collector::addHit);`,
        );
        source = removeMethod(source, "private <T extends JoltPhysicsObject> T own(");
        source = body(
            source,
            "boolean raycast(",
            `        destination.set(null, 0, 0, 0);
        if (dx == 0 && dy == 0) return false;
        if (!world.bodies.raycast(x, y, dx, dy)) return false;
        RigidBody2D hit = world.findBody(world.bodies.rayBody());
        if (hit == null) throw new IllegalStateException("Ray hit an unowned native body");
        float fraction = world.bodies.rayFraction();
        destination.set(hit, x + dx * fraction, y + dy * fraction, fraction);
        return true;`,
        );
        source = source
            .replace("        world.vectorScratch.set(x, y, 0);\n", "")
            .replace(
                "broadphase.collidePoint(world.vectorScratch, collector, broadphaseFilter, objectFilter);",
                "world.bodies.queryPoint(x, y);",
            );
        source = source.replace(
            /        boundScratch.set\(minX,[\s\S]*?        collector.begin/,
            "        collector.begin",
        );
        source = source.replace(
            "broadphase.collideAaBox(bounds, collector, broadphaseFilter, objectFilter);",
            "world.bodies.queryBounds(minX, minY, maxX, maxY);",
        );
        source = body(source, "public void close()", "        world.bodies.closeQueries();");
        source = source.replace(
            / \* a bounds box,[\s\S]*? \* All queries/,
            " * a bounds box and primitive-ID collector. Every query reuses owned native\n * filters and ray storage without steady per-query allocations.\n * All queries",
        );
    }
    if (name === "PhysicsJoints2D") {
        source = imports(source, ["BrowserPhysicsWorld", "PhysicsConstraint"]);
        source = source
            .replaceAll("PhysicsSystem", "BrowserPhysicsWorld")
            .replaceAll("TwoBodyConstraint constraint", "PhysicsConstraint constraint");
        const distance = bounds(source, "PhysicsJoint2D createDistanceJoint(");
        const first = source.indexOf("        try (DistanceConstraintSettings", distance.open);
        source =
            source.slice(0, first) +
            `        return add(first, second, system.createConstraint(first.id, second.id, true, anchorFirstX, anchorFirstY, anchorSecondX, anchorSecondY, minimum, maximum));
    }` +
            source.slice(distance.end);
        const pivot = bounds(source, "PhysicsJoint2D createPivotJoint(");
        const next = source.indexOf("        try (PointConstraintSettings", pivot.open);
        source =
            source.slice(0, next) +
            `        return add(first, second, system.createConstraint(first.id, second.id, false, anchorX, anchorY, anchorX, anchorY, 0, 0));
    }` +
            source.slice(pivot.end);
        source = replace(source, "TwoBodyConstraintSettings settings", "PhysicsConstraint constraint");
        source = replace(
            source,
            "        PhysicsConstraint constraint = world.bodies.createConstraint(settings, first.id, second.id);\n",
            "",
        );
        source = source.replace(
            "@param settings temporary native constraint description",
            "@param constraint newly owned native constraint",
        );
    }
    if (name === "PhysicsJoint2D") {
        source = imports(source, ["PhysicsConstraint"]);
        source = source
            .replaceAll("TwoBodyConstraint", "PhysicsConstraint")
            .replaceAll("DistanceConstraint", "PhysicsConstraint");
        source = replace(
            source,
            "if (!(constraint instanceof PhysicsConstraint distance))",
            "if (!constraint.isDistance())",
        );
        source = replace(source, "        return distance;", "        return constraint;");
        source = replace(source, "constraint instanceof PhysicsConstraint ?", "constraint.isDistance() ?");
    }
    if (source.includes("EActivation.")) {
        if (!source.includes("import valthorne.browser.physics.BrowserPhysics;"))
            source = imports(source, ["BrowserPhysics"]);
        source = source
            .replaceAll("EActivation.Activate", "BrowserPhysics.ACTIVATE")
            .replaceAll("EActivation.DontActivate", "BrowserPhysics.DONT_ACTIVATE");
    }
    if (
        /com\.github\.stephengold|\b(?:Vec3|RVec3|Quat|JoltPhysicsObject|PhysicsPoses2D)\b/.test(
            source.replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*/g, ""),
        )
    )
        throw new Error("Unadapted native boundary in " + name);
    await writeFile(path.join(output, name + ".java"), source);
}
