package valthorne.tween;

import valthorne.collections.array.SwapOnRemoveArray;

/**
 * Represents a sequence of steps that can include either {@link TweenInstance} or {@link TweenTask}.
 * The steps are executed in order, one at a time, and the sequence progresses to the next step
 * only when the current step is finished.
 * <p>
 * This class is immutable with respect to its structure and can only be modified by adding steps via
 * its methods. Once all steps are completed, the sequence is considered finished.
 * <p>
 * The implementation ensures that each step is updated properly in sequence and prevents further
 * updates once the sequence is marked as finished.
 *
 * @author Albert Beaupre
 * @since February 20th, 2026
 */
public final class Sequence implements TweenTask {

    private final SwapOnRemoveArray<Object> steps = new SwapOnRemoveArray<>(); // TweenInstance or TweenTask.
    private int index;
    private boolean finished;

    /**
     * Adds a {@link TweenInstance} to this sequence as a new step.
     *
     * @param t the {@link TweenInstance} to add; must not be null
     * @return the current sequence instance, allowing chained calls
     * @throws NullPointerException if the provided {@link TweenInstance} is null
     */
    public Sequence add(TweenInstance t) {
        if (t == null) throw new NullPointerException("TweenInstance cannot be null");
        steps.add(t);
        return this;
    }

    /**
     * Adds a {@link TweenTask} to this sequence as a new step.
     *
     * @param task the {@link TweenTask} to add; must not be null
     * @return the current sequence instance, allowing chained calls
     * @throws NullPointerException if the provided {@link TweenTask} is null
     */
    public Sequence add(TweenTask task) {
        if (task == null) throw new NullPointerException("TweenTask cannot be null");
        steps.add(task);
        return this;
    }

    @Override
    public void update(float dt) {
        if (finished) return;

        float step = Math.max(0f, dt);

        // No steps
        if (index >= steps.size()) {
            finished = true;
            return;
        }

        Object cur = steps.get(index);

        if (cur instanceof TweenInstance t) {
            t.update(step);

            // If this step finished, advance the index, but DO NOT process the next step this frame.
            if (t.isFinished()) {
                index++;
                if (index >= steps.size()) finished = true;
            }

            return;
        }

        if (cur instanceof TweenTask task) {
            task.update(step);

            if (task.isFinished()) {
                index++;
                if (index >= steps.size()) finished = true;
            }

            return;
        }

        // Unknown/null step: skip it (one skip per frame to avoid while)
        index++;
        if (index >= steps.size()) finished = true;
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