package valthorne.state;

/**
 * A boolean condition used to decide transitions.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Condition isDead = () -> health <= 0;
 * if (isDead.test()) { ... }
 * }</pre>
 *
 * <p>This interface is a general-purpose predicate and may be used outside the generic FSM
 * (this FSM implementation primarily uses {@link Guard} + {@link Trigger}).</p>
 *
 * @author Albert Beaupre
 * @since February 12th, 2026
 */
@FunctionalInterface
public interface Condition {

    /**
     * Evaluates the condition.
     *
     * @return true if the condition is met
     */
    boolean test();
}