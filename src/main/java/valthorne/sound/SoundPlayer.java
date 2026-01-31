package valthorne.sound;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL11.AL_SEC_OFFSET;

/**
 * Manages and plays audio buffer using OpenAL. This class wraps around OpenAL functionalities
 * to create, manage, and control sound playback, providing support for actions like playing,
 * pausing, stopping, rewinding, and configuring playback parameters such as volume, pitch,
 * and looping.
 * <p>
 * This class uses a sound buffer input {@link SoundData} and manages the playback state for
 * the sound generated from the provided buffer.
 *
 * @author Albert Beaupre
 * @since December 5th, 2025
 */
public class SoundPlayer {

    private final SoundData data;
    private final int source;
    private final int buffer;

    /**
     * Constructs a {@code SoundPlayer} instance to manage and play audio using the provided {@link SoundData}.
     * The constructor initializes and configures OpenAL buffers and sources based on the audio buffer details,
     * such as its channels, sample rate, and format.
     *
     * @param data an instance of {@link SoundData} containing the audio buffer and its attributes
     *             such as channels, sample rate, and raw PCM buffer.
     */
    public SoundPlayer(SoundData data) {
        this.data = data;
        this.buffer = alGenBuffers();
        int format = (data.channels() == 1) ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;

        alBufferData(buffer, format, data.data(), data.sampleRate());

        this.source = alGenSources();
        alSourcei(source, AL_BUFFER, buffer);
        setVolume(1f);
        setPitch(1f);
        setLooping(false);
    }

    /**
     * Plays the audio associated with this {@code SoundPlayer} instance.
     * This method triggers the playback of the loaded sound using the underlying OpenAL
     * audio source. If the sound is already playing, this call will have no effect.
     * Ensure that the sound source has been initialized and configured before calling this method.
     */
    public void play() {
        alSourcePlay(source);
    }

    /**
     * Pauses the playback of the audio associated with this {@code SoundPlayer} instance.
     * This method halts the currently playing sound at its current position, allowing it to
     * be resumed later from the same point. If the audio is already paused or stopped, this
     * method has no effect.
     * <p>
     * Ensure the audio source has been initialized and is currently in a playing state before
     * invoking this method.
     */
    public void pause() {
        alSourcePause(source);
    }

    /**
     * Resumes playback of the audio associated with this {@code SoundPlayer} instance.
     * This method continues the playback of the loaded sound from the point at which it was paused.
     * If the audio is already playing or stopped, this method has no effect.
     * <p>
     * Ensure that the sound source is properly initialized and has been paused before invoking this method.
     */
    public void resume() {
        alSourcePlay(source);
    }

    /**
     * Stops the audio playback for the current {@code SoundPlayer} instance.
     * This method halts the playback of the sound associated with the OpenAL audio source.
     * If the sound is already stopped, calling this method has no additional effect.
     * After stopping, the playback position will be reset to the start of the audio.
     * <p>
     * Note: Ensure that the audio source has been initialized properly before invoking this method.
     */
    public void stop() {
        alSourceStop(source);
        rewind();
    }

    /**
     * Resets the playback position of the audio source to the beginning.
     * This method reinitializes the playback position of the associated audio to start from zero,
     * but does not begin playing the sound. To replay the sound after rewinding,
     * invoke the {@code play()} method.
     * <p>
     * Note: Ensure the audio source has been properly initialized and loaded
     * before calling this method. If the audio source is already in a playing or paused state,
     * its position will still be reset without affecting its state.
     */
    public void rewind() {
        alSourceRewind(source);
    }

    /**
     * Checks whether the audio is currently playing.
     * This method determines if the sound source associated with this {@code SoundPlayer}
     * instance is in a playing state using the OpenAL audio library.
     *
     * @return {@code true} if the audio is playing; {@code false} otherwise.
     */
    public boolean isPlaying() {
        return alGetSourcei(source, AL_SOURCE_STATE) == AL_PLAYING;
    }

    /**
     * Checks whether the audio source is currently in a paused state.
     * This method determines if the associated sound is temporarily halted
     * but ready to resume from its current position.
     *
     * @return {@code true} if the audio source is paused; {@code false} otherwise.
     */
    public boolean isPaused() {
        return alGetSourcei(source, AL_SOURCE_STATE) == AL_PAUSED;
    }

    /**
     * Checks whether the audio source associated with this {@code SoundPlayer} instance is stopped.
     * This method determines if the sound source is in either the stopped or initial state,
     * as defined by the OpenAL audio library.
     *
     * @return {@code true} if the audio source is stopped or in the initial state; {@code false} otherwise.
     */
    public boolean isStopped() {
        int s = alGetSourcei(source, AL_SOURCE_STATE);
        return s == AL_STOPPED || s == AL_INITIAL;
    }

    /**
     * Retrieves the duration of the audio associated with this {@code SoundPlayer} instance.
     * The duration is measured in seconds and represents the total playback time of the audio.
     *
     * @return the audio duration in seconds.
     */
    public float duration() {
        return data.duration();
    }

    /**
     * Retrieves the current playback position of the audio associated with this {@code SoundPlayer} instance.
     * This method queries the underlying OpenAL audio source to determine the current playback
     * offset, measured in seconds from the start of the audio.
     *
     * @return the current playback time in seconds.
     */
    public float getCurrentTime() {
        return alGetSourcef(source, AL_SEC_OFFSET);
    }

    /**
     * Sets the current playback position for the audio associated with this {@code SoundPlayer} instance.
     * The position is clamped between the start (0 seconds) and the maximum duration of the audio.
     * If the specified value exceeds the duration or is negative, it will be adjusted accordingly.
     *
     * @param sec the desired playback position in seconds.
     *            Values less than 0 will be clamped to 0,
     *            and values greater than the duration will be clamped to the duration.
     */
    public void setCurrentTime(float sec) {
        alSourcef(source, AL_SEC_OFFSET, Math.max(0, Math.min(sec, data.duration())));
    }

    /**
     * Retrieves the current volume level of the audio source.
     * The returned value represents the gain of the underlying
     * OpenAL audio source, where 0.0 is completely silent and
     * 1.0 is the maximum volume.
     *
     * @return the current volume level as a float between 0.0 and 1.0.
     */
    public float getVolume() {
        return alGetSourcef(source, AL_GAIN);
    }

    /**
     * Sets the volume level for audio playback of this {@code SoundPlayer} instance.
     * The volume is clamped between 0.0 (silent) and 1.0 (maximum volume).
     * This method adjusts the gain parameter of the underlying OpenAL audio source.
     *
     * @param v the desired volume level, where 0.0 is completely silent
     *          and 1.0 is the maximum allowed volume.
     */
    public void setVolume(float v) {
        alSourcef(source, AL_GAIN, Math.max(0f, Math.min(1f, v)));
    }

    /**
     * Retrieves the current pitch value of the audio playback for this {@code SoundPlayer} instance.
     * The pitch determines the perceived frequency of the audio, with a default value of 1.0 representing
     * the normal pitch. Values greater than 1.0 increase the pitch (higher frequency), and values less
     * than 1.0 decrease the pitch (lower frequency).
     *
     * @return the current pitch value as a float, typically in the range of 0.1 to 8.0.
     */
    public float getPitch() {
        return alGetSourcef(source, AL_PITCH);
    }

    /**
     * Sets the pitch level for audio playback of this {@code SoundPlayer} instance.
     * The pitch value is clamped between 0.1 (lowest pitch) and 8.0 (highest pitch).
     * This method adjusts the pitch parameter of the underlying OpenAL audio source.
     *
     * @param p the desired pitch level, where 1.0 represents the normal pitch,
     *          values less than 1.0 decrease the pitch (lower frequency),
     *          and values greater than 1.0 increase the pitch (higher frequency).
     */
    public void setPitch(float p) {
        alSourcef(source, AL_PITCH, Math.max(0.1f, Math.min(8f, p)));
    }

    /**
     * Checks whether the audio playback is currently set to loop.
     * This method queries the underlying OpenAL audio source to determine
     * if the looping state is enabled.
     *
     * @return {@code true} if the audio playback is looping; {@code false} otherwise.
     */
    public boolean isLooping() {
        return alGetSourcei(source, AL_LOOPING) == AL_TRUE;
    }

    /**
     * Sets whether the audio playback should loop continuously.
     * When looping is enabled, the sound will restart automatically
     * from the beginning once it reaches the end.
     *
     * @param looping {@code true} to enable looping, {@code false} to disable it.
     */
    public void setLooping(boolean looping) {
        alSourcei(source, AL_LOOPING, looping ? AL_TRUE : AL_FALSE);
    }

    /**
     * Releases the resources associated with this {@code SoundPlayer} instance.
     * This method deletes the OpenAL audio sources and buffers that were used
     * to manage and play the audio. After invoking this method, any further
     * operations on the instance may result in undefined behavior as the
     * underlying resources will no longer be available.
     * <p>
     * It is recommended to call this method when the {@code SoundPlayer} instance
     * is no longer needed to free up system resources related to audio processing.
     */
    public void dispose() {
        alDeleteSources(source);
        alDeleteBuffers(buffer);
    }

    /**
     * Retrieves the {@link SoundData} instance associated with this {@code SoundPlayer}.
     * This method returns the audio buffer and its attributes, such as the channels, sample rate,
     * duration, and raw PCM buffer, used in sound playback and processing.
     *
     * @return the {@link SoundData} instance containing the audio buffer and its related attributes.
     */
    public SoundData getData() {
        return data;
    }
}