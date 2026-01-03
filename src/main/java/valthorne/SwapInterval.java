package valthorne;

/**
 * Represents available swap interval (VSync) settings.
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
     * Triple refresh rate — syncs every 3 frames (≈20 FPS on a 60Hz monitor)
     */
    TRIPLE(3),

    /**
     * Adaptive VSync — driver-dependent (may act like 0 or 1)
     */
    ADAPTIVE(-1);

    private final int interval;

    SwapInterval(int interval) {
        this.interval = interval;
    }

    public int getInterval() {
        return interval;
    }
}
