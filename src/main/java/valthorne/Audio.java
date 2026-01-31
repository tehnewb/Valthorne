package valthorne;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import valthorne.sound.SoundData;
import valthorne.sound.SoundPlayer;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * The Audio class manages the initialization and disposal of the audio system using OpenAL.
 * It is responsible for setting up the OpenAL device and context, ensuring that audio-related
 * resources are properly configured and released.
 * <p>
 * This class is a utility class and is not intended to be instantiated.
 * It provides static methods to handle audio system operations.
 *
 * @author Albert Beaupre
 * @since December 5th, 2025
 */
public class Audio {

    private static long device;
    private static long context;

    /**
     * Initializes the audio system using OpenAL. This method sets up the OpenAL device
     * and context required for audio operations. It ensures that the device and context
     * are successfully created and made current. Additionally, it establishes the
     * capabilities of the OpenAL context.
     *
     * @throws IllegalStateException if the OpenAL device or context cannot be created.
     */
    static void init() {
        device = alcOpenDevice((ByteBuffer) null);
        if (device == NULL) throw new IllegalStateException("Failed to open OpenAL device");

        ALCCapabilities capabilities = ALC.createCapabilities(device);
        context = alcCreateContext(device, (IntBuffer) null);
        if (context == NULL) throw new IllegalStateException("Failed to create OpenAL context");

        alcMakeContextCurrent(context);
        AL.createCapabilities(capabilities);
    }

    /**
     * Releases all resources associated with the OpenAL audio system. This method ensures that the audio context,
     * audio device, and other resources managed by OpenAL are properly disposed of to prevent memory leaks or resource
     * conflicts.
     * <p>
     * Specifically, this method performs the following tasks:
     * - Sets the current OpenAL context to {@code NULL}.
     * - Destroys the OpenAL context.
     * - Closes the OpenAL device.
     */
    static void dispose() {
        alcMakeContextCurrent(NULL);
        alcDestroyContext(context);
        alcCloseDevice(device);
    }

    /**
     * Loads sound buffer from the specified file path and creates a {@link SoundPlayer} instance.
     *
     * @param path the file path to the sound file to be loaded
     * @return a {@link SoundPlayer} instance containing the loaded sound buffer
     * @throws RuntimeException if an I/O error occurs while reading the file
     */
    public static SoundPlayer load(String path) {
        return new SoundPlayer(SoundData.load(path));
    }

    /**
     * Loads sound buffer from the provided byte array and creates a {@link SoundPlayer} instance.
     *
     * @param data the byte array containing raw sound buffer to be loaded
     * @return a {@link SoundPlayer} instance initialized with the provided sound buffer
     * @throws RuntimeException if an error occurs during the sound buffer loading process
     */
    public static SoundPlayer load(byte[] data) {
        try {
            return new SoundPlayer(SoundData.load(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
