package valthorne.tween;

/**
 * Represents a delay task that can be used in tween sequences or parallel tasks.
 * The delay task essentially "waits" for a specified number of seconds before
 * marking itself as finished.
 * <p>
 * This class is immutable once instantiated and is primarily used for scheduling
 * delays in tween animations or task sequences.
 *
 * @author Albert Beaupre
 * @since February 20th, 2026
 */
public final class Delay implements TweenTask {

    private float remaining;
    private boolean finished;

    /**
     * Creates a new delay task with a specified duration in seconds.
     * The delay will last for at least the given number of seconds,
     * and any negative input will be clamped to zero.
     *
     * @param seconds the duration of the delay in seconds, must be ≥ 0
     */
    public Delay(float seconds) {
        this.remaining = Math.max(0f, seconds);
    }

    @Override
    public void update(float dt) {
        if (finished) return;

        remaining -= Math.max(0f, dt);
        if (remaining <= 0f) finished = true;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void cancel() {
        finished = true;
    }
}