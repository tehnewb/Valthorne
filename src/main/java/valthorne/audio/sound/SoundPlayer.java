package valthorne.audio.sound;

import org.lwjgl.BufferUtils;
import valthorne.Audio;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL11.AL_SEC_OFFSET;

/**
 * <p>
 * {@code SoundPlayer} is Valthorne's high-level playback controller for a single
 * {@link SoundData} instance. It wraps one OpenAL source and handles all of the
 * state and operations needed to play, pause, stop, seek, loop, mute, change
 * playback speed, and dispose audio safely.
 * </p>
 *
 * <p>
 * This class supports two different playback models:
 * </p>
 *
 * <ul>
 *     <li><strong>Fully buffered playback</strong>, where the decoded audio is uploaded
 *     once into a single OpenAL buffer and then played directly from that buffer.</li>
 *     <li><strong>Streaming playback</strong>, where audio is read incrementally from a
 *     {@link SoundStream}, filled into a rotating set of OpenAL buffers, and queued
 *     onto the source as playback progresses.</li>
 * </ul>
 *
 * <p>
 * The playback mode is determined by {@link SoundData#streaming()}. If the sound data
 * is not streaming, this player creates one normal OpenAL buffer and binds it to the
 * source. If the sound data is streaming, this player creates a small pool of stream
 * buffers and continuously refills them as processed buffers are returned by OpenAL.
 * </p>
 *
 * <p>
 * All OpenAL work must happen on the audio thread owned by {@link Audio}. To enforce
 * that safely, public methods route work through {@link Audio#sync(Runnable)} or
 * {@link Audio#call(java.util.concurrent.Callable)} when needed. If the caller is
 * already on the audio thread, work executes immediately. This means the class is
 * convenient to use from normal game code while still preserving proper OpenAL
 * threading rules.
 * </p>
 *
 * <p>
 * In addition to normal playback controls, this class also provides:
 * </p>
 *
 * <ul>
 *     <li>time seeking in seconds</li>
 *     <li>progress-based seeking</li>
 *     <li>relative seek operations</li>
 *     <li>wording-friendly helpers like {@link #restart()}, {@link #skipToStart()},
 *     {@link #skipToEnd()}, {@link #stepForward()}, and {@link #stepBackward()}</li>
 *     <li>volume and mute controls</li>
 *     <li>pitch-based playback speed changes</li>
 *     <li>automatic stream rewinding and loop refill logic for streamed audio</li>
 * </ul>
 *
 * <p>
 * For streaming sounds, {@link #update()} must be called from the audio system so
 * processed buffers can be unqueued, timed correctly, refilled, and requeued. In
 * Valthorne, that update lifecycle is handled by {@link Audio}, so engine users
 * normally do not need to update each player manually.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * SoundPlayer player = Audio.load("audio/music/theme.ogg");
 *
 * player.setLooping(true);
 * player.setVolume(0.65f);
 * player.play();
 *
 * float duration = player.duration();
 * float progress = player.getProgress();
 *
 * player.fastForward(10f);
 * player.pause();
 * player.resume();
 *
 * player.setPlaybackSpeed(1.25f);
 * player.toggleMute();
 * player.stop();
 *
 * player.dispose();
 * }</pre>
 *
 * <p>
 * This example demonstrates the full intended use of the class: creation through
 * the audio system, playback control, seeking, playback-speed changes, mute control,
 * state inspection, and disposal.
 * </p>
 *
 * @author Albert Beaupre
 * @since December 5th, 2025
 */
public class SoundPlayer {

    /**
     * Number of rotating OpenAL buffers used for streamed playback.
     */
    private static final int STREAM_BUFFER_COUNT = 4;

    /**
     * Size in bytes of each temporary stream chunk buffer used when refilling streamed audio.
     */
    private static final int STREAM_BUFFER_SIZE = 64 * 1024;

    private final SoundData data; // Sound data definition backing this player
    private final int source; // OpenAL source used to perform playback
    private final int buffer; // Single OpenAL buffer used for non-streaming playback
    private final boolean streaming; // Whether this player is operating in streaming mode
    private final int[] streamBuffers; // OpenAL buffer handles used for streamed playback
    private final float[] streamBufferDurations; // Duration in seconds stored in each stream buffer
    private final ByteBuffer streamChunk; // Temporary byte buffer used when reading streamed audio chunks

    private SoundStream stream; // Active stream reader used when streaming playback is enabled
    private boolean wantPlaying; // Whether streamed playback logically wants to remain playing
    private boolean looping; // Whether looping is enabled for this player
    private float streamedSeconds; // Number of seconds already consumed from processed stream buffers
    private float storedVolume = 1f; // Saved volume used to restore state after unmuting
    private boolean muted; // Whether this player is currently considered muted

    /**
     * <p>
     * Creates a new sound player for the supplied {@link SoundData}.
     * </p>
     *
     * <p>
     * This constructor immediately allocates an OpenAL source. From there, one of two
     * initialization paths is taken:
     * </p>
     *
     * <ul>
     *     <li>If the data is streaming, the player allocates the rotating stream buffer
     *     set, creates the temporary chunk buffer, and primes the stream through
     *     {@link #resetStream(float)}.</li>
     *     <li>If the data is not streaming, the player allocates a single OpenAL buffer,
     *     copies the PCM data into it, and binds that buffer directly to the source.</li>
     * </ul>
     *
     * <p>
     * After the source and buffers are prepared, the player applies its initial default
     * state of full volume, normal pitch, and looping disabled.
     * </p>
     *
     * @param data the sound data that this player will control
     */
    public SoundPlayer(SoundData data) {
        this.data = data;
        this.source = alGenSources();
        this.streaming = data.streaming();

        if (streaming) {
            this.buffer = 0;
            this.streamBuffers = new int[STREAM_BUFFER_COUNT];
            this.streamBufferDurations = new float[STREAM_BUFFER_COUNT];
            this.streamChunk = BufferUtils.createByteBuffer(STREAM_BUFFER_SIZE);
            this.stream = null;

            for (int i = 0; i < streamBuffers.length; i++) {
                streamBuffers[i] = alGenBuffers();
            }

            resetStream(0f);
        } else {
            this.streamBuffers = null;
            this.streamBufferDurations = null;
            this.streamChunk = null;
            this.stream = null;
            this.buffer = alGenBuffers();

            int format = data.channels() == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;
            ByteBuffer pcm = data.data().duplicate();
            pcm.position(0);
            alBufferData(buffer, format, pcm, data.sampleRate());
            alSourcei(source, AL_BUFFER, buffer);
        }

        setVolumeInternal(1f);
        setPitchInternal(1f);
        setLoopingInternal(false);
    }

    /**
     * <p>
     * Updates streamed playback state.
     * </p>
     *
     * <p>
     * This method does nothing for fully buffered sounds. For streamed sounds, it must
     * run on the audio thread and is responsible for:
     * </p>
     *
     * <ul>
     *     <li>unqueueing processed stream buffers</li>
     *     <li>advancing {@link #streamedSeconds}</li>
     *     <li>re-filling buffers with new audio data</li>
     *     <li>re-queueing buffers onto the source</li>
     *     <li>resuming playback if the player still wants to be playing and buffers exist</li>
     * </ul>
     *
     * <p>
     * In normal Valthorne usage, this is called automatically by the global audio
     * subsystem.
     * </p>
     */
    public void update() {
        if (Audio.isAudioThread()) {
            updateInternal();
        }
    }

    /**
     * <p>
     * Starts playback of this sound.
     * </p>
     *
     * <p>
     * For streamed sounds, this may also re-prime the stream if no playable buffers
     * are currently queued. For non-streaming sounds, it simply plays the source.
     * </p>
     */
    public void play() {
        execute(this::playInternal);
    }

    /**
     * <p>
     * Pauses playback of this sound.
     * </p>
     *
     * <p>
     * For streamed sounds, this also clears the logical desire to keep playing until
     * a future resume or play request happens.
     * </p>
     */
    public void pause() {
        execute(this::pauseInternal);
    }

    /**
     * <p>
     * Resumes playback after a pause.
     * </p>
     *
     * <p>
     * For streamed sounds, this also ensures that the stream still has playable
     * buffers available and re-primes them if necessary before playback resumes.
     * </p>
     */
    public void resume() {
        execute(this::resumeInternal);
    }

    /**
     * <p>
     * Stops playback of this sound.
     * </p>
     *
     * <p>
     * For streamed sounds, stopping also resets the stream back to the beginning.
     * For fully buffered sounds, the source is stopped and then rewound.
     * </p>
     */
    public void stop() {
        execute(this::stopInternal);
    }

    /**
     * <p>
     * Rewinds the sound back to the beginning without automatically starting playback.
     * </p>
     *
     * <p>
     * For streamed sounds, this is implemented through seeking to time zero. For
     * non-streaming sounds, it directly rewinds the OpenAL source.
     * </p>
     */
    public void rewind() {
        execute(this::rewindInternal);
    }

    /**
     * <p>
     * Returns whether the source is currently in the OpenAL playing state.
     * </p>
     *
     * @return {@code true} if the source is currently playing
     */
    public boolean isPlaying() {
        return query(this::isPlayingInternal);
    }

    /**
     * <p>
     * Returns whether the source is currently paused.
     * </p>
     *
     * @return {@code true} if the source is currently paused
     */
    public boolean isPaused() {
        return query(this::isPausedInternal);
    }

    /**
     * <p>
     * Returns whether the source is currently stopped or still in its initial state.
     * </p>
     *
     * @return {@code true} if the source is not actively playing or paused
     */
    public boolean isStopped() {
        return query(this::isStoppedInternal);
    }

    /**
     * <p>
     * Returns the full duration of the underlying sound data in seconds.
     * </p>
     *
     * @return the total sound duration in seconds
     */
    public float duration() {
        return data.duration();
    }

    /**
     * <p>
     * Returns the current playback position in seconds.
     * </p>
     *
     * <p>
     * For fully buffered sounds, this is read directly from OpenAL. For streamed
     * sounds, the value is reconstructed from {@link #streamedSeconds} plus the
     * source's current buffer offset.
     * </p>
     *
     * @return the current playback position in seconds
     */
    public float getCurrentTime() {
        return query(this::getCurrentTimeInternal);
    }

    /**
     * <p>
     * Sets the current playback position in seconds.
     * </p>
     *
     * <p>
     * The requested time is clamped to a valid range before being applied. For streamed
     * sounds, seeking rebuilds stream state and may resume or re-pause playback
     * depending on the state before the seek.
     * </p>
     *
     * @param seconds the desired playback position in seconds
     */
    public void setCurrentTime(float seconds) {
        execute(() -> setCurrentTimeInternal(seconds));
    }

    /**
     * <p>
     * Returns the current effective source gain.
     * </p>
     *
     * @return the current source volume in the {@code [0, 1]} range
     */
    public float getVolume() {
        return query(this::getVolumeInternal);
    }

    /**
     * <p>
     * Sets the source gain.
     * </p>
     *
     * <p>
     * The value is clamped into the {@code [0, 1]} range before being applied to
     * OpenAL.
     * </p>
     *
     * @param volume the desired volume
     */
    public void setVolume(float volume) {
        execute(() -> setVolumeInternal(volume));
    }

    /**
     * <p>
     * Returns the current source pitch.
     * </p>
     *
     * @return the current pitch value
     */
    public float getPitch() {
        return query(this::getPitchInternal);
    }

    /**
     * <p>
     * Sets the source pitch.
     * </p>
     *
     * <p>
     * Pitch is clamped into a safe playback range before it is applied.
     * </p>
     *
     * @param pitch the desired pitch value
     */
    public void setPitch(float pitch) {
        execute(() -> setPitchInternal(pitch));
    }

    /**
     * <p>
     * Returns whether looping is enabled for this player.
     * </p>
     *
     * <p>
     * For streamed sounds, the value is tracked manually. For non-streaming sounds,
     * it is also mirrored into the OpenAL source state.
     * </p>
     *
     * @return {@code true} if looping is enabled
     */
    public boolean isLooping() {
        return query(this::isLoopingInternal);
    }

    /**
     * <p>
     * Enables or disables looping for this player.
     * </p>
     *
     * @param looping whether looping should be enabled
     */
    public void setLooping(boolean looping) {
        execute(() -> setLoopingInternal(looping));
    }

    /**
     * <p>
     * Rewinds the player to the beginning and starts playback immediately.
     * </p>
     */
    public void playFromStart() {
        execute(this::playFromStartInternal);
    }

    /**
     * <p>
     * Seeks to the given playback position and starts playback immediately.
     * </p>
     *
     * @param seconds the playback position in seconds to start from
     */
    public void playFrom(float seconds) {
        execute(() -> playFromInternal(seconds));
    }

    /**
     * <p>
     * Restarts playback from the beginning.
     * </p>
     *
     * <p>
     * This is a convenience alias for {@link #playFromStart()}.
     * </p>
     */
    public void restart() {
        execute(this::restartInternal);
    }

    /**
     * <p>
     * Moves playback forward by a relative number of seconds.
     * </p>
     *
     * @param seconds the amount of time to skip forward
     */
    public void fastForward(float seconds) {
        execute(() -> fastForwardInternal(seconds));
    }

    /**
     * <p>
     * Moves playback backward by a relative number of seconds.
     * </p>
     *
     * @param seconds the amount of time to move backward
     */
    public void rewind(float seconds) {
        execute(() -> rewindByInternal(seconds));
    }

    /**
     * <p>
     * Moves playback by a signed relative offset in seconds.
     * </p>
     *
     * <p>
     * Positive values move forward. Negative values move backward.
     * </p>
     *
     * @param seconds the relative seek amount
     */
    public void seekBy(float seconds) {
        execute(() -> seekByInternal(seconds));
    }

    /**
     * <p>
     * Jumps playback to the start of the sound.
     * </p>
     */
    public void skipToStart() {
        execute(this::skipToStartInternal);
    }

    /**
     * <p>
     * Jumps playback to the end of the sound.
     * </p>
     */
    public void skipToEnd() {
        execute(this::skipToEndInternal);
    }

    /**
     * <p>
     * Moves playback forward by the default step amount of five seconds.
     * </p>
     */
    public void stepForward() {
        execute(this::stepForwardInternal);
    }

    /**
     * <p>
     * Moves playback backward by the default step amount of five seconds.
     * </p>
     */
    public void stepBackward() {
        execute(this::stepBackwardInternal);
    }

    /**
     * <p>
     * Moves playback forward by a caller-defined step amount.
     * </p>
     *
     * @param seconds the step size in seconds
     */
    public void stepForward(float seconds) {
        execute(() -> stepForwardInternal(seconds));
    }

    /**
     * <p>
     * Moves playback backward by a caller-defined step amount.
     * </p>
     *
     * @param seconds the step size in seconds
     */
    public void stepBackward(float seconds) {
        execute(() -> stepBackwardInternal(seconds));
    }

    /**
     * <p>
     * Toggles between playing and paused states.
     * </p>
     */
    public void togglePlayPause() {
        execute(this::togglePlayPauseInternal);
    }

    /**
     * <p>
     * Toggles the looping flag.
     * </p>
     */
    public void toggleLooping() {
        execute(this::toggleLoopingInternal);
    }

    /**
     * <p>
     * Returns whether playback has fully finished.
     * </p>
     *
     * <p>
     * A looping sound is never considered finished. For streamed audio, this checks
     * that playback is no longer active, no playable buffers remain, and the playback
     * position has reached the duration.
     * </p>
     *
     * @return {@code true} if playback is complete
     */
    public boolean isFinished() {
        return query(this::isFinishedInternal);
    }

    /**
     * <p>
     * Returns the current playback progress as a normalized value in the
     * {@code [0, 1]} range.
     * </p>
     *
     * @return normalized playback progress
     */
    public float getProgress() {
        return query(this::getProgressInternal);
    }

    /**
     * <p>
     * Sets playback progress using a normalized value in the {@code [0, 1]} range.
     * </p>
     *
     * @param progress the desired normalized progress
     */
    public void setProgress(float progress) {
        execute(() -> setProgressInternal(progress));
    }

    /**
     * <p>
     * Moves playback forward by a percentage of the full sound duration.
     * </p>
     *
     * @param percent the normalized percentage of the duration to move forward
     */
    public void fastForwardPercent(float percent) {
        execute(() -> fastForwardPercentInternal(percent));
    }

    /**
     * <p>
     * Moves playback backward by a percentage of the full sound duration.
     * </p>
     *
     * @param percent the normalized percentage of the duration to move backward
     */
    public void rewindPercent(float percent) {
        execute(() -> rewindPercentInternal(percent));
    }

    /**
     * <p>
     * Mutes the player while remembering the current volume so it can be restored later.
     * </p>
     */
    public void mute() {
        execute(this::muteInternal);
    }

    /**
     * <p>
     * Restores the remembered volume and clears muted state.
     * </p>
     */
    public void unmute() {
        execute(this::unmuteInternal);
    }

    /**
     * <p>
     * Toggles the mute state.
     * </p>
     */
    public void toggleMute() {
        execute(this::toggleMuteInternal);
    }

    /**
     * <p>
     * Returns whether this player is currently muted.
     * </p>
     *
     * @return {@code true} if muted or effectively silent
     */
    public boolean isMuted() {
        return query(this::isMutedInternal);
    }

    /**
     * <p>
     * Raises volume by the given amount.
     * </p>
     *
     * @param amount the amount to increase volume by
     */
    public void volumeUp(float amount) {
        execute(() -> volumeUpInternal(amount));
    }

    /**
     * <p>
     * Lowers volume by the given amount.
     * </p>
     *
     * @param amount the amount to decrease volume by
     */
    public void volumeDown(float amount) {
        execute(() -> volumeDownInternal(amount));
    }

    /**
     * <p>
     * Sets playback speed by mapping the given speed directly to source pitch.
     * </p>
     *
     * @param speed the desired playback speed multiplier
     */
    public void setPlaybackSpeed(float speed) {
        execute(() -> setPlaybackSpeedInternal(speed));
    }

    /**
     * <p>
     * Returns the current playback speed value.
     * </p>
     *
     * <p>
     * Internally, playback speed is represented through OpenAL pitch.
     * </p>
     *
     * @return the current playback speed multiplier
     */
    public float getPlaybackSpeed() {
        return query(this::getPlaybackSpeedInternal);
    }

    /**
     * <p>
     * Resets playback speed to normal.
     * </p>
     */
    public void normalSpeed() {
        execute(this::normalSpeedInternal);
    }

    /**
     * <p>
     * Decreases playback speed by a small fixed step.
     * </p>
     */
    public void slower() {
        execute(this::slowerInternal);
    }

    /**
     * <p>
     * Increases playback speed by a small fixed step.
     * </p>
     */
    public void faster() {
        execute(this::fasterInternal);
    }

    /**
     * <p>
     * Disposes all resources owned by this player.
     * </p>
     *
     * <p>
     * This stops playback, releases OpenAL buffers and sources, closes any active
     * stream if present, and unregisters the player from {@link Audio}.
     * </p>
     */
    public void dispose() {
        execute(this::disposeInternal);
    }

    /**
     * <p>
     * Returns the underlying sound data object used by this player.
     * </p>
     *
     * @return the backing sound data
     */
    public SoundData getData() {
        return data;
    }

    /**
     * <p>
     * Performs the internal streaming update pass.
     * </p>
     *
     * <p>
     * This method is only meaningful for streaming sounds. It unqueues processed
     * buffers, advances consumed stream time, refills those buffers if more audio
     * is available, and requeues them. At the end, it checks whether the source
     * should still be playing and resumes the source if playback is desired and
     * playable buffers remain.
     * </p>
     */
    private void updateInternal() {
        if (!streaming) {
            return;
        }

        int processed = alGetSourcei(source, AL_BUFFERS_PROCESSED);

        while (processed-- > 0) {
            int processedBuffer = alSourceUnqueueBuffers(source);

            streamedSeconds += getStreamBufferDuration(processedBuffer);
            if (looping && data.duration() > 0f) {
                streamedSeconds %= data.duration();
            }

            setStreamBufferDuration(processedBuffer, 0f);

            if (fillStreamBuffer(processedBuffer)) {
                alSourceQueueBuffers(source, processedBuffer);
            }
        }

        int state = alGetSourcei(source, AL_SOURCE_STATE);
        int queued = alGetSourcei(source, AL_BUFFERS_QUEUED);
        int processedNow = alGetSourcei(source, AL_BUFFERS_PROCESSED);
        int playable = Math.max(0, queued - processedNow);

        if (playable <= 0) {
            wantPlaying = false;
            return;
        }

        if (wantPlaying && state != AL_PLAYING) {
            alSourcePlay(source);
        }
    }

    /**
     * <p>
     * Performs the internal play operation.
     * </p>
     *
     * <p>
     * For streaming playback, the method ensures there are playable buffers queued.
     * If not, the stream is reset from the current streamed position before playback
     * begins. For non-streaming playback, the source is played directly.
     * </p>
     */
    private void playInternal() {
        if (streaming) {
            int queued = alGetSourcei(source, AL_BUFFERS_QUEUED);
            int processed = alGetSourcei(source, AL_BUFFERS_PROCESSED);
            int playable = Math.max(0, queued - processed);

            if (playable <= 0) {
                resetStream(streamedSeconds);
            }

            wantPlaying = true;
            if (alGetSourcei(source, AL_BUFFERS_QUEUED) > 0) {
                alSourcePlay(source);
            }
            return;
        }

        alSourcePlay(source);
    }

    /**
     * <p>
     * Performs the internal pause operation.
     * </p>
     *
     * <p>
     * For streaming sounds, the desire to remain playing is cleared so update logic
     * does not automatically restart playback until explicitly asked.
     * </p>
     */
    private void pauseInternal() {
        if (streaming) {
            wantPlaying = false;
        }
        alSourcePause(source);
    }

    /**
     * <p>
     * Performs the internal resume operation.
     * </p>
     *
     * <p>
     * For streaming playback, the method ensures there are still playable buffers and
     * recreates stream state if necessary. It then marks the player as wanting to play
     * and resumes the source.
     * </p>
     */
    private void resumeInternal() {
        if (streaming) {
            int queued = alGetSourcei(source, AL_BUFFERS_QUEUED);
            int processed = alGetSourcei(source, AL_BUFFERS_PROCESSED);
            int playable = Math.max(0, queued - processed);

            if (playable <= 0) {
                resetStream(streamedSeconds);
            }

            wantPlaying = true;
        }
        alSourcePlay(source);
    }

    /**
     * <p>
     * Performs the internal stop operation.
     * </p>
     *
     * <p>
     * For streaming sounds, this stops the source, clears the play request, and resets
     * the stream back to time zero. For non-streaming sounds, the source is stopped
     * and rewound.
     * </p>
     */
    private void stopInternal() {
        if (streaming) {
            wantPlaying = false;
            alSourceStop(source);
            resetStream(0f);
            return;
        }

        alSourceStop(source);
        rewindInternal();
    }

    /**
     * <p>
     * Performs the internal rewind operation.
     * </p>
     *
     * <p>
     * Streaming sounds are rewound by seeking to zero. Fully buffered sounds are
     * rewound directly through OpenAL.
     * </p>
     */
    private void rewindInternal() {
        if (streaming) {
            setCurrentTimeInternal(0f);
            return;
        }

        alSourceRewind(source);
    }

    /**
     * <p>
     * Returns whether the source is in the OpenAL playing state.
     * </p>
     *
     * @return {@code true} if the source is playing
     */
    private boolean isPlayingInternal() {
        return alGetSourcei(source, AL_SOURCE_STATE) == AL_PLAYING;
    }

    /**
     * <p>
     * Returns whether the source is in the OpenAL paused state.
     * </p>
     *
     * @return {@code true} if the source is paused
     */
    private boolean isPausedInternal() {
        return alGetSourcei(source, AL_SOURCE_STATE) == AL_PAUSED;
    }

    /**
     * <p>
     * Returns whether the source is stopped or still in the initial state.
     * </p>
     *
     * @return {@code true} if the source is not currently active
     */
    private boolean isStoppedInternal() {
        int state = alGetSourcei(source, AL_SOURCE_STATE);
        return state == AL_STOPPED || state == AL_INITIAL;
    }

    /**
     * <p>
     * Computes the current playback time.
     * </p>
     *
     * <p>
     * For non-streamed sounds, OpenAL already tracks the position and it can be read
     * directly. For streamed sounds, the result is reconstructed by combining the
     * time represented by fully processed buffers with the source's current offset
     * within the active buffer.
     * </p>
     *
     * @return the current playback time in seconds
     */
    private float getCurrentTimeInternal() {
        if (!streaming) {
            return alGetSourcef(source, AL_SEC_OFFSET);
        }

        int state = alGetSourcei(source, AL_SOURCE_STATE);
        float offset = state == AL_PLAYING || state == AL_PAUSED ? alGetSourcef(source, AL_SEC_OFFSET) : 0f;
        float time = streamedSeconds + offset;

        if (looping && data.duration() > 0f) {
            return time % data.duration();
        }

        if (data.duration() > 0f) {
            return Math.min(time, data.duration());
        }

        return time;
    }

    /**
     * <p>
     * Sets the current playback time internally.
     * </p>
     *
     * <p>
     * For fully buffered sounds, this uses {@code AL_SEC_OFFSET}. For streamed sounds,
     * the method must rebuild the stream so playback can resume from the requested
     * position. If playback was active before the seek, the method restores either the
     * playing or paused state after the new stream is prepared.
     * </p>
     *
     * @param seconds the desired playback position in seconds
     */
    private void setCurrentTimeInternal(float seconds) {
        if (!streaming) {
            alSourcef(source, AL_SEC_OFFSET, clampTime(seconds));
            return;
        }

        float target = clampTime(seconds);
        boolean wasPlaying = isPlayingInternal();
        boolean wasPaused = isPausedInternal();

        wantPlaying = false;
        alSourceStop(source);
        resetStream(target);

        if (wasPlaying) {
            wantPlaying = true;
            if (alGetSourcei(source, AL_BUFFERS_QUEUED) > 0) {
                alSourcePlay(source);
            }
        } else if (wasPaused) {
            if (alGetSourcei(source, AL_BUFFERS_QUEUED) > 0) {
                alSourcePlay(source);
                alSourcePause(source);
            }
        }
    }

    /**
     * <p>
     * Returns the current source gain.
     * </p>
     *
     * @return the current volume value
     */
    private float getVolumeInternal() {
        return alGetSourcef(source, AL_GAIN);
    }

    /**
     * <p>
     * Sets the source gain.
     * </p>
     *
     * <p>
     * The supplied value is clamped into the range {@code [0, 1]} before being sent
     * to OpenAL.
     * </p>
     *
     * @param volume the desired volume value
     */
    private void setVolumeInternal(float volume) {
        alSourcef(source, AL_GAIN, Math.max(0f, Math.min(1f, volume)));
    }

    /**
     * <p>
     * Returns the current source pitch.
     * </p>
     *
     * @return the current pitch value
     */
    private float getPitchInternal() {
        return alGetSourcef(source, AL_PITCH);
    }

    /**
     * <p>
     * Sets the source pitch.
     * </p>
     *
     * <p>
     * The value is clamped into a conservative playback range to avoid invalid or
     * extreme values.
     * </p>
     *
     * @param pitch the desired pitch value
     */
    private void setPitchInternal(float pitch) {
        alSourcef(source, AL_PITCH, Math.max(0.1f, Math.min(8f, pitch)));
    }

    /**
     * <p>
     * Returns whether looping is enabled.
     * </p>
     *
     * <p>
     * Streamed playback maintains looping in normal Java state because the stream
     * must be manually rewound and refilled. Non-streamed playback also mirrors the
     * looping flag into the OpenAL source.
     * </p>
     *
     * @return {@code true} if looping is enabled
     */
    private boolean isLoopingInternal() {
        if (streaming) {
            return looping;
        }
        return alGetSourcei(source, AL_LOOPING) == AL_TRUE;
    }

    /**
     * <p>
     * Sets the looping state internally.
     * </p>
     *
     * <p>
     * All players store the looping flag locally. Non-streaming players additionally
     * update the OpenAL source looping property directly.
     * </p>
     *
     * @param looping whether looping should be enabled
     */
    private void setLoopingInternal(boolean looping) {
        this.looping = looping;

        if (!streaming) {
            alSourcei(source, AL_LOOPING, looping ? AL_TRUE : AL_FALSE);
        }
    }

    /**
     * <p>
     * Rewinds to the beginning and starts playback immediately.
     * </p>
     */
    private void playFromStartInternal() {
        setCurrentTimeInternal(0f);
        playInternal();
    }

    /**
     * <p>
     * Seeks to a specific playback time and starts playback immediately.
     * </p>
     *
     * @param seconds the desired starting time in seconds
     */
    private void playFromInternal(float seconds) {
        setCurrentTimeInternal(seconds);
        playInternal();
    }

    /**
     * <p>
     * Restarts playback from the beginning.
     * </p>
     */
    private void restartInternal() {
        playFromStartInternal();
    }

    /**
     * <p>
     * Moves playback forward by a relative amount of time.
     * </p>
     *
     * @param seconds the amount of time to move forward
     */
    private void fastForwardInternal(float seconds) {
        if (seconds <= 0f) {
            return;
        }
        setCurrentTimeInternal(getCurrentTimeInternal() + seconds);
    }

    /**
     * <p>
     * Moves playback backward by a relative amount of time.
     * </p>
     *
     * @param seconds the amount of time to move backward
     */
    private void rewindByInternal(float seconds) {
        if (seconds <= 0f) {
            return;
        }
        setCurrentTimeInternal(getCurrentTimeInternal() - seconds);
    }

    /**
     * <p>
     * Seeks playback by a signed time offset.
     * </p>
     *
     * @param seconds the signed offset in seconds
     */
    private void seekByInternal(float seconds) {
        if (seconds == 0f) {
            return;
        }
        setCurrentTimeInternal(getCurrentTimeInternal() + seconds);
    }

    /**
     * <p>
     * Jumps to the beginning of the sound.
     * </p>
     */
    private void skipToStartInternal() {
        setCurrentTimeInternal(0f);
    }

    /**
     * <p>
     * Jumps to the end of the sound.
     * </p>
     */
    private void skipToEndInternal() {
        setCurrentTimeInternal(duration());
    }

    /**
     * <p>
     * Moves playback forward by the default five-second step.
     * </p>
     */
    private void stepForwardInternal() {
        fastForwardInternal(5f);
    }

    /**
     * <p>
     * Moves playback backward by the default five-second step.
     * </p>
     */
    private void stepBackwardInternal() {
        rewindByInternal(5f);
    }

    /**
     * <p>
     * Moves playback forward by the supplied step size.
     * </p>
     *
     * @param seconds the forward step size in seconds
     */
    private void stepForwardInternal(float seconds) {
        fastForwardInternal(seconds);
    }

    /**
     * <p>
     * Moves playback backward by the supplied step size.
     * </p>
     *
     * @param seconds the backward step size in seconds
     */
    private void stepBackwardInternal(float seconds) {
        rewindByInternal(seconds);
    }

    /**
     * <p>
     * Toggles between play and pause states.
     * </p>
     *
     * <p>
     * If the source is currently playing, it is paused. Otherwise playback is resumed.
     * </p>
     */
    private void togglePlayPauseInternal() {
        if (isPlayingInternal()) {
            pauseInternal();
        } else {
            resumeInternal();
        }
    }

    /**
     * <p>
     * Toggles the looping flag.
     * </p>
     */
    private void toggleLoopingInternal() {
        setLoopingInternal(!isLoopingInternal());
    }

    /**
     * <p>
     * Returns whether playback has fully completed.
     * </p>
     *
     * <p>
     * Looping sounds are never considered finished. Streamed sounds are only finished
     * when the source is not actively playing, there are no playable queued buffers,
     * and the playback time has reached the sound duration.
     * </p>
     *
     * @return {@code true} if playback is finished
     */
    private boolean isFinishedInternal() {
        if (isLoopingInternal()) {
            return false;
        }

        if (streaming) {
            int state = alGetSourcei(source, AL_SOURCE_STATE);
            int queued = alGetSourcei(source, AL_BUFFERS_QUEUED);
            int processed = alGetSourcei(source, AL_BUFFERS_PROCESSED);
            return state != AL_PLAYING && Math.max(0, queued - processed) == 0 && getCurrentTimeInternal() >= duration();
        }

        return isStoppedInternal() && getCurrentTimeInternal() >= duration();
    }

    /**
     * <p>
     * Returns normalized playback progress.
     * </p>
     *
     * <p>
     * If the sound has no valid duration, zero is returned.
     * </p>
     *
     * @return progress in the {@code [0, 1]} range
     */
    private float getProgressInternal() {
        float duration = duration();
        if (duration <= 0f) {
            return 0f;
        }
        return getCurrentTimeInternal() / duration;
    }

    /**
     * <p>
     * Sets playback position using normalized progress.
     * </p>
     *
     * @param progress progress in the {@code [0, 1]} range
     */
    private void setProgressInternal(float progress) {
        setCurrentTimeInternal(duration() * clamp01(progress));
    }

    /**
     * <p>
     * Moves playback forward by a percentage of the total duration.
     * </p>
     *
     * @param percent normalized percentage of total duration to advance
     */
    private void fastForwardPercentInternal(float percent) {
        float duration = duration();
        if (duration <= 0f) {
            return;
        }
        seekByInternal(duration * clamp01(percent));
    }

    /**
     * <p>
     * Moves playback backward by a percentage of the total duration.
     * </p>
     *
     * @param percent normalized percentage of total duration to rewind
     */
    private void rewindPercentInternal(float percent) {
        float duration = duration();
        if (duration <= 0f) {
            return;
        }
        seekByInternal(-duration * clamp01(percent));
    }

    /**
     * <p>
     * Mutes playback while remembering the current volume.
     * </p>
     *
     * <p>
     * The remembered volume is only updated when the player was not already muted.
     * </p>
     */
    private void muteInternal() {
        if (!muted) {
            storedVolume = getVolumeInternal();
            muted = true;
        }
        setVolumeInternal(0f);
    }

    /**
     * <p>
     * Restores the remembered volume and clears muted state.
     * </p>
     */
    private void unmuteInternal() {
        muted = false;
        setVolumeInternal(storedVolume);
    }

    /**
     * <p>
     * Toggles mute state.
     * </p>
     *
     * <p>
     * If the player is already muted or effectively silent, it un-mutes. Otherwise
     * it stores current volume and mutes.
     * </p>
     */
    private void toggleMuteInternal() {
        if (muted || getVolumeInternal() <= 0f) {
            unmuteInternal();
        } else {
            muteInternal();
        }
    }

    /**
     * <p>
     * Returns whether the player is muted.
     * </p>
     *
     * @return {@code true} if muted or effectively silent
     */
    private boolean isMutedInternal() {
        return muted || getVolumeInternal() <= 0f;
    }

    /**
     * <p>
     * Increases volume by the supplied amount.
     * </p>
     *
     * @param amount the amount to add to the current volume
     */
    private void volumeUpInternal(float amount) {
        if (amount <= 0f) {
            return;
        }
        muted = false;
        setVolumeInternal(getVolumeInternal() + amount);
        storedVolume = getVolumeInternal();
    }

    /**
     * <p>
     * Decreases volume by the supplied amount.
     * </p>
     *
     * @param amount the amount to subtract from the current volume
     */
    private void volumeDownInternal(float amount) {
        if (amount <= 0f) {
            return;
        }
        setVolumeInternal(getVolumeInternal() - amount);
        storedVolume = getVolumeInternal();
        if (getVolumeInternal() <= 0f) {
            muted = true;
        }
    }

    /**
     * <p>
     * Sets playback speed by updating pitch.
     * </p>
     *
     * @param speed the desired playback speed multiplier
     */
    private void setPlaybackSpeedInternal(float speed) {
        setPitchInternal(speed);
    }

    /**
     * <p>
     * Returns playback speed by reading pitch.
     * </p>
     *
     * @return the current playback speed multiplier
     */
    private float getPlaybackSpeedInternal() {
        return getPitchInternal();
    }

    /**
     * <p>
     * Restores playback speed to normal.
     * </p>
     */
    private void normalSpeedInternal() {
        setPitchInternal(1f);
    }

    /**
     * <p>
     * Decreases playback speed by a small fixed step.
     * </p>
     */
    private void slowerInternal() {
        setPitchInternal(Math.max(0.1f, getPitchInternal() - 0.1f));
    }

    /**
     * <p>
     * Increases playback speed by a small fixed step.
     * </p>
     */
    private void fasterInternal() {
        setPitchInternal(Math.min(8f, getPitchInternal() + 0.1f));
    }

    /**
     * <p>
     * Performs complete resource disposal for this player.
     * </p>
     *
     * <p>
     * The method stops playback, clears and deletes stream buffers if streaming is in
     * use, deletes the normal buffer otherwise, deletes the OpenAL source, and finally
     * unregisters the player from the audio subsystem.
     * </p>
     */
    private void disposeInternal() {
        alSourceStop(source);

        if (streaming) {
            clearQueuedBuffers();

            if (stream != null) {
                stream.close();
                stream = null;
            }

            for (int streamBuffer : streamBuffers) {
                alDeleteBuffers(streamBuffer);
            }
        } else {
            alDeleteBuffers(buffer);
        }

        alDeleteSources(source);
        Audio.unregister(this);
    }

    /**
     * <p>
     * Rebuilds stream state starting from the requested playback time.
     * </p>
     *
     * <p>
     * This method is used whenever streamed playback needs to start over, resume from
     * a specific position, or recover from an empty queue. It clears any queued OpenAL
     * buffers, reopens the {@link SoundStream}, attempts to seek to the requested time,
     * resets per-buffer duration bookkeeping, refills the rotating stream buffers, and
     * queues all buffers that successfully received audio.
     * </p>
     *
     * @param seconds the target starting time in seconds
     */
    private void resetStream(float seconds) {
        clearQueuedBuffers();
        alSourcei(source, AL_BUFFER, 0);

        if (stream != null) {
            stream.close();
        }

        stream = data.openStream();
        streamedSeconds = clampTime(seconds);

        if (!stream.seek(streamedSeconds)) {
            streamedSeconds = 0f;
            stream.close();
            stream = data.openStream();
        }

        for (int j : streamBuffers) {
            setStreamBufferDuration(j, 0f);
        }

        for (int streamBuffer : streamBuffers) {
            if (!fillStreamBuffer(streamBuffer)) {
                break;
            }
            alSourceQueueBuffers(source, streamBuffer);
        }

        alSourcef(source, AL_SEC_OFFSET, 0f);
    }

    /**
     * <p>
     * Reads new streamed PCM data and uploads it into one OpenAL stream buffer.
     * </p>
     *
     * <p>
     * If the current stream has no more data and looping is disabled, the method
     * returns {@code false}. If looping is enabled, the stream is reopened from the
     * beginning and another read attempt is made. On success, the buffer is filled
     * with PCM data, the buffer duration is recorded, and {@code true} is returned.
     * </p>
     *
     * @param bufferId the OpenAL buffer to refill
     * @return {@code true} if audio data was written into the buffer
     */
    private boolean fillStreamBuffer(int bufferId) {
        int bytesRead = readStreamChunk();

        if (bytesRead <= 0) {
            if (!looping) {
                return false;
            }

            if (stream != null) {
                stream.close();
            }

            stream = data.openStream();
            streamedSeconds = 0f;

            if (!stream.seek(0f)) {
                return false;
            }

            bytesRead = readStreamChunk();
            if (bytesRead <= 0) {
                return false;
            }
        }

        int channels = stream.channels();
        int sampleRate = stream.sampleRate();
        int bitsPerSample = stream.bitsPerSample();
        int format = channels == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;

        ByteBuffer pcm = streamChunk.duplicate();
        pcm.position(0);
        pcm.limit(bytesRead);
        alBufferData(bufferId, format, pcm, sampleRate);

        float seconds = bytesRead / (float) (channels * sampleRate * (bitsPerSample / 8f));
        setStreamBufferDuration(bufferId, seconds);
        return true;
    }

    /**
     * <p>
     * Reads the next chunk of PCM data from the active stream.
     * </p>
     *
     * <p>
     * If no stream is currently active, zero is returned.
     * </p>
     *
     * @return the number of bytes read into {@link #streamChunk}
     */
    private int readStreamChunk() {
        if (stream == null) {
            return 0;
        }

        return stream.read(streamChunk);
    }

    /**
     * <p>
     * Unqueues and clears all buffers currently attached to a streaming source.
     * </p>
     *
     * <p>
     * This method is only meaningful for streaming playback. It removes every queued
     * buffer from the source and clears the recorded duration for each one.
     * </p>
     */
    private void clearQueuedBuffers() {
        if (!streaming) {
            return;
        }

        int queued = alGetSourcei(source, AL_BUFFERS_QUEUED);

        while (queued-- > 0) {
            int bufferId = alSourceUnqueueBuffers(source);
            setStreamBufferDuration(bufferId, 0f);
        }
    }

    /**
     * <p>
     * Clamps a playback time into the valid range for this sound.
     * </p>
     *
     * <p>
     * If the sound does not report a valid duration, the result is only clamped to be
     * non-negative. Otherwise the result is clamped into the {@code [0, duration]}
     * range.
     * </p>
     *
     * @param seconds the time to clamp
     * @return the clamped playback time
     */
    private float clampTime(float seconds) {
        if (data.duration() <= 0f) {
            return Math.max(0f, seconds);
        }
        return Math.max(0f, Math.min(seconds, data.duration()));
    }

    /**
     * <p>
     * Returns the recorded duration for a specific stream buffer.
     * </p>
     *
     * @param bufferId the OpenAL buffer handle
     * @return the known duration of audio stored in that buffer
     */
    private float getStreamBufferDuration(int bufferId) {
        for (int i = 0; i < streamBuffers.length; i++) {
            if (streamBuffers[i] == bufferId) {
                return streamBufferDurations[i];
            }
        }
        return 0f;
    }

    /**
     * <p>
     * Records the duration associated with a specific stream buffer.
     * </p>
     *
     * @param bufferId the OpenAL buffer handle
     * @param duration the duration in seconds represented by that buffer's audio data
     */
    private void setStreamBufferDuration(int bufferId, float duration) {
        for (int i = 0; i < streamBuffers.length; i++) {
            if (streamBuffers[i] == bufferId) {
                streamBufferDurations[i] = duration;
                return;
            }
        }
    }

    /**
     * <p>
     * Clamps a floating-point value into the normalized {@code [0, 1]} range.
     * </p>
     *
     * @param value the value to clamp
     * @return the normalized clamped value
     */
    private float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /**
     * <p>
     * Executes an action on the audio thread.
     * </p>
     *
     * <p>
     * If the current thread already owns the audio context, the action runs
     * immediately. Otherwise the action is executed synchronously through
     * {@link Audio#sync(Runnable)}.
     * </p>
     *
     * @param action the action to execute
     */
    private void execute(Runnable action) {
        if (Audio.isAudioThread()) {
            action.run();
        } else {
            Audio.sync(action);
        }
    }

    /**
     * <p>
     * Queries a value from the audio thread.
     * </p>
     *
     * <p>
     * If the current thread is already the audio thread, the supplier is evaluated
     * immediately. Otherwise the value is requested synchronously through
     * {@link Audio#call(java.util.concurrent.Callable)}.
     * </p>
     *
     * @param supplier the supplier used to produce the result
     * @param <T>      the result type
     * @return the supplied result
     */
    private <T> T query(Supplier<T> supplier) {
        if (Audio.isAudioThread()) {
            return supplier.get();
        }
        return Audio.call(supplier::get);
    }
}