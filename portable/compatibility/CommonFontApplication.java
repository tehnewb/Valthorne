package compatibility;

import valthorne.Application;
import valthorne.JGL;
import valthorne.JGLConfiguration;
import valthorne.Window;
import valthorne.graphics.Color;
import valthorne.graphics.font.Font;
import valthorne.graphics.font.FontData;
import valthorne.graphics.texture.TextureBatch;

import java.io.IOException;

public final class CommonFontApplication implements Application {
    private FontData data;
    private Font font;
    private TextureBatch batch;
    private int frames, checks;

    static void main(String[] args) {
        try {
            JGL.init(new CommonFontApplication(), JGLConfiguration.defaults().title("Shared font rendering").size(640, 480).visible(false));
            System.out.println("COMMON_FONT_RETURNED");
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
        try (var resource = CommonFontApplication.class.getResourceAsStream("/ui/AtkinsonHyperlegible-Regular.ttf")) {
            if (resource == null) throw new AssertionError("Classpath font missing");
            data = FontData.load(resource.readAllBytes(), 32, 32, 224);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        check(data.contains('A') && !data.contains('\n'), "Character range");
        check(data.glyph('A').width() > 0, "Glyph bounds");
        check(data.ascent() > 0 && data.descent() < 0, "Font metrics");
        check(Float.isFinite(data.getKerningAdvance('A', 'V')), "Kerning");
        font = new Font(data);
        font.setText("Valthorne on the web\nTextures, fonts, physics and light");
        font.setPosition(25, 250);
        check(font.getWidth() > 200 && font.getHeight() > 32, "Multiline layout");
        batch = new TextureBatch(256);
        System.out.println("COMMON_FONT_READY");
    }

    public void update(float dt) {
        if (++frames >= 180) Window.requestClose();
    }

    public void render() {
        Window.clear(Color.NAVY);
        batch.begin();
        font.draw(batch);
        batch.end();
    }

    public void dispose() {
        if (font != null) font.dispose();
        if (data != null) data.dispose();
        if (batch != null) batch.dispose();
        System.out.println("COMMON_FONT_VALIDATED checks=" + checks);
    }
}
