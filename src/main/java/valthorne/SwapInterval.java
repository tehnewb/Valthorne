package valthorne;

/**
 * Represents available swap interval (VSync) settings.
 * Values are passed to the window's buffer-swap configuration. Positive intervals
 * request a number of display refreshes between swaps; the achieved frame rate also
 * depends on rendering time, the platform, and driver settings.
 *
 * @author Albert Beaupre
 */
public enum SwapInterval {
    /**
     * VSync disabled — uncapped FPS
     */
    OFF(0),

    /**
     * Standard VSync — syncs every frame (≈60 FPS on a 60Hz monitor)
     */
    VSYNC(1),

    /**
     * Half refresh rate — syncs every 2 frames (≈30 FPS on a 60Hz monitor)
     */
    HALF(2),

    /**
     * One third of the refresh rate — swaps every 3 refreshes (about 20 FPS at 60 Hz).
     */
    TRIPLE(3),

    /**
     * Adaptive VSync — driver-dependent (may act like 0 or 1)
     */
    ADAPTIVE(-1);

    private final int interval; // Native swap interval represented by this setting.

    /**
     * Associates an enum constant with its native swap interval; this does not
     * configure a window or check support for adaptive synchronization.
     *
     * @param interval native refresh interval, or a negative value for adaptive mode
     */
    SwapInterval(int interval) {
        this.interval = interval;
    }

    /**
     * Returns the native value to supply when configuring buffer swaps.
     * Reading the value has no effect on the active graphics context.
     *
     * @return this setting's swap interval
     */
    public int getValue() {
        return interval;
    }
}
