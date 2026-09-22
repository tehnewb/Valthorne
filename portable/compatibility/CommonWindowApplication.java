package compatibility;

import valthorne.*;
import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureData;

import java.nio.ByteBuffer;

public final class CommonWindowApplication implements Application {
    private int frames, checks;

    static void main(String[] args) {
        try {
            JGL.init(new CommonWindowApplication(), JGLConfiguration.defaults().title("Shared window controls").size(640, 480).visible(false));
            System.out.println("COMMON_WINDOW_RETURNED");
        } catch (Throwable error) {
            error.printStackTrace();
            throw error;
        }
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    public void init() {
        Window.setSize(720, 500);
        check(Window.getWidth() == 720 && Window.getHeight() == 500, "Window resize");
        Window.setResizable(false);
        check(!Window.isResizable(), "Resize lock");
        Window.setResizable(true);
        check(Window.isResizable(), "Resize unlock");
        Window.setBorderless(true);
        check(Window.isBorderless(), "Borderless");
        Window.setPosition(10, 20);
        Window.center();
        Window.setSizeLimits(320, 240, 1024, 768);
        Window.setOpacity(.8f);
        Window.setOpacity(1);
        Window.setAlwaysOnTop(false);
        var dimension = Window.getDimensional();
        check(dimension.getX() == 0 && dimension.getY() == 0, "Content origin");
        dimension.setSize(640, 480);
        check(dimension.getWidth() == 640 && dimension.getHeight() == 480, "Dimensional adapter");
        ByteBuffer pixels = ByteBuffer.allocateDirect(16 * 16 * 4);
        while (pixels.hasRemaining()) pixels.putInt(-1);
        pixels.flip();
        TextureData image = new TextureData(pixels, 16, 16);
        Window.setIcon(image);
        Mouse.setCursor(image, 8, 8);
        Mouse.setCursorMode(Mouse.CURSOR_HIDDEN);
        Mouse.setCursorMode(Mouse.CURSOR_NORMAL);
        Mouse.setCursor(Mouse.CURSOR_ARROW);
        image.dispose();
        System.out.println("COMMON_WINDOW_READY");
    }

    public void update(float dt) {
        if (++frames == 3) {
            int[] size = Window.getFramebufferSize();
            check(size[0] > 0 && size[1] > 0, "Framebuffer size");
        }
        if (frames >= 60) Window.requestClose();
    }

    public void render() {
        Window.clear(Color.NAVY);
    }

    public void dispose() {
        System.out.println("COMMON_WINDOW_VALIDATED checks=" + checks);
    }
}
