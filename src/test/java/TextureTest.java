import valthorne.Application;
import valthorne.JGL;
import valthorne.Window;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureData;
import valthorne.graphics.texture.TextureFilter;
import valthorne.viewport.ScreenViewport;

public class TextureTest implements Application {

    private Texture rotating, scaling, flipping, region;
    private ScreenViewport viewport;

    private float time = 0f;

    @Override
    public void init() {
        int width = Window.getWidth();
        int height = Window.getHeight();
        viewport = new ScreenViewport(width, height);

        // Load texture ONCE
        TextureData data = TextureData.load("./src/test/resources/pixel-art.png");
        region = new Texture(data);
        rotating = new Texture(data);
        scaling = new Texture(data);
        flipping = new Texture(data);

        region.setFilter(TextureFilter.NEAREST);
        region.setRegion(128, 128, 256, 256);
        region.setSize(width / 2f, height / 2f);
        rotating.setSize(width / 2f, height / 2f);
        scaling.setSize(width / 2f, height / 2f);
        flipping.setSize(width / 2f, height / 2f);

        region.setPosition(0, 0);
        rotating.setPosition(width / 2f, 0);
        scaling.setPosition(width / 2f, height / 2f);
        flipping.setPosition(0, height / 2f);

        rotating.setRotationOriginCenter();

        Window.addWindowResizeListener(event -> {
            int w = event.getNewWidth();
            int h = event.getNewHeight();
            region.setSize(w / 2f, h / 2f);
            rotating.setSize(w / 2f, h / 2f);
            scaling.setSize(w / 2f, h / 2f);
            flipping.setSize(w / 2f, h / 2f);

            region.setPosition(0, 0);
            rotating.setPosition(w / 2f, 0);
            scaling.setPosition(w / 2f, h / 2f);
            flipping.setPosition(0, h / 2f);
        });
    }

    @Override
    public void update(float delta) {
        viewport.update(Window.getWidth(), Window.getHeight());
        Window.setTitle("FPS: " + JGL.getFramesPerSecond());
        time += delta;

        // Animate scale to verify scaleX/scaleY work
        float s = 1f + (float) Math.sin(time) * 0.5f; // range 0.5 → 1.5
        scaling.setScale(s, s);
        // Toggle flipping every 2 seconds to test flip logic
        flipping.setFlipX((int) (time * 0.5f) % 2 == 0);
        flipping.setFlipY((int) (time * 0.25f) % 2 == 0);
        rotating.setRotation((float) (rotating.getRotation() + delta * 100f));
    }

    @Override
    public void render() {
        viewport.render(() -> {
            region.draw();
            scaling.draw();
            flipping.draw();
            rotating.draw();
        });
    }

    @Override
    public void dispose() {

    }

    public static void main(String[] args) {
        JGL.init(new TextureTest(), "Texture Feature Test", 1280, 720);
    }
}
