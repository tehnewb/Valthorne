package valthorne.graphics.font.directwrite;

/**
 * Immutable snapshot of installed Windows text-rendering preferences. Values are
 * read from native rendering parameters without applying application weight,
 * gamma, or contrast adjustments. A zero rendering mode delegates mode selection
 * to DirectWrite; it does not mean smoothing is disabled.
 *
 * @param gamma native gamma
 * @param enhancedContrast native enhanced contrast
 * @param grayscaleEnhancedContrast native grayscale contrast, or NaN when unavailable
 * @param clearTypeLevel native LCD subpixel strength
 * @param pixelGeometry native flat, RGB, or BGR geometry value
 * @param renderingMode native base rendering-mode value
 * @param gridFitMode native grid-fitting mode, or -1 when unavailable
 * @param extendedRenderingMode native extended rendering mode, or -1 when unavailable
 * @param smoothingEnabled whether Windows enables font smoothing
 * @param clearTypeEnabled whether Windows requests ClearType on eligible surfaces
 */
public record DirectWriteRenderingSettings(float gamma, float enhancedContrast, float grayscaleEnhancedContrast, float clearTypeLevel, int pixelGeometry, int renderingMode, int gridFitMode, int extendedRenderingMode, boolean smoothingEnabled, boolean clearTypeEnabled) {}
