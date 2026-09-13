package valthorne.verification;

import java.io.IOException;
import java.nio.file.Path;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.primitives.Rayf;
import valthorne.graphics.FilamentPlatform;
import valthorne.graphics.model.ModelInstance3D;
import valthorne.math.physics.BodySettings3D;
import valthorne.math.physics.CollisionLayers3D;
import valthorne.math.physics.CollisionShape3D;
import valthorne.math.physics.MotionType3D;
import valthorne.math.physics.PhysicsWorld3D;
import valthorne.math.physics.RigidBody3D;

/**
 * Headless native compatibility consumer. Exercises public Jolt-backed engine
 * APIs without a window, OpenGL context or Filament renderer. Platform selection
 * checks the published runtime matrix independently of hardware rendering support.
 */
public final class DesktopNativeVerification {
    private static final PhysicsWorld3D.Settings SETTINGS =
            new PhysicsWorld3D.Settings(1f / 60, 8, 64, 2048, 2048, 0);

    private DesktopNativeVerification() {}

    /** Runs all native cases; the optional first argument selects the JSON report. */
    public static void main(String[] args) throws IOException {
        Path output = Path.of(args.length == 0 ? "build/reports/verification/desktop-native.json" : args[0]);
        VerificationReport report = new VerificationReport("desktop-native", output,
                new VerificationReport.Case("filament-runtime-platform-selection", 10),
                new VerificationReport.Case("native-collision-model-binding-and-raycast", 14),
                new VerificationReport.Case("native-body-joint-and-world-lifecycle", 11),
                new VerificationReport.Case("native-collision-layer-filtering", 2),
                new VerificationReport.Case("native-fixed-step-catch-up", 5),
                new VerificationReport.Case("native-sensor-contacts", 2));
        report.run("filament-runtime-platform-selection", () -> platformSelection(report));
        report.run("native-collision-model-binding-and-raycast", () -> collision(report));
        report.run("native-body-joint-and-world-lifecycle", () -> lifecycle(report));
        report.run("native-collision-layer-filtering", () -> collisionLayers(report));
        report.run("native-fixed-step-catch-up", () -> fixedStep(report));
        report.run("native-sensor-contacts", () -> sensors(report));
        report.finish();
    }

    private static void platformSelection(VerificationReport report) {
        String[][] supported = {
                {"Windows 11", "amd64"}, {"Linux", "x86_64"}, {"Linux", "aarch64"},
                {"Mac OS X", "aarch64"}, {"Darwin", "arm64"}
        };
        String[][] unsupported = {
                {"Mac OS X", "x86_64"}, {"Windows 11", "aarch64"}, {"Linux", "arm"},
                {"Android", "aarch64"}, {"unknown", "amd64"}
        };
        for (String[] platform : supported) {
            report.require(FilamentPlatform.supported(platform[0], platform[1]),
                    "Expected published Filament runtime: " + String.join("/", platform));
        }
        for (String[] platform : unsupported) {
            report.require(!FilamentPlatform.supported(platform[0], platform[1]),
                    "Reject unpublished Filament runtime: " + String.join("/", platform));
        }
    }

    private static void collision(VerificationReport report) {
        try (PhysicsWorld3D world = new PhysicsWorld3D(SETTINGS);
             PhysicsWorld3D independent = new PhysicsWorld3D(SETTINGS)) {
            report.near(world.getGravity().z, -9.81, .001, "Z-up gravity");
            RigidBody3D ground = world.createBody(new BodySettings3D(
                    CollisionShape3D.box(30, 30, 1), MotionType3D.STATIC).setPosition(0, 0, -.5f));
            RigidBody3D box = world.createBody(new BodySettings3D(
                    CollisionShape3D.box(1, 1, 1), MotionType3D.DYNAMIC)
                    .setPosition(0, 0, 4).setMass(2).setRotationLocked(true));
            ModelInstance3D model = new ModelInstance3D().setScale(2);
            box.bind(model).setUserData("box");
            report.require("box".equals(box.getUserData()), "Retain consumer metadata");
            int[] contacts = {0, 0, 0};
            int[] steps = {0, 0};
            boolean[] validContacts = {true};
            world.addContactListener(event -> {
                contacts[event.type().ordinal()]++;
                validContacts[0] &= event.bodyA() != null && event.bodyB() != null;
            });
            world.addBeforeStepListener(value -> steps[0]++);
            world.addAfterStepListener(value -> steps[1]++);
            world.optimizeBroadPhase();
            for (int index = 0; index < 180; index++) world.step();
            report.near(box.getPosition().z, .5, .06, "Box rests on floor");
            report.near(model.getPosition().z, box.getPosition().z, .00001, "Model follows body");
            report.near(model.getScale().x, 2, 0, "Binding preserves model scale");
            report.require(validContacts[0], "Contact callbacks expose both bodies");
            report.require(contacts[0] > 0, "Receive added contacts");
            report.require(contacts[1] > 0, "Receive persisted contacts");
            report.require(steps[0] == 180 && steps[1] == 180, "Dispatch every step callback");
            report.require(independent.getStepCount() == 0 && independent.getBodyCount() == 0,
                    "Worlds own independent simulation state");
            Rayf ray = new Rayf(0, 0, 5, 0, 0, -2);
            var hit = world.raycast(ray, 10);
            report.require(hit != null && hit.body() == box, "Ray finds the closest body");
            report.near(hit.distance(), 4, .1, "Normalize ray distance");
            report.near(hit.normal().z, 1, .01, "Return surface normal");
            var floorHit = world.raycast(ray, 10, box);
            report.require(floorHit != null && floorHit.body() == ground, "Honor excluded ray body");
        }
    }

    private static void lifecycle(VerificationReport report) {
        PhysicsWorld3D world = new PhysicsWorld3D(SETTINGS);
        RigidBody3D remaining;
        try (world) {
            RigidBody3D ground = world.createBody(new BodySettings3D(
                    CollisionShape3D.box(10, 10, 1), MotionType3D.STATIC));
            RigidBody3D box = world.createBody(new BodySettings3D(
                    CollisionShape3D.box(1, 1, 1), MotionType3D.DYNAMIC).setMass(2));
            box.setTransform(new Vector3f(0, 0, 5), new Quaternionf(0, 0, 0, 2));
            report.near(box.getRotation().w, 1, .0001, "Normalize body orientation");
            box.setLinearVelocity(0, 0, 0).addImpulse(new Vector3f(2, 0, 0));
            report.near(box.getLinearVelocity().x, 1, .01, "Impulse respects mass");
            box.sleep();
            report.require(!box.isActive(), "Sleep a body");
            box.activate();
            report.require(box.isActive(), "Reactivate a body");
            remaining = world.createBody(new BodySettings3D(
                    CollisionShape3D.box(1, 1, 1), MotionType3D.KINEMATIC).setPosition(6, 0, 2));
            remaining.moveKinematic(new Vector3f(7, 0, 2), new Quaternionf());
            world.step();
            report.near(remaining.getPosition().x, 7, .01, "Move a kinematic body");
            var joint = world.createDistanceJoint(ground, box, new Vector3f(0, 0, 8), box.getPosition(), 3, 3);
            report.require(!joint.isDestroyed(), "Create a live joint");
            joint.close();
            report.require(joint.isDestroyed(), "Close a joint");
            var ownedJoint = world.createDistanceJoint(ground, box, new Vector3f(0, 0, 8), box.getPosition(), 0, 10);
            box.close();
            box.close();
            report.require(box.isDestroyed() && ownedJoint.isDestroyed(), "Closing a body also closes its joints");
            report.require(world.getBodyCount() == 2, "Remove disposed body from the world");
        }
        report.require(remaining.isDestroyed(), "Closing a world invalidates retained handles");
        report.expect(IllegalStateException.class, world::step);
        world.close();
    }

    private static void collisionLayers(VerificationReport report) {
        CollisionLayers3D layers = new CollisionLayers3D().setCollision(0, 1, false);
        try (PhysicsWorld3D world = new PhysicsWorld3D(SETTINGS, layers)) {
            world.createBody(new BodySettings3D(CollisionShape3D.box(10, 10, 1), MotionType3D.STATIC));
            RigidBody3D body = world.createBody(new BodySettings3D(
                    CollisionShape3D.sphere(.5f), MotionType3D.DYNAMIC).setLayer(1).setPosition(0, 0, 2));
            int[] contacts = {0};
            world.addContactListener(event -> contacts[0]++);
            for (int index = 0; index < 90; index++) world.step();
            report.require(body.getPosition().z < 0, "Disabled layer pair does not block motion");
            report.require(contacts[0] == 0, "Disabled layer pair does not emit contacts");
        }
    }

    private static void fixedStep(VerificationReport report) {
        try (PhysicsWorld3D world = new PhysicsWorld3D(SETTINGS)) {
            report.require(world.update(1) == 8, "Bound catch-up steps");
            report.require(world.getDroppedTime() > .8, "Record discarded catch-up time");
            report.require(world.getStepCount() == 8, "Count executed native steps");
            world.setGravity(new Vector3f());
            report.near(world.getGravity().length(), 0, 0, "Set world gravity");
            report.require(world.getBodies().size() == world.getBodyCount(), "Expose consistent body snapshot");
        }
    }

    private static void sensors(VerificationReport report) {
        try (PhysicsWorld3D world = new PhysicsWorld3D(SETTINGS)) {
            int[] contacts = {0};
            world.addContactListener(event -> {
                if (event.bodyA().isSensor() || event.bodyB().isSensor()) contacts[0]++;
            });
            world.createBody(new BodySettings3D(CollisionShape3D.box(10, 10, 1), MotionType3D.STATIC)
                    .setSensor(true));
            RigidBody3D body = world.createBody(new BodySettings3D(
                    CollisionShape3D.sphere(.25f), MotionType3D.DYNAMIC).setPosition(0, 0, 2));
            for (int index = 0; index < 80; index++) world.step();
            report.require(contacts[0] > 0, "Sensors emit contacts");
            report.require(body.getPosition().z < 0, "Sensors do not block dynamic bodies");
        }
    }
}
