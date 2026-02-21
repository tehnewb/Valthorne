package valthorne.tween;

/**
 * Represents a task designed to be executed within a tweening system.
 * A TweenTask provides a mechanism for updating over time, determining
 * its completion state, and allowing cancellation.
 * <p>
 * This interface should be implemented by classes that encapsulate specific
 * behaviors or effects to be executed during animations or time-based operations.
 * Typical implementations may include sequences, delays, or repetitions
 * as part of a larger animation framework.
 *
 * @author Albert Beaupre
 * @since February 20th, 2026
 */
public interface TweenTask {

    /**
     * Updates the state of the task for a given delta time. This method is
     * called to progress the task over time, using the elapsed time
     * since the last update.
     *
     * @param dt the time, in seconds, since the last update; must be non-negative
     */
    void update(float dt);

    /**
     * Determines whether the task has completed its execution or reached its end state.
     *
     * @return true if the task is finished, false otherwise
     */
    boolean isFinished();

    /**
     * Cancels the current task, marking it as finished and preventing
     * any further updates or processing. Once canceled, the task is
     * considered completed and cannot be resumed.
     */
    void cancel();
}