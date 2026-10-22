package valthorne.ui.behavior.file;

import java.io.IOException;

/**
 * One checked filesystem or desktop operation executed outside the render thread.
 * Completion and failure are returned to the owning UI by its command controller.
 */
@FunctionalInterface
public interface FileCommand {

    /**
     * Performs the captured operation without accessing UI or graphics state.
     *
     * @throws IOException when validation, file IO, or native dispatch fails
     */
    void execute() throws IOException;
}
