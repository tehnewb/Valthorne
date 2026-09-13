package valthorne.portable;

/** Browser-independent input, sound, local settings and immediate 2D presentation. */
public interface PlatformServices extends AutoCloseable {
    /** Physical key name, for example KeyW, Space or Escape. */
    boolean keyDown(String code);
    /** Consumes a key-down edge, including taps shorter than one simulation update. */
    boolean takeKeyPress(String code);
    boolean buttonDown(int button);
    float takeLookX();
    float takeLookY();
    /** Requires a user gesture on web. */
    void capturePointer(boolean capture);
    /** Local small settings; null means absent. Storage failures are reported to the caller. */
    String loadSetting(String key);
    void saveSetting(String key, String value);
    void removeSetting(String key);
    /** Plays a bounded synthesized voice, after audio has been unlocked by user interaction. */
    void tone(float frequency, float seconds, float volume, float pan);
    void beginOverlay();
    int viewportWidth();
    int viewportHeight();
    void rectangle(float x, float y, float width, float height, int rgb, float alpha);
    void text(String text, float x, float y, float size, int rgb);
    @Override void close();
}
