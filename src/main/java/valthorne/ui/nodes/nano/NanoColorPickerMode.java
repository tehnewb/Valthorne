package valthorne.ui.nodes.nano;

/**
 * Selects the visible editing surfaces without changing the current color.
 * Both surfaces edit the same packed value and share hexadecimal and alpha input.
 * @author Albert Beaupre
 */
public enum NanoColorPickerMode {
    /**
     * Four red, green, blue and alpha channel sliders.
     */
    SLIDERS,
    /**
     * Hue/saturation wheel with brightness and alpha sliders.
     */
    WHEEL,
    /**
     * Wheel, brightness and all four RGBA sliders together.
     */
    BOTH
}
