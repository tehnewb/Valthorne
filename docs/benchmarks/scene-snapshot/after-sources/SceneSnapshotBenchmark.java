package valthorne.graphics.model;

import org.openjdk.jmh.annotations.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Measures the same CPU scene collection used by both renderers. Each operation
 * consumes every captured transform and the material signature. Models are shared
 * across a trial; the OBJ case has two untextured material groups and needs no GL.
 * Timings and allocations are per complete scene, excluding GPU upload/BVH build.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations=3, time=1)
@Measurement(iterations=5, time=1)
@Fork(value=2, jvmArgsAppend={"-Xms256m", "-Xmx256m"})
@State(Scope.Thread)
public class SceneSnapshotBenchmark {
    @Param({"1", "256"}) public int count;
    @Param({"false", "true"}) public boolean moving;
    @Param({"direct", "hierarchy", "obj"}) public String kind;
    private final PathTracingScene.Collector collector = new PathTracingScene.Collector();
    private Scene3D scene;
    private ModelInstance3D[] instances;
    private SceneNode3D root;
    private ObjModel3D imported;
    private float phase;

    @Setup public void setup() {
        scene = new Scene3D();
        Model3D model = ModelBuilder3D.box(1, 1, 1);
        if (kind.equals("obj")) {
            String source = "v 0 0 0\nv 1 0 0\nv 0 1 0\nv 0 0 1\n"
                    + "usemtl first\nf 1 2 3\nusemtl second\nf 1 4 2\n";
            imported = ObjModel3D.load("scene.obj", path -> source.getBytes(StandardCharsets.UTF_8), false);
            model = imported;
        }
        if (kind.equals("hierarchy")) {
            root = new SceneNode3D().setPosition(3, 4, 2).setRotation(.1f, .2f, .3f);
            scene.addNode(root);
            for (int i = 0; i < count; i++)
                root.addChild(new SceneNode3D().setModel(model).setPosition(i * .1f, 2, 1));
        } else {
            instances = new ModelInstance3D[count];
            for (int i = 0; i < count; i++) {
                instances[i] = new ModelInstance3D().setModel(model).setPosition(i * .1f, 2, 1)
                        .setRotation(.1f, .2f, .3f);
                scene.add(instances[i]);
            }
        }
        collector.capture(scene);
    }

    @Benchmark public long collect() {
        phase = phase >= 1 ? 0 : phase + .001f;
        if (moving) {
            if (root != null) root.getPosition().x = phase;
            else for (int i = 0; i < instances.length; i++) instances[i].getPosition().x = i * .1f + phase;
        }
        PathTracingScene snapshot = collector.capture(scene);
        float sum = 0;
        for (int i = 0; i < snapshot.instances.size(); i++)
            sum += snapshot.instances.get(i).transform().m30();
        return snapshot.signature() ^ Float.floatToIntBits(sum) ^ snapshot.instances.size();
    }

    @TearDown public void close() {
        collector.clear();
        if (imported != null) imported.dispose();
    }
}
