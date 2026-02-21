package valthorne.tween;

import valthorne.collections.array.SwapOnRemoveArray;

/**
 * Manages and updates a collection of tween instances and tasks.
 * Provides functionality to add, update, remove, clear, and query
 * the state of active tweens or tasks.
 *
 * @author Albert Beaupre
 * @since February 20st, 2026
 */
public final class TweenManager {

    private final SwapOnRemoveArray<Object> active = new SwapOnRemoveArray<>(); // TweenInstance or TweenTask.

    /**
     * Adds a given tween instance to the manager's collection of active tweens.
     *
     * @param tween the tween instance to be added; must not be null
     * @return the same tween instance that was added
     * @throws NullPointerException if the provided tween instance is null
     */
    public TweenInstance add(TweenInstance tween) {
        if (tween == null) throw new NullPointerException("tween");
        active.add(tween);
        return tween;
    }

    /**
     * Adds the given tween task to the manager's collection of active tasks.
     * The task will be processed and updated by the manager.
     *
     * @param task the tween task to be added; must not be null
     * @return the same tween task that was added
     * @throws NullPointerException if the provided tween task is null
     */
    public TweenTask add(TweenTask task) {
        if (task == null) throw new NullPointerException("task");
        active.add(task);
        return task;
    }

    /**
     * Updates all active tween instances and tasks in the manager over a given time step.
     * Elements are updated based on the provided delta time (delta), and completed elements
     * are removed from the manager. Removes invalid or non-conforming elements as well.
     *
     * @param delta the delta time used for updating the elements; must be non-negative
     *              (values less than 0 will be clamped to 0)
     */
    public void update(float delta) {
        float step = Math.max(0f, delta);

        // Important: when we remove(i) we swap last into i, so we must NOT increment i in that case.
        for (int i = 0; i < active.size(); ) {
            Object obj = active.get(i);

            if (obj instanceof TweenInstance t) {
                t.update(step);
                if (t.isFinished()) {
                    active.remove(i);
                    continue; // re-check the swapped-in element
                }
            } else if (obj instanceof TweenTask task) {
                task.update(step);
                if (task.isFinished()) {
                    active.remove(i);
                    continue; // re-check the swapped-in element
                }
            } else {
                active.remove(i);
                continue; // re-check the swapped-in element
            }

            i++; // only advance if we did not remove
        }
    }

    /**
     * Removes all currently active tweens and tasks managed by this TweenManager.
     * This operation clears the internal collection, effectively resetting the manager.
     */
    public void clear() {
        active.clear();
    }

    /**
     * Returns the total number of active tweens or tasks currently managed
     * by the TweenManager instance.
     *
     * @return the number of active elements in the manager
     */
    public int size() {
        return active.size();
    }

    /**
     * Checks whether the collection of active tweens or tasks managed by
     * the TweenManager is empty.
     *
     * @return true if there are no active tweens or tasks; false otherwise
     */
    public boolean isEmpty() {
        return active.isEmpty();
    }
}