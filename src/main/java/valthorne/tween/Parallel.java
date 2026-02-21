package valthorne.tween;

import valthorne.collections.stack.FastStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Parallel class allows multiple TweenTasks or TweenInstances to be executed simultaneously.
 * It evaluates all added tasks or instances in parallel and finishes when all tasks have completed.
 * The class is immutable after its construction and acts as a combination of tasks or instances
 * to be processed together.
 *
 * @author Albert Beaupre
 * @since February 20th, 2026
 */
public final class Parallel implements TweenTask {

    private final FastStack<Object> tasks = new FastStack<>(); // TweenInstance or TweenTask.
    private boolean finished;

    /**
     * Adds the specified {@code TweenInstance} to this {@code Parallel} container,
     * allowing it to be executed alongside other tasks or instances in parallel.
     *
     * @param t the {@code TweenInstance} to be added; must not be {@code null}
     * @return this {@code Parallel} instance, allowing method chaining
     * @throws NullPointerException if the specified {@code TweenInstance} is {@code null}
     */
    public Parallel add(TweenInstance t) {
        if (t == null) throw new NullPointerException("TweenInstance cannot be null");
        tasks.push(t);
        return this;
    }

    /**
     * Adds the specified {@code TweenTask} to this {@code Parallel} container,
     * allowing it to be executed alongside other tasks or instances in parallel.
     *
     * @param task the {@code TweenTask} to be added; must not be {@code null}
     * @return this {@code Parallel} instance, allowing method chaining
     * @throws NullPointerException if the specified {@code TweenTask} is {@code null}
     */
    public Parallel add(TweenTask task) {
        if (task == null) throw new NullPointerException("TweenTask cannot be null");
        tasks.push(task);
        return this;
    }

    @Override
    public void update(float dt) {
        if (finished) return;

        float step = Math.max(0f, dt);

        int done = 0;

        for (Object obj : tasks) {
            if (obj instanceof TweenInstance t) {
                t.update(step);
                if (t.isFinished()) done++;
            } else if (obj instanceof TweenTask task) {
                task.update(step);
                if (task.isFinished()) done++;
            } else {
                done++;
            }
        }

        finished = done >= tasks.size();
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