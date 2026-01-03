package valthorne.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@code EventPriority} annotation specifies the execution order of methods within an
 * {@code EventListener} when handling an {@code Event}. Higher priority values indicate
 * that the method will be invoked earlier, while lower values delay execution.
 *
 * <p>
 * This annotation is processed at runtime by the {@code EventPublisher}.
 *
 * @author Albert Beaupre
 * @since August 29th, 2024
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventPriority {

    /**
     * The priority value determining the order of execution. Higher values result in
     * earlier handling; defaults to {@code 0} (normal priority).
     *
     * @return the priority level
     */
    int priority() default 0;
}