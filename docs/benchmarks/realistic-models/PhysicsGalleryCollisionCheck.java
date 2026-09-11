import org.joml.Vector3f;
import valthorne.examples.PhysicsStudioModels;
import valthorne.math.physics.*;

class PhysicsGalleryCollisionCheck {
    public static void main(String[] args) {
        try (var gallery = new PhysicsStudioModels()) {
            for (var entry : gallery.entries()) {
                try (var world = new PhysicsWorld3D()) {
                    world.setGravity(new Vector3f(0, 0, -9.81f));
                    world.createBody(new BodySettings3D(CollisionShape3D.box(24, 20, 1), MotionType3D.STATIC).setPosition(0, 0, -.5f));
                    var body = world.createBody(new BodySettings3D(CollisionShape3D.convexHull(entry.model()), MotionType3D.DYNAMIC).setPosition(0, 0, 5).setRotation(entry.rotation()).setContinuousCollision(true));
                    for (int i = 0; i < 180; i++) world.step();
                    var position = body.getPosition();
                    if (!position.isFinite() || position.z < -.1f || position.z > 3)
                        throw new AssertionError(entry.name() + " failed ground collision: " + position);
                    System.out.println(entry.name() + " convex hull drop passed: " + position);
                }
            }
        }
    }
}
