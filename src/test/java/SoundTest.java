import valthorne.Application;
import valthorne.Audio;
import valthorne.JGL;
import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.sound.SoundPlayer;

public class SoundTest implements Application {

    private SoundPlayer sound1;
    private SoundPlayer sound2;
    private SoundPlayer sound3;

    private SoundPlayer active;  // <-- currently selected sound

    public static void main(String[] args) {
        JGL.init(new SoundTest(), "SoundPlayer Multi-Sound Test", 1280, 720);
    }

    @Override
    public void init() {
        sound1 = Audio.load("src/test/resources/test-sound-4.mp3");
        sound2 = Audio.load("src/test/resources/test-sound-2.ogg");
        sound3 = Audio.load("src/test/resources/test-sound.wav");
        // default selection
        active = sound2;

        JGL.subscribe(KeyPressEvent.class, event -> {
            int key = event.getKey();

            switch (key) {

                // -------------------------
                // SELECT SOUND (1–3)
                // -------------------------
                case Keyboard.KEY_1:
                    active = sound1;
                    System.out.println("Selected sound 1");
                    break;

                case Keyboard.KEY_2:
                    active = sound2;
                    System.out.println("Selected sound 2");
                    break;

                case Keyboard.KEY_3:
                    active = sound3;
                    System.out.println("Selected sound 3");
                    break;

                // -------------------------
                // PLAY
                // -------------------------
                case Keyboard.P:
                    System.out.println("Play");
                    active.play();
                    break;

                // -------------------------
                // STOP
                // -------------------------
                case Keyboard.S:
                    System.out.println("Stop");
                    active.stop();
                    break;

                // -------------------------
                // PAUSE
                // -------------------------
                case Keyboard.O:
                    System.out.println("Pause");
                    active.pause();
                    break;

                // -------------------------
                // RESUME
                // -------------------------
                case Keyboard.I:
                    System.out.println("Resume");
                    active.resume();
                    break;

                // -------------------------
                // TOGGLE LOOPING
                // -------------------------
                case Keyboard.L:
                    boolean loop = !active.isLooping();
                    active.setLooping(loop);
                    System.out.println("Looping = " + loop);
                    break;

                // -------------------------
                // SEEK BACKWARD 1S
                // -------------------------
                case Keyboard.J:
                    float back = Math.max(0f, active.getCurrentTime() - 1f);
                    active.setCurrentTime(back);
                    System.out.println("Jump backward → " + back);
                    break;

                // -------------------------
                // SEEK FORWARD 1S
                // -------------------------
                case Keyboard.K:
                    float forward = Math.min(active.duration(), active.getCurrentTime() + 1f);
                    active.setCurrentTime(forward);
                    System.out.println("Jump forward → " + forward);
                    break;

                // -------------------------
                // PRINT STATUS
                // -------------------------
                case Keyboard.T:
                    System.out.println(
                            "Status → playing=" + active.isPlaying() +
                                    " paused=" + active.isPaused() +
                                    " stopped=" + active.isStopped() +
                                    " time=" + active.getCurrentTime() +
                                    " / " + active.duration()
                    );
                    break;

                // -------------------------
                // VOLUME UP
                // -------------------------
                case Keyboard.UP:
                    active.setVolume(active.getVolume() + 0.1f);
                    System.out.println("Volume = " + active.getVolume());
                    break;

                // -------------------------
                // VOLUME DOWN
                // -------------------------
                case Keyboard.DOWN:
                    active.setVolume(active.getVolume() - 0.1f);
                    System.out.println("Volume = " + active.getVolume());
                    break;

                // -------------------------
                // PITCH UP
                // -------------------------
                case Keyboard.RIGHT:
                    active.setPitch(active.getPitch() + 0.1f);
                    System.out.println("Pitch = " + active.getPitch());
                    break;

                // -------------------------
                // PITCH DOWN
                // -------------------------
                case Keyboard.LEFT:
                    active.setPitch(active.getPitch() - 0.1f);
                    System.out.println("Pitch = " + active.getPitch());
                    break;
            }
        });
    }

    @Override
    public void update(float delta) {
        // event-driven, nothing needed here
    }

    @Override
    public void render() {
        // visual feedback optional
    }

    @Override
    public void dispose() {
        if (sound1 != null) sound1.dispose();
        if (sound2 != null) sound2.dispose();
        if (sound3 != null) sound3.dispose();
    }
}
