package compatibility;

import valthorne.*;
import valthorne.graphics.Color;
import valthorne.graphics.lighting2d.Lighting2D;
import valthorne.graphics.lighting2d.Occluder2D;
import valthorne.graphics.lighting2d.PointLight2D;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureBatch;
import valthorne.graphics.texture.TextureData;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class CommonLightingApplication implements Application {
    private final double[] samples = new double[80];
    private Lighting2D lighting;
    private Texture white;
    private TextureBatch batch;
    private PointLight2D key;
    private int frames, checks;
    private long uploads, maps;

    static void main(String[] args) {
        try {
            JGL.init(new CommonLightingApplication(), JGLConfiguration.defaults().title("Shared 2D lighting").size(640, 480).visible(false));
            System.out.println("COMMON_LIGHTING_RETURNED");
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    public void init() {
        Window.setSwapInterval(SwapInterval.OFF);
        lighting = new Lighting2D(64, 512).setAmbient(new Color(.015f, .015f, .015f, 1)).setResolutionScale(1);
        key = new PointLight2D().setPosition(150, 240).setRadius(360).setColor(new Color(1, .3f, .1f, 1)).setIntensity(4).setSourceRadius(6);
        lighting.addLight(key);
        lighting.addLight(new PointLight2D().setPosition(530, 320).setRadius(160).setColor(new Color(.1f, .4f, 1, 1)).setIntensity(3));
        lighting.addOccluder(Occluder2D.rectangle(300, 180, 30, 140));
        batch = new TextureBatch(32);
        ByteBuffer data = ByteBuffer.allocateDirect(4);
        data.putInt(-1).flip();
        white = new Texture(new TextureData(data, 1, 1));
    }

    public void update(float dt) {
        frames++;
        if (frames > 100 && frames <= 200) key.setPosition(150 + (float) Math.sin(frames * .1) * 25, 240);
        if (frames == 201) key.setCastsShadows(false);
        if (frames >= 300) Window.requestClose();
    }

    public void render() {
        long started = System.nanoTime();
        lighting.beginScene(0, 0, 640, 480, Window.getWidth(), Window.getHeight());
        try {
            batch.begin();
            batch.draw(white, 0, 0, 640, 480);
            batch.end();
            lighting.endScene();
        } finally {
            lighting.cancelScene();
        }
        int phase = (frames - 1) / 100, index = (frames - 1) % 100;
        if (index >= 20 && index < 100) samples[index - 20] = (System.nanoTime() - started) / 1e6;
        if (frames == 2) {
            check(lighting.getVisibleLightCount() == 2, "Visible lights");
            check(lighting.getShadowUploadCount() > 0, "Shadow data uploaded");
            uploads = lighting.getShadowUploadCount();
            maps = lighting.getLightMapRenderCount();
            System.out.println("COMMON_LIGHTING_READY");
        }
        if (frames == 90) {
            check(lighting.getShadowUploadCount() == uploads, "Static shadow cache");
            check(lighting.getLightMapRenderCount() == maps, "Static light map cache");
        }
        if (frames == 190) {
            check(lighting.getShadowUploadCount() > uploads, "Moving shadow update");
            check(lighting.getLightMapRenderCount() > maps, "Moving light map update");
        }
        if (index == 99) {
            double sum = 0;
            for (double sample : samples) sum += sample;
            Arrays.sort(samples);
            System.out.println("LIGHTING_BENCHMARK phase=" + phase + " meanMs=" + sum / samples.length + " p95Ms=" + samples[75] + " shadowUploads=" + lighting.getShadowUploadCount() + " mapRenders=" + lighting.getLightMapRenderCount());
        }
    }

    public void dispose() {
        if (lighting != null) {
            lighting.close();
            lighting.close();
        }
        if (white != null) white.dispose();
        if (batch != null) batch.dispose();
        System.out.println("COMMON_LIGHTING_VALIDATED checks=" + checks);
    }
}
