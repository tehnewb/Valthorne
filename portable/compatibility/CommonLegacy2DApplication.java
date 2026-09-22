package compatibility;

import valthorne.*;
import valthorne.graphics.Color;
import valthorne.graphics.lighting.PointLight;
import valthorne.graphics.lighting.RayHandler;
import valthorne.graphics.particle.ParticleSystem;
import valthorne.graphics.shader.ShapeShader;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureBatch;
import valthorne.graphics.texture.TextureData;
import valthorne.graphics.texture.TexturePacker;
import valthorne.math.geometry.Rectangle;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class CommonLegacy2DApplication implements Application {
    private final long[] samples = new long[80];
    private Texture white, atlas;
    private TextureBatch batch;
    private RayHandler lighting;
    private ParticleSystem particles;
    private ShapeShader shapes;
    private Rectangle rectangle;
    private int frames, checks, count;

    static void main(String[] args) {
        try {
            JGL.init(new CommonLegacy2DApplication(), JGLConfiguration.defaults().title("Shared 2D systems").size(640, 480).visible(false));
            System.out.println("COMMON_LEGACY2D_RETURNED");
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
        Window.setSwapInterval(SwapInterval.OFF);
        var data = new TextureData(ByteBuffer.wrap(new byte[]{-1, -1, -1, -1}), 1, 1);
        white = new Texture(data);
        TexturePacker packer = new TexturePacker(4, 4);
        packer.addRegion(data, 0, 0, 1, 1, 1, 1);
        var packed = packer.bake();
        int opaque = 0;
        for (int i = 3; i < 64; i += 4) if (packed.buffer().get(i) != 0) opaque++;
        check(opaque == 1, "Atlas pixel packing");
        check((packed.buffer().get(39) & 255) == 255, "Atlas coordinate origin");
        atlas = new Texture(packed);
        batch = new TextureBatch(64);
        shapes = new ShapeShader();
        rectangle = new Rectangle(120, 160, 120, 160);
        rectangle.setColor(Color.RED);
        lighting = new RayHandler(640, 480);
        lighting.setAmbientLight(.08f, .08f, .08f, 1);
        lighting.addLight(new PointLight(lighting, 64, Color.WHITE, 220, 180, 240));
        lighting.addLight(new PointLight(lighting, 64, new Color(.1f, .5f, 1, 1), 180, 470, 240));
        check(lighting.getLights().size() == 2, "Legacy light membership");
        particles = new ParticleSystem(white, 64);
        particles.setPosition(470, 240);
        particles.getEmitter().setLifetime(100, 100).setEmissionRate(0).setVelocity(0, 0).setGravity(0, 0).setWind(0, 0).setBaseSize(12, 12).setStartEndScale(1, 1).setSpawnCircle(60, false).setStartEndColor(Color.WHITE, Color.WHITE);
        particles.setMaxDrawPerFrame(8);
        particles.burst(32);
        particles.update(.5f);
        check(particles.getBurstRemaining() == 0, "Particle burst processed");
        System.out.println("COMMON_LEGACY2D_READY");
    }

    public void update(float dt) {
        lighting.update();
        particles.update(dt);
        if (++frames >= 180) Window.requestClose();
    }

    public void render() {
        long start = System.nanoTime();
        Window.clear(Color.BLACK);
        batch.begin();
        batch.draw(white, 0, 0, 640, 480);
        batch.draw(atlas, 280, 340, 80, 80);
        batch.end();
        shapes.draw(rectangle);
        particles.draw();
        lighting.render();
        if (frames > 20 && count < 80) samples[count++] = System.nanoTime() - start;
    }

    public void dispose() {
        if (lighting != null) lighting.dispose();
        if (particles != null) particles.dispose();
        if (shapes != null) shapes.dispose();
        if (batch != null) batch.dispose();
        if (white != null) white.dispose();
        if (atlas != null) atlas.dispose();
        if (count > 0) {
            Arrays.sort(samples);
            long total = 0;
            for (long value : samples) total += value;
            System.out.println("LEGACY2D_BENCHMARK samples=" + count + " meanSubmitMs=" + (total / 1e6 / count) + " p95SubmitMs=" + (samples[76] / 1e6));
        }
        System.out.println("COMMON_LEGACY2D_VALIDATED checks=" + checks);
    }
}
