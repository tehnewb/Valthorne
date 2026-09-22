package compatibility;

import valthorne.*;
import valthorne.graphics.Color;
import valthorne.graphics.Sprite;
import valthorne.graphics.debug.PerformanceOverlay;
import valthorne.graphics.lighting2d.GroundShadowRenderer2D;
import valthorne.graphics.lighting2d.PointLight2D;
import valthorne.graphics.lighting2d.SpriteGroundShadow2D;
import valthorne.graphics.lighting2d.SpriteVolumeRenderer2D;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureBatch;
import valthorne.graphics.texture.TextureData;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class CommonSpriteLightingApplication implements Application {
    private final long[] samples = new long[80];
    private Texture white, art;
    private TextureBatch batch;
    private GroundShadowRenderer2D shadows;
    private SpriteVolumeRenderer2D volume;
    private SpriteGroundShadow2D card;
    private List<SpriteGroundShadow2D> cards;
    private PointLight2D key;
    private PointLight2D[] fills;
    private PerformanceOverlay overlay;
    private int frames, checks, count;

    static void main(String[] args) {
        try {
            JGL.init(new CommonSpriteLightingApplication(), JGLConfiguration.defaults().title("Shared sprite lighting").size(640, 480).visible(false));
            System.out.println("COMMON_SPRITE_LIGHTING_RETURNED");
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
        String formatted = String.format(Locale.ROOT, "FPS %.0f  |  %.2f MS", 120d, 8.34d);
        check(formatted.equals("FPS 120  |  8.34 MS"), "FPS formatting: " + formatted);
        white = new Texture(new TextureData(ByteBuffer.wrap(new byte[]{-1, -1, -1, -1}), 1, 1));
        ByteBuffer pixels = ByteBuffer.allocate(32 * 32 * 4);
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++) {
                float dx = (x - 15.5f) / 15, dy = (y - 15.5f) / 15;
                pixels.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) (dx * dx + dy * dy < 1 ? 255 : 0));
            }
        pixels.flip();
        art = new Texture(new TextureData(pixels, 32, 32));
        var sprite = new Sprite(art);
        sprite.setPosition(260, 140);
        sprite.setSize(120, 240);
        sprite.setColor(new Color(.15f, .9f, .3f, 1));
        card = new SpriteGroundShadow2D(sprite).setHeight(100).setMaxLength(160).setOpacity(.7f);
        cards = List.of(card);
        key = new PointLight2D().setPosition(100, 380).setElevation(320).setRadius(1000).setIntensity(4);
        fills = new PointLight2D[]{new PointLight2D().setPosition(500, 350).setElevation(280).setRadius(1000).setIntensity(.5f)};
        batch = new TextureBatch(64);
        shadows = new GroundShadowRenderer2D();
        volume = new SpriteVolumeRenderer2D();
        overlay = new PerformanceOverlay();
        check(card.getSprite() == sprite, "Borrowed sprite ownership");
        System.out.println("COMMON_SPRITE_LIGHTING_READY");
    }

    public void update(float dt) {
        frames++;
        if (frames == 121) {
            card.setEnabled(false);
            System.out.println("SPRITE_SHADOW_DISABLED");
        }
        if (frames == 241) {
            key.setIntensity(0);
            fills[0].setIntensity(0);
            System.out.println("SPRITE_LIGHTS_DISABLED");
        }
        if (frames >= 360) Window.requestClose();
    }

    public void render() {
        long start = System.nanoTime();
        Window.clear(Color.BLACK);
        batch.begin();
        batch.setColor(.5f, .5f, .5f, 1);
        batch.draw(white, 0, 0, 640, 480);
        batch.setColor(Color.WHITE);
        shadows.draw(batch, key, cards, fills);
        volume.draw(batch, card, key, fills, 1);
        overlay.frame();
        overlay.draw(batch, 18, 440);
        batch.end();
        if (frames == 2) check(shadows.getDrawCalls() == 2, "Projected and contact shadow draws");
        if (frames == 122) check(shadows.getDrawCalls() == 0, "Disabled shadow culling");
        if (frames > 20 && count < 80) samples[count++] = System.nanoTime() - start;
    }

    public void dispose() {
        if (shadows != null) {
            shadows.close();
            shadows.close();
        }
        if (volume != null) {
            volume.close();
            volume.close();
        }
        if (overlay != null) overlay.close();
        if (batch != null) batch.dispose();
        if (art != null) art.dispose();
        if (white != null) white.dispose();
        if (count > 0) {
            Arrays.sort(samples);
            long total = 0;
            for (long v : samples) total += v;
            System.out.println("SPRITE_LIGHTING_BENCHMARK samples=" + count + " meanSubmitMs=" + (total / 1e6 / count) + " p95SubmitMs=" + (samples[76] / 1e6));
        }
        System.out.println("COMMON_SPRITE_LIGHTING_VALIDATED checks=" + checks);
    }
}
