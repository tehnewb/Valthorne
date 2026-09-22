package compatibility;

import valthorne.*;
import valthorne.audio.AudioFormat;
import valthorne.audio.sound.*;
import valthorne.graphics.Color;
import valthorne.io.file.ValthorneFiles;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Shared playback, seeking, PCM, queued streaming, attenuation and lifetime checks.
 */
public final class CommonAudioApplication implements Application {
    private SoundPlayer player, streamed;
    private int frames, checks;
    private boolean tested;
    private long begin;

    static void main(String[] args) {
        try {
            JGL.init(new CommonAudioApplication(), JGLConfiguration.defaults().title("Shared audio compatibility").size(640, 480).visible(false));
            System.out.println("COMMON_AUDIO_RETURNED");
        } catch (Throwable error) {
            error.printStackTrace();
            throw error;
        }
    }

    private static byte[] wave() {
        int rate = 22050, count = rate * 2;
        ByteBuffer b = ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x46464952).putInt(36 + count * 2).putInt(0x45564157).putInt(0x20746d66).putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16).putInt(0x61746164).putInt(count * 2);
        for (int i = 0; i < count; i++) b.putShort((short) (Math.sin(i * 2 * Math.PI * 440 / rate) * 6000));
        return b.array();
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    public void init() {
        for (String format : new String[]{"mp3", "ogg"}) {
            String base = "portable/compatibility/assets/audio/";
            SoundData longSound = SoundData.load(base + "tone." + format);
            check(longSound.streaming() && longSound.data() == null && longSound.streamLength() > 0, "Long compressed audio stays streamed with encoded length: " + format);
            check(longSound.channels() == 2 && longSound.sampleRate() == 22050 && longSound.duration() > 30 && longSound.duration() < 61, "Compressed format and duration: " + format + " channels=" + longSound.channels() + " rate=" + longSound.sampleRate() + " duration=" + longSound.duration());
            try (SoundStream reader = longSound.openStream()) {
                ByteBuffer chunk = ByteBuffer.allocateDirect(4096);
                check(reader.read(chunk) > 0, "Compressed stream PCM: " + format);
                check(reader.seek(30) && reader.read(chunk) > 0, "Compressed stream seek: " + format);
                int energy = 0;
                while (chunk.hasRemaining()) energy |= chunk.get();
                check(energy != 0, "Compressed stream signal: " + format);
            }
            SoundData shortSound = SoundData.load(base + "short." + format);
            check(!shortSound.streaming() && shortSound.data() != null && shortSound.data().remaining() > 100000, "Short compressed buffering: " + format);
            try {
                SoundData fromBytes = SoundData.load(ValthorneFiles.readBytes("audio/tone." + format));
                check(fromBytes.streaming() && fromBytes.data() == null, "Encoded memory source stays streamed: " + format);
                try (SoundStream reader = fromBytes.openStream()) {
                    check(reader.read(ByteBuffer.allocateDirect(1024)) > 0, "Encoded memory stream read: " + format);
                }
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        }
        SoundData data = SoundData.load(wave());
        check(data.channels() == 1 && data.sampleRate() == 22050 && data.bitsPerSample() == 16, "PCM format");
        check(Math.abs(data.duration() - 2) < .01, "Duration");
        check(!data.streaming(), "Short audio buffering");
        check(data.format() == AudioFormat.WAV, "Format detection");
        SoundMetadata metadata = WaveSoundDecoder.probe(wave());
        check(metadata.dataOffset() == 44 && metadata.dataLength() == 88200, "Encoded payload metadata");
        boolean rejected = false;
        try {
            OggSoundDecoder.probe(wave());
        } catch (Exception expected) {
            rejected = true;
        }
        check(rejected, "OGG rejects WAV data");
        try {
            check(Mp3SoundDecoder.probe(wave()).dataLength() == wave().length, "AudioSystem probe payload metadata");
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
        player = Audio.create(data);
        player.setVolume(.25f);
        player.setPitch(1.25f);
        player.setLooping(true);
        check(Math.abs(player.getVolume() - .25) < .001, "Gain");
        check(player.getPitch() == 1.25f, "Pitch");
        check(player.isLooping(), "Looping");
        player.play();
        player.pause();
        player.setCurrentTime(.5f);
        check(Math.abs(player.getCurrentTime() - .5) < .01, "Seek");
        player.mute();
        check(player.isMuted() && player.getVolume() == 0, "Mute");
        player.unmute();
        check(!player.isMuted() && Math.abs(player.getVolume() - .25) < .01, "Unmute");
        player.setArea(SoundArea.circle(0, 0, 2, 2));
        Audio.setListenerPosition(10, 0, 0);
        player.update();
        check(player.getEffectiveVolume() == 0, "Area outside");
        Audio.setListenerPosition(0, 0, 0);
        player.update();
        check(Math.abs(player.getEffectiveVolume() - .25) < .01, "Area inside");
        SoundData stream = new SoundData(new SoundSource.BytesSource(wave()), null, 44, 88200, 2, 1, 22050, 16, true, false, AudioFormat.WAV);
        try (SoundStream reader = stream.openStream()) {
            ByteBuffer chunk = ByteBuffer.allocate(256);
            check(reader.read(chunk) == 256, "Stream read");
            check(reader.seek(1), "Stream seek");
            check(reader.read(chunk) == 256, "Stream read after seek");
        }
        streamed = Audio.create(stream);
        streamed.setVolume(.1f);
        streamed.setLooping(true);
        player.playFromStart();
        streamed.play();
        check(player.isPlaying() && streamed.isPlaying(), "Playback starts");
        begin = System.nanoTime();
        System.out.println("COMMON_AUDIO_READY");
    }

    public void update(float dt) {
        frames++;
        if (frames == 80) {
            player.pause();
            check(player.isPaused(), "Pause");
            player.resume();
            check(player.isPlaying(), "Resume");
            player.setProgress(.5f);
            check(Math.abs(player.getProgress() - .5) < .02, "Progress seek");
            tested = true;
        }
        if (frames == 140) {
            check(streamed.isPlaying(), "Queued playback remains active");
            player.stop();
            check(player.isStopped(), "Stop");
            streamed.setCurrentTime(.5f);
            check(Math.abs(streamed.getCurrentTime() - .5f) < .03, "Queued seek");
        }
        if (frames >= 220) Window.requestClose();
    }

    public void render() {
        Window.clear(Color.NAVY);
    }

    public void dispose() {
        if (player != null) {
            player.dispose();
            player.dispose();
        }
        if (streamed != null) streamed.dispose();
        check(tested, "Lifecycle reached playback checks");
        System.out.println("COMMON_AUDIO_VALIDATED checks=" + checks + " elapsedMs=" + (System.nanoTime() - begin) / 1e6);
    }
}
