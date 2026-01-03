import valthorne.Application;
import valthorne.JGL;
import valthorne.Window;
import valthorne.asset.Assets;
import valthorne.graphics.font.Font;
import valthorne.graphics.font.FontData;
import valthorne.sound.SoundData;
import valthorne.sound.SoundParameters;
import valthorne.sound.SoundPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AssetTest implements Application {

    private Font font;
    private SoundPlayer player;

    @Override
    public void init() {
        try {
            this.font = new Font(FontData.load(Files.readAllBytes(Paths.get("./src/test/resources/test-font.ttf")), 32, 0, 254));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Assets.prepare(new SoundParameters("src/test/resources/bulk/Retro Instrument - crystal - C00.wav"));
        Assets.prepare(new SoundParameters("src/test/resources/bulk/Retro Instrument - crystal - C01.wav"));
        Assets.prepare(new SoundParameters("src/test/resources/bulk/Retro Instrument - crystal - C02.wav"));
        Assets.prepare(new SoundParameters("src/test/resources/bulk/Retro Instrument - crystal - C03.wav"));
        Assets.prepare(new SoundParameters("src/test/resources/bulk/Retro Instrument - crystal - C04.wav"));
        Assets.prepare(new SoundParameters("src/test/resources/bulk/Retro Instrument - crystal - C05.wav"));
        Assets.prepare(new SoundParameters("src/test/resources/bulk/Retro Instrument - crystal - C06.wav"));
        Assets.prepare(new SoundParameters("src/test/resources/bulk/Retro Instrument - crystal - C07.wav"));
        Assets.prepare(new SoundParameters("src/test/resources/bulk/Retro Instrument - crystal - C08.wav"));
        Assets.prepare(new SoundParameters("src/test/resources/bulk/Retro Instrument - crystal - C09.wav"));
        Assets.prepare(new SoundParameters("src/test/resources/bulk/Retro Instrument - crystal - C10.wav"));
        Assets.prepare(new SoundParameters("src/test/resources/bulk/Retro Instrument - crystal - C11.wav"));
        Assets.prepare(new SoundParameters("src/test/resources/bulk/Retro Instrument - crystal - C12.wav"));

        Assets.load().whenComplete((_, _) -> {
            SoundData data = Assets.get("src/test/resources/bulk/Retro Instrument - crystal - C12.wav", SoundData.class);
            if (data == null)
                throw new RuntimeException("Failed to load sound");
            this.player = new SoundPlayer(data);
            this.player.play();

        });
    }

    @Override
    public void update(float delta) {
        int progress = (int) (Assets.getProgress() * 100f);
        font.setText("Loading - " + progress + "%");
        font.setPosition((Window.getWidth() - font.getWidth()) / 2f, (Window.getHeight() - font.getHeight()) / 2f);
    }

    @Override
    public void render() {
        font.draw();
    }

    @Override
    public void dispose() {
        Assets.shutdown();
    }

    public static void main(String[] args) {
        JGL.init(new AssetTest(), "Asset Test", 1280, 720);
    }
}
