package compatibility;

import valthorne.*;
import valthorne.camera.PerspectiveCamera;
import valthorne.graphics.Color;
import valthorne.graphics.model.*;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureBatch;
import valthorne.graphics.texture.TextureData;
import valthorne.math.physics.BodySettings3D;
import valthorne.math.physics.CollisionShape3D;
import valthorne.math.physics.MotionType3D;
import valthorne.math.physics.PhysicsWorld3D;

import java.nio.ByteBuffer;

/**
 * One 3D application source for desktop and web, including physics and editable light state.
 */
public final class CommonSceneApplication implements Application {
    private final Scene3D scene = new Scene3D();
    private final PerspectiveCamera camera = new PerspectiveCamera();
    private FilamentRenderer3D renderer;
    private SceneRenderer3D sceneRenderer;
    private PhysicsWorld3D world;
    private PointLight3D key;
    private int frames;
    private Texture texture;
    private TextureBatch overlay;
    private Material3D textured;

    static void main(String[] args) {
        try {
            JGL.init(new CommonSceneApplication(), JGLConfiguration.defaults().title("Shared 3D scene / B light / N camera / Escape close").contextVersion(4, 1).size(960, 640).visible(false));
            System.out.println("COMMON_SCENE_VALIDATED");
        } catch (Throwable failure) {
            failure.printStackTrace();
            throw failure;
        }
    }

    public void init() {
        sceneRenderer = new SceneRenderer3D();
        renderer = sceneRenderer.filament();
        renderer.setQuality(FilamentRenderer3D.Quality.PERFORMANCE);
        renderer.setAntiAliasing(true);
        renderer.setEnvironmentIntensity(500);
        world = new PhysicsWorld3D(new PhysicsWorld3D.Settings(1f / 60, 8, 64, 2048, 2048, 0));
        camera.setPosition(7, -9, 6);
        camera.lookAt(0, 0, 1.5f, 0, 0, 1);
        camera.setFieldOfViewDegrees(50);
        camera.setClipPlanes(.1f, 100);
        ByteBuffer pixels = ByteBuffer.allocateDirect(16);
        pixels.put(new byte[]{(byte) 255, 30, 30, (byte) 255, 30, (byte) 255, 30, (byte) 255, 30, 30, (byte) 255, (byte) 255, (byte) 160, (byte) 160, (byte) 160, (byte) 255}).flip();
        texture = new Texture(new TextureData(pixels, 2, 2));
        overlay = new TextureBatch(8);
        Model3D box = ModelBuilder3D.box(1, 1, 1);
        scene.add(new ModelInstance3D().setModel(box).setScale(20, 20, .5f).setPosition(0, 0, -.25f).setMaterial(new Material3D().setTint(new Color(.65f, .7f, .75f, 1)).setRoughness(.65f)));
        world.createBody(new BodySettings3D(CollisionShape3D.box(20, 20, .5f), MotionType3D.STATIC).setPosition(0, 0, -.25f));
        for (int i = 0; i < 6; i++) {
            var instance = new ModelInstance3D().setModel(box).setMaterial(new Material3D().setTint(new Color(.12f + i * .12f, .35f, .8f - i * .1f, 1)).setRoughness(.2f + i * .1f));
            scene.add(instance);
            world.createBody(new BodySettings3D(CollisionShape3D.box(1, 1, 1), MotionType3D.DYNAMIC).setPosition((i % 3) * 2 - 2, 0, 1 + (i / 3) * 2)).bind(instance);
        }
        textured = new Material3D().setTexture(texture).setRoughness(.7f);
        scene.add(new ModelInstance3D().setModel(box).setPosition(0, -3, 1).setScale(2, 2, 2).setMaterial(textured));
        scene.add(new ModelInstance3D().setModel(ModelBuilder3D.sphere(1, 24, 16)).setPosition(-3, 3, 1).setMaterial(new Material3D().setTint(new Color(.85f, .45f, .12f, 1)).setMetallic(.8f).setRoughness(.22f)));
        scene.add(new ModelInstance3D().setModel(box).setScale(1.2f, 1.2f, 2).setPosition(3, 2, 1).setMaterial(new Material3D().setTint(new Color(.25f, .8f, 1, .4f)).setRenderPass(RenderPass3D.TRANSLUCENT).setCastsShadow(false).setDepthWrite(false)));
        scene.add(new ModelInstance3D().setModel(box).setPosition(0, 4, 1).setMaterial(new Material3D().setEmissive(.2f, .55f, 1, 8).setEmissionLightEnabled(true)));
        SceneNode3D parent = new SceneNode3D().setPosition(4, 4, 0).setYawRadians(.4f);
        parent.addChild(new SceneNode3D().setModel(box).setPosition(0, 0, 1).setScale(.5f, .5f, 2));
        scene.addNode(parent);
        scene.add(new ModelInstance3D().setModel(box).setPosition(500, 0, 0).setMaterial(new Material3D().setCastsShadow(false)));
        key = new PointLight3D().setPosition(-3, -4, 7).setColor(new Color(1, .85f, .65f, 1)).setIntensity(500).setRange(35).setCastsShadows(true);
        scene.addLight(key);
        scene.addLight(new PointLight3D().setPosition(5, 2, 5).setColor(new Color(.3f, .55f, 1, 1)).setIntensity(45).setRange(25));
    }

    public void update(float dt) {
        world.update(dt);
        textured.setTexture(Keyboard.isKeyDown(Keyboard.T) ? null : texture);
        key.setIntensity(Keyboard.isKeyDown(Keyboard.B) ? 0 : 500);
        if (Keyboard.isKeyDown(Keyboard.N)) {
            camera.setPosition(-9, -13, 8);
            camera.lookAt(0, 0, 1.5f, 0, 0, 1);
        }
        if (Keyboard.isKeyDown(Keyboard.ESCAPE) || frames >= 600) Window.requestClose();
    }

    public void render() {
        sceneRenderer.render(scene, camera);
        frames++;
        overlay.begin();
        overlay.draw(texture, 20, 20, 80, 80);
        overlay.end();
        if (frames == 2) {
            if (renderer.getCachedMeshCount() != 2 || renderer.getOffscreenCount() < 1 || renderer.getPointLightCount() < 3)
                throw new AssertionError("Shared renderer state differs");
            System.out.println("COMMON_SCENE_READY");
        }
        if (frames == 100) {
            renderer.invalidate();
            System.out.println("COMMON_SCENE_STATS vertices=" + renderer.getUploadedSourceVertices() + " unique=" + renderer.getUploadedUniqueVertices());
        }
    }

    public void dispose() {
        if (world != null) world.close();
        if (renderer != null) {
            scene.clear();
            renderer.render(scene, camera);
            if (renderer.getCachedMeshCount() != 0 || renderer.getPointLightCount() != 0)
                throw new AssertionError("Scene retained resources");
            sceneRenderer.close();
            sceneRenderer.close();
        }
        if (overlay != null) overlay.dispose();
        if (texture != null) texture.dispose();
    }
}
