package compatibility;

import valthorne.Application;
import valthorne.JGL;
import valthorne.JGLConfiguration;
import valthorne.Window;
import valthorne.camera.PerspectiveCamera;
import valthorne.graphics.Color;
import valthorne.graphics.lighting2d.PathTracer2D;
import valthorne.graphics.model.*;
import valthorne.graphics.texture.FrameBuffer;
import valthorne.graphics.texture.Texture;

import java.util.Arrays;

public final class CommonPathTracingApplication implements Application {
    private final Scene3D scene = new Scene3D();
    private final PerspectiveCamera camera = new PerspectiveCamera();
    private final long[] timings = new long[24];
    private PathTracer3D tracer;
    private PathTracer2D flat;
    private FrameBuffer target;
    private Texture texture;
    private ModelInstance3D box;
    private int frames, checks, count;

    static void main(String[] args) {
        try {
            JGL.init(new CommonPathTracingApplication(), JGLConfiguration.defaults().title("Shared path tracing").contextVersion(4, 3).size(640, 480).visible(false));
            System.out.println("COMMON_PATH_RETURNED");
        } catch (Throwable error) {
            error.printStackTrace();
            throw error;
        }
    }

    private void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    public void init() {
        target = new FrameBuffer(256, 192, true);
        tracer = new PathTracer3D().setRenderMode(PathTracer3D.RenderMode.PROGRESSIVE).setQuality(PathTracer3D.Quality.ULTRA).setMaxSamples(32).setSky(.1f, .12f, .15f);
        camera.setPosition(7, -10, 7);
        camera.lookAt(0, 0, 1, 0, 0, 1);
        camera.setClipPlanes(.1f, 100);
        scene.add(new ModelInstance3D().setModel(ModelBuilder3D.box(12, 12, .4f)).setPosition(0, 0, -.2f).setMaterial(new Material3D().setTint(Color.WHITE)));
        box = new ModelInstance3D().setModel(ModelBuilder3D.box(2, 2, 2)).setPosition(-1, 0, 1).setMaterial(new Material3D().setTint(Color.RED).setRoughness(.5f));
        scene.add(box);
        texture = new Texture("portable/compatibility/assets/colors.png");
        scene.add(new ModelInstance3D().setModel(ModelBuilder3D.box(1.5f, 1.5f, 3)).setPosition(2, 1, 1.5f).setMaterial(new Material3D().setTexture(texture)));
        scene.add(new ModelInstance3D().setModel(ModelBuilder3D.box(2, 2, .2f)).setPosition(0, 0, 5).setMaterial(new Material3D().setEmissive(1, .85f, .6f, 12)));
        flat = new PathTracer2D().setView(0, 0, 10, 20);
        flat.getTracer().setRenderMode(PathTracer3D.RenderMode.PROGRESSIVE).setQuality(PathTracer3D.Quality.HIGH).setMaxSamples(24);
        flat.addSurface(-5, -5, 10, 10, 0, new Material3D().setTint(Color.WHITE));
        flat.addWall(-1, -1, 2, 2, 2, new Material3D().setTint(Color.BLUE));
        flat.addAreaLight(2, -2, 4, .5f, Color.WHITE, 12);
        System.out.println("COMMON_PATH_READY");
    }

    public void update(float dt) {
        frames++;
        if (frames == 35) {
            check(tracer.getAccumulatedSamples() == 32, "Progressive sample budget");
            check(tracer.getSceneBuildCount() == 1, "Stationary BVH reuse");
            check(tracer.getTriangleCount() == 48, "Scene triangles");
            camera.setPosition(8, -10, 7);
            camera.lookAt(0, 0, 1, 0, 0, 1);
        }
        if (frames == 36) {
            check(tracer.getAccumulatedSamples() == 4, "Camera resets accumulation");
            check(tracer.getSceneBuildCount() == 1, "Camera preserves BVH");
        }
        if (frames == 45) box.setPosition(-2, 0, 1);
        if (frames == 46) check(tracer.getSceneBuildCount() == 2, "Scene edit rebuilds BVH");
        if (frames == 55) {
            tracer.setRenderMode(PathTracer3D.RenderMode.REALTIME).setQuality(PathTracer3D.Quality.INTERACTIVE);
            System.out.println("PATH_REALTIME");
        }
        if (frames == 95) System.out.println("PATH_2D");
        if (frames >= 125) Window.requestClose();
    }

    public void render() {
        Window.clear3D(Color.BLACK);
        long start = System.nanoTime();
        target.begin();
        if (frames < 95) tracer.render(scene, camera);
        else flat.render();
        target.end();
        target.draw(0, 0, Window.getWidth(), Window.getHeight());
        if (frames >= 65 && count < 24) timings[count++] = System.nanoTime() - start;
    }

    public void dispose() {
        if (tracer != null) tracer.close();
        if (flat != null) flat.close();
        if (target != null) target.dispose();
        if (texture != null) texture.dispose();
        Arrays.sort(timings);
        if (count == 24) {
            long total = 0;
            for (long time : timings) total += time;
            System.out.println("PATH_BENCHMARK samples=24 meanSubmitMs=" + total / 24e6 + " p95SubmitMs=" + timings[22] / 1e6);
        }
        System.out.println("COMMON_PATH_VALIDATED checks=" + checks);
    }
}
