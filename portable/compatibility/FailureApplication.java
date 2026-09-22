package compatibility;

import valthorne.Application;
import valthorne.JGL;
import valthorne.JGLConfiguration;

/**
 * Same-source proof that update failures reach the caller after disposal.
 */
public final class FailureApplication implements Application {
    private boolean disposed;

    static void main(String[] args) {
        var app = new FailureApplication();
        try {
            JGL.init(app, JGLConfiguration.defaults().visible(false));
            throw new AssertionError("Failure did not propagate");
        } catch (IllegalStateException failure) {
            if (!app.disposed || !failure.getMessage().equals("expected failure")) throw new AssertionError(failure);
            System.out.println("COMMON_FAILURE_VALIDATED");
        }
    }

    public void init() {
    }

    public void render() {
    }

    public void update(float dt) {
        throw new IllegalStateException("expected failure");
    }

    public void dispose() {
        disposed = true;
    }
}
