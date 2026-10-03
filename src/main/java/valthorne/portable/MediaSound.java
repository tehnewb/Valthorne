package valthorne.portable;

/**
 * Owned sampled audio resource. Close it on the application thread after its
 * final use; playback availability depends on the platform audio voice budget.
 */
public interface MediaSound extends AutoCloseable {
    /**
     * Attempts playback with normalized volume and stereo pan.
     *
     * @param volume gain between zero and one
     * @param pan stereo position from minus one (left) to one (right)
     * @return false if audio is locked or no playback voice is available
     */
    boolean play(float volume, float pan);

    @Override
    void close();
}
