package valthorne.graphics.animation;

import valthorne.graphics.texture.TextureRegion;
import valthorne.graphics.texture.TextureRegionDrawable;

/**
 * A utility class for creating animations using texture regions.
 */
public final class AnimationUtility {

    private AnimationUtility() {
        // Inaccessible
    }

    /**
     * Creates an animation using the specified playback mode, total duration, and texture regions.
     *
     * @param mode     the playback mode for the animation, determining how frames are cycled.
     * @param duration the total duration of the animation in seconds.
     * @param regions  the texture regions that define the frames of the animation.
     * @return a new {@code Animation} instance configured with the provided playback mode, duration, and frames.
     */
    public static Animation fromRegions(PlaybackMode mode, float duration, TextureRegion... regions) {
        AnimationFrame[] frames = new AnimationFrame[regions.length];
        for (int i = 0; i < frames.length; i++)
            frames[i] = new AnimationFrame(new TextureRegionDrawable(regions[i]), duration / frames.length);
        return new Animation(mode, frames);
    }

    /**
     * Creates an animation using the specified playback mode, an array of frame durations,
     * and corresponding texture regions.
     *
     * @param mode      the playback mode for the animation, determining the order and repetition of frames.
     * @param durations an array of durations in seconds, specifying the duration of each frame.
     *                  The length of this array must match the number of provided texture regions.
     * @param regions   the texture regions that define the frames of the animation.
     * @return a new {@code Animation} instance configured with the provided playback mode, frame durations, and frames.
     * @throws IllegalArgumentException if the lengths of the {@code durations} array and {@code regions} array do not match.
     */
    public static Animation fromRegions(PlaybackMode mode, float[] durations, TextureRegion... regions) {
        if (regions.length != durations.length) throw new IllegalArgumentException("regions and durations must match");

        AnimationFrame[] frames = new AnimationFrame[regions.length];

        for (int i = 0; i < regions.length; i++) {
            frames[i] = new AnimationFrame(new TextureRegionDrawable(regions[i]), durations[i]);
        }

        return new Animation(mode, frames);
    }

    /**
     * Creates an animation using a grid of texture regions, the specified playback mode, and frame duration.
     * Each cell in the provided grid is treated as an animation frame.
     *
     * @param mode          the playback mode for the animation, which determines the order and repetition of frames.
     * @param frameDuration the duration of each frame in seconds.
     * @param grid          a 2D array of {@code TextureRegion} objects representing the animation frames.
     * @return a new {@code Animation} instance configured with the provided playback mode, frame duration, and frames.
     */
    public static Animation fromGrid(PlaybackMode mode, float frameDuration, TextureRegion[][] grid) {
        int rows = grid.length;
        int cols = grid[0].length;

        AnimationFrame[] frames = new AnimationFrame[rows * cols];

        int index = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                frames[index++] = new AnimationFrame(
                        new TextureRegionDrawable(grid[r][c]),
                        frameDuration
                );
            }
        }

        return new Animation(mode, frames);
    }

}
