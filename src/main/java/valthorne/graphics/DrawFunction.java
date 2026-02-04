package valthorne.graphics;

/**
 * Functional interface representing a drawing functionality.
 * This interface defines a single abstract method, {@code draw}, which can
 * be implemented to perform custom drawing logic.
 * <p>
 * Being a functional interface, it can be used as a target for lambda expressions
 * or method references.
 */
@FunctionalInterface
public interface DrawFunction {

    /**
     * Executes the drawing operation.
     * This method is intended to be implemented with custom logic
     * for performing a drawing task.
     */
    void draw();

}
