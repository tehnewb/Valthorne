package valthorne.graphics;

/**
 * Functional interface representing a drawing functionality.
 * This interface defines a single abstract method, {@code draw}, which can
 * be implemented to perform custom drawing logic.
 * <p>
 * Being a functional interface, it can be used as a target for lambda expressions
 * or method references.
 * The caller supplies the rendering context through its surrounding state; this
 * callback does not establish a batch, projection, or graphics context itself.
 *
 * @author Albert Beaupre
 */
@FunctionalInterface
public interface DrawFunction {

    /**
     * Executes the drawing operation.
     * Implementations use the caller's current graphics state and are responsible
     * for any state-restoration obligations of that rendering integration. Exceptions
     * propagate to the invoking code; the interface performs no recovery or scheduling.
     */
    void draw();

}
