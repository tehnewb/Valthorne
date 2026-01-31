import valthorne.Application;
import valthorne.JGL;
import valthorne.Mouse;
import valthorne.Window;
import valthorne.camera.OrthographicCamera2D;
import valthorne.graphics.font.Font;
import valthorne.graphics.font.FontData;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureData;
import valthorne.graphics.texture.TexturePacker;
import valthorne.viewport.ScreenViewport;
import valthorne.viewport.Viewport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TexturePackerTest implements Application {

    private Texture atlasTexture;
    private Viewport viewport;
    private OrthographicCamera2D camera2D;
    private Font font;

    public static void main(String[] args) {
        JGL.init(new TexturePackerTest(), "Texture Packer Test", 1280, 720);
    }

    @Override
    public void init() {
        try {
            font = new Font(FontData.load(Files.readAllBytes(Paths.get("./src/test/resources/test-font.ttf")), 16, 0, 254));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.viewport = new ScreenViewport(Window.getWidth(), Window.getHeight());
        this.camera2D = new OrthographicCamera2D();
        this.camera2D.setCenter(Window.getWidth() / 2f, Window.getHeight() / 2f);
        this.viewport.setCamera(camera2D);

        TextureData sheet = TextureData.load("./src/test/resources/cat-test.png");
        TexturePacker packer = new TexturePacker(128, 128);

        // Extract 3 patches
        packer.addRegion(sheet, 0, 0, 64, 64, 0, 0);
        packer.addRegion(sheet, 64, 0, 64, 64, 64, 0);
        packer.addRegion(sheet, 0, 64, 64, 64, 0, 64);

        // Bake to new TextureData
        TextureData atlasData = packer.bake();

        // Make a Texture so you can draw it with your system
        atlasTexture = new Texture(atlasData);

        // Scale so it’s clearly visible
        atlasTexture.setScale(4, 4);
        // Position center-ish
        atlasTexture.setPosition(0, 0);
        font.setPosition(25, Window.getHeight() - font.getHeight() - 25);
    }

    @Override
    public void render() {
        viewport.render(() -> {
            font.draw();

            atlasTexture.draw();
        });
    }

    @Override
    public void update(float delta) {
        viewport.update(Window.getWidth(), Window.getHeight());
        font.setText("FPS: " + JGL.getFramesPerSecond() + " X: " + Mouse.getX() + " Y: " + Mouse.getY());

        if (Mouse.getScrollY() != 0) {
            this.camera2D.setZoom(this.camera2D.getZoom() + Mouse.getScrollY() * 0.1f);
            this.camera2D.setCenter(Mouse.getX(), Mouse.getY());
        }
    }

    @Override
    public void dispose() {
        if (atlasTexture != null) atlasTexture.dispose();
    }
}
