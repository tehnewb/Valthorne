package valthorne.tween;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A wrapper class for repeating a {@link TweenTask} multiple times, or indefinitely.
 * The {@code Repeat} class creates a new instance of the given task for each repetition
 * using the provided {@code Supplier}. This ensures a fresh start for each iteration.
 * <p>
 * The repetition count can be specified:
 * - A value of {@code 0} means the task will execute once.
 * - A positive value specifies the number of repetitions (including the first execution).
 * - A value of {@code -1} indicates infinite repetition.
 * <p>
 * The repeat cycle ends once the specified count is reached or the internal task
 * signals that it has finished. If the {@code Supplier} fails to provide a valid task at any iteration,
 * the loop is terminated.
 *
 * @author Albert Beaupre
 * @since February 20th, 2026
 */
public final class Repeat implements TweenTask {

    private final Supplier<? extends TweenTask> factory;
    private final int count; // -1 = infinite.

    private TweenTask current;
    private int played;
    private boolean finished;

    /**
     * Constructs a new Repeat task to execute a {@link TweenTask} multiple times or indefinitely.
     *
     * @param factory a {@link Supplier} that provides new instances of {@link TweenTask} for each repetition
     * @param count   the number of times the task should repeat; use -1 for infinite repetition
     * @throws NullPointerException if the factory is null or returns a null {@link TweenTask}
     */
    public Repeat(Supplier<? extends TweenTask> factory, int count) {
        this.factory = Objects.requireNonNull(factory, "The factory cannot be null.");
        this.count = count;
        this.current = factory.get();
        if (this.current == null) throw new NullPointerException("The factory returned a null object");
    }

    @Override
    public void update(float dt) {
        if (finished) return;

        current.update(dt);

        if (!current.isFinished()) return;

        played++;

        if (count != -1 && played > count) {
            finished = true;
            return;
        }

        current = factory.get();
        if (current == null) {
            finished = true;
        }
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void cancel() {
        finished = true;
        if (current != null) current.cancel();
    }
}