import valthorne.Application;
import valthorne.JGL;
import valthorne.Window;
import valthorne.graphics.texture.NinePatchTexture;
import valthorne.graphics.texture.TextureData;
import valthorne.graphics.texture.TextureFilter;
import valthorne.viewport.ScreenViewport;

public class NinePatchTextureTest implements Application {

    private ScreenViewport viewport;
    private NinePatchTexture panel;

    private float time = 0f;

    public static void main(String[] args) {
        JGL.init(new NinePatchTextureTest(), "NinePatch Texture Test", 1280, 720);
    }

    @Override
    public void init() {
        int width = Window.getWidth();
        int height = Window.getHeight();
        viewport = new ScreenViewport(width, height);

        // Load your UI panel texture (replace path with your actual asset)
        TextureData data = TextureData.load("./src/test/resources/ui-test.png");

        // Example: 12px borders on all sides in the source image
        int border = 12;
        panel = new NinePatchTexture(data, 5, 0, 5, 5);

        // Initial size is half the window, centered
        float w = width / 2f;
        float h = height / 2f;
        panel.setSize(w, h);
        panel.setRotationOriginCenter();
        panel.setPosition((width - w) / 2f, (height - h) / 2f);
        panel.setFilter(TextureFilter.NEAREST);

        Window.addWindowResizeListener(event -> {
            int wWin = event.getNewWidth();
            int hWin = event.getNewHeight();

            // Keep the panel roughly half the window size and centered
            float pw = wWin / 2f;
            float ph = hWin / 2f;
            panel.setSize(pw, ph);
            panel.setPosition((wWin - pw) / 2f, (hWin - ph) / 2f);
            panel.setRotationOriginCenter();
        });
    }

    @Override
    public void update(float delta) {
        viewport.update(Window.getWidth(), Window.getHeight());
        time += delta;
        Window.setTitle("FPS: " + JGL.getFramesPerSecond());

    }

    @Override
    public void render() {
        viewport.render(() -> panel.draw());
    }

    @Override
    public void dispose() {
        if (panel != null) {
            panel.dispose();
        }
    }
}
