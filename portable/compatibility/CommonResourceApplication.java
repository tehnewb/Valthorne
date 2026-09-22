package compatibility;

import valthorne.Application;
import valthorne.JGL;
import valthorne.JGLConfiguration;
import valthorne.Window;
import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureData;
import valthorne.io.file.ValthorneFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class CommonResourceApplication implements Application {
    private int frames, checks;

    static void main(String[] args) {
        try {
            JGL.init(new CommonResourceApplication(), JGLConfiguration.defaults().title("Shared resource loading").size(640, 480).visible(false));
            System.out.println("COMMON_RESOURCE_RETURNED");
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }
    }

    private void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    public void init() {
        String resource = "valthorne/shaders/lighting2d/light.frag";
        String shader = ValthorneFiles.readString(resource);
        check(shader.contains("u_shadows"), "Resource content");
        check(ValthorneFiles.exists(resource), "Existing resource lookup");
        check(!ValthorneFiles.exists("absent-test-resource"), "Missing resource lookup");
        try (var input = CommonResourceApplication.class.getResourceAsStream("/" + resource)) {
            check(input != null && Arrays.equals(input.readAllBytes(), ValthorneFiles.readBytes(resource)), "Classpath lookup");
            Path temporary = ValthorneFiles.extractToTempFile(resource);
            check(Arrays.equals(Files.readAllBytes(temporary), ValthorneFiles.readBytes(resource)), "Temporary resource extraction");
            Files.delete(temporary);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("COMMON_RESOURCE_READY");
        try {
            Path image = ValthorneFiles.extractToTempFile("colors.png");
            var data = TextureData.load(image.toString());
            check(data.width() == 2 && data.height() == 2, "Packaged application resource decoded from temporary path");
            data.dispose();
            Files.delete(image);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void update(float dt) {
        if (++frames >= 90) Window.requestClose();
    }

    public void render() {
        Window.clear(Color.NAVY);
    }

    public void dispose() {
        System.out.println("COMMON_RESOURCE_VALIDATED checks=" + checks);
    }
}
