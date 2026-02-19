package valthorne.state;

import java.util.Objects;

/**
 * A queued FSM event (trigger).
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Trigger jump = new Trigger("jump");
 *
 * // Transition requires "jump".
 * fsm.addTransition(idle, jumpState, 10, jump, ctx -> ctx.data().grounded(), 0f, "jump", null);
 *
 * // Fire it from input:
 * if (jumpPressed) {
 *     fsm.fireTrigger("jump");
 * }
 * }</pre>
 *
 * <p>Triggers are stored on the machine as string names.</p>
 * <p>A transition can require a trigger, and the trigger is consumed when that transition is taken.</p>
 *
 * @param name trigger name
 * @author Albert Beaupre
 * @since February 12th, 2026
 */
public record Trigger(String name) {

    /**
     * Creates a trigger.
     *
     * <p>Names must be non-null and non-blank.</p>
     *
     * @param name trigger name
     * @throws NullPointerException     if name is null
     * @throws IllegalArgumentException if name is blank
     */
    public Trigger {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Trigger name must not be blank.");
        }
    }
}