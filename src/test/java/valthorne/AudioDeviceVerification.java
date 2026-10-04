package valthorne;

import valthorne.audio.SoundPlayer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.lwjgl.openal.ALC10.alcGetCurrentContext;

/**
 * Native regression checks for output switching with buffered and streamed silent
 * WAV players. Exercises available endpoints, default selection, failed reopen,
 * paused and playing states, retained context ownership and subsystem restart.
 * Run this entry point with the test runtime classpath on a machine with a working
 * playback endpoint.
 */
public final class AudioDeviceVerification {

    /**
     * Prevents construction of this command-line verification entry point.
     */
    private AudioDeviceVerification() {
    }

    /**
     * Runs the checks using real OpenAL resources and always releases audio.
     *
     * @param arguments unused command-line arguments
     */
    public static void main(String[] arguments) {
        /*
         * Silent PCM permits real endpoint changes without generating sound;
         * the longer WAV crosses the loader's streaming duration threshold.
         */
        try {
            List<String> devices = Audio.getDevices();
            System.out.println("Playback devices: " + devices);
            require(Audio.getCurrentDevice() != null, "Current endpoint missing");
            require(Audio.getDefaultDevice() != null, "Default endpoint missing");
            for (String invalid : new String[]{"", "  ", "device\0suffix"}) {
                try {
                    Audio.switchDevice(invalid);
                    throw new AssertionError("Invalid name accepted");
                } catch (IllegalArgumentException expected) {
                    require(expected.getMessage() != null, "Validation must explain the failure");
                }
            }

            SoundPlayer buffered = Audio.load(silentWave(1));
            SoundPlayer streamed = Audio.load(silentWave(16));
            require(!buffered.getData().streaming(), "Buffered fixture must not stream");
            require(streamed.getData().streaming(), "Streaming fixture must stream");
            buffered.setLooping(true);
            buffered.setVolume(0.25f);
            buffered.play();
            streamed.play();
            streamed.pause();
            long context = Audio.call(() -> alcGetCurrentContext());

            if (Audio.isDeviceSwitchingSupported()) {
                for (String name : devices) {
                    require(Audio.switchDevice(name), "Could not select " + name);
                    require(name.equals(Audio.getCurrentDevice()), "Selected endpoint mismatch");
                    verifyPlayers(buffered, streamed, context);
                }
                require(Audio.switchDevice(null), "Default endpoint switch failed");
                verifyPlayers(buffered, streamed, context);
                String previous = Audio.getCurrentDevice();
                require(!Audio.switchDevice("Valthorne nonexistent endpoint 83ba2960"), "Unknown endpoint accepted");
                require(previous.equals(Audio.getCurrentDevice()), "Failed switch changed endpoint");
                verifyPlayers(buffered, streamed, context);
                streamed.resume();
                buffered.pause();
                require(Audio.switchDevice(null), "Second default switch failed");
                require(streamed.isPlaying() && buffered.isPaused(), "Swapped playback states lost");
            } else {
                require(!Audio.switchDevice(null), "Unsupported driver accepted switch");
                verifyPlayers(buffered, streamed, context);
                System.out.println("Driver does not support resource-preserving switching");
            }
            Audio.destroy(buffered);
            Audio.destroy(streamed);
            Audio.dispose();
            require(Audio.getCurrentDevice() != null, "Restart failed");
            System.out.println("Audio device verification passed");
        } finally {
            Audio.dispose();
        }
    }

    /**
     * Verifies that switching retained the native context and configured players.
     *
     * @param buffered looping buffered player
     * @param streamed paused streaming player
     * @param context original native context handle
     */
    private static void verifyPlayers(SoundPlayer buffered, SoundPlayer streamed, long context) {
        /*
         * Playback queries access the actual OpenAL sources, detecting stale
         * handles as well as Java-side settings lost during a destructive switch.
         */
        require(Audio.call(() -> alcGetCurrentContext()) == context, "Context replaced");
        require(buffered.isPlaying(), "Buffered playback stopped");
        require(streamed.isPaused(), "Paused stream state lost");
        require(buffered.isLooping(), "Looping setting lost");
        require(buffered.getVolume() == 0.25f, "Volume setting lost");
    }

    /**
     * Creates an uncompressed mono 16-bit, 8 kHz WAV with zero-valued PCM samples.
     *
     * @param seconds fixture duration in whole seconds
     * @return encoded WAV bytes
     */
    private static byte[] silentWave(int seconds) {
        /*
         * A minimal PCM header exercises the production WAV loader without
         * external assets; zero-initialized payload bytes represent silence.
         */
        int length = seconds * 8000 * 2;
        ByteBuffer wave = ByteBuffer.allocate(44 + length)
                .order(ByteOrder.LITTLE_ENDIAN);
        wave.putInt(0x46464952)
                .putInt(36 + length)
                .putInt(0x45564157);
        wave.putInt(0x20746d66)
                .putInt(16)
                .putShort((short) 1)
                .putShort((short) 1);
        wave.putInt(8000)
                .putInt(16000)
                .putShort((short) 2)
                .putShort((short) 16);
        wave.putInt(0x61746164)
                .putInt(length);
        return wave.array();
    }

    /**
     * Fails the verification when a required invariant is not satisfied.
     *
     * @param condition invariant result
     * @param message diagnostic explaining the failure
     */
    private static void require(boolean condition, String message) {
        /*
         * Explicit failures remain active even when JVM assertions are disabled.
         */
        if (!condition) throw new AssertionError(message);
    }
}
