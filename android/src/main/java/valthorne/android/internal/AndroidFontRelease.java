package valthorne.android.internal;

/**
 * Releases a CPU font parser without retaining the Java object that owns it.
 */
final class AndroidFontRelease implements Runnable {
    private final int token; // Owned native STB parser token.

    /**
     * Captures an independently owned CPU parser.
     * @param token native parser token
     */
    AndroidFontRelease(int token) { this.token = token; }

    @Override
    public void run() { AndroidNative.word(0, token); AndroidNative.call(AndroidOperation.FONT_FREE); }
}
