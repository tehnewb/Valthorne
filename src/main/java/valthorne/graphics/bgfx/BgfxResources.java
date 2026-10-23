package valthorne.graphics.bgfx;

import java.util.ArrayList;
import valthorne.graphics.GraphicsBackend;
import valthorne.graphics.GraphicsRecoveryException;
import valthorne.graphics.GraphicsResource;

/**
 * Tracks device-dependent resource lifetimes for frame-boundary backend changes.
 * Registration allocates only during resource creation. Drawing does not traverse
 * this registry. Reset snapshots the registry so dependencies restore in creation
 * order and release in reverse order. A failed transition attempts to restore the
 * original renderer and reports the failure to the requesting caller.
 */
public final class BgfxResources {
    /**
     * Strong references retained until their owners dispose the resources.
     */
    private static final ArrayList<GraphicsResource> resources = new ArrayList<>();
    /**
     * Prevents mutation and reentrant switching during native recreation.
     */
    private static boolean resetting;

    /**
     * Prevents constructing the render-thread resource registry.
     */
    private BgfxResources() {
    }

    /**
     * Registers a successfully initialized engine resource exactly once.
     * Resources created outside JGL retain their explicit lifecycle.
     *
     * @param resource live resource owned by the active application
     */
    public static void register(GraphicsResource resource) {
        /*
         * Resources outside the engine session retain their own lifecycle.
         */
        if (resource == null) throw new NullPointerException("resource");
        if (!BgfxGraphics.isActive()) return;
        BgfxGraphics.device();
        if (resetting) throw new IllegalStateException("Cannot create resources during a graphics transition");
        for (int i = 0; i < resources.size(); i++)
            if (resources.get(i) == resource) return;
        resources.add(resource);
    }

    /**
     * Removes a resource during normal disposal or failed construction.
     *
     * @param resource resource no longer owned by the application
     */
    public static void unregister(GraphicsResource resource) {
        /*
         * Removal is a cold lifecycle operation and needs no concurrent collection.
         */
        if (resource == null) throw new NullPointerException("resource");
        if (BgfxGraphics.isActive()) BgfxGraphics.device();
        if (resetting) throw new IllegalStateException("Cannot dispose resources during a graphics transition");
        for (int i = 0; i < resources.size(); i++) {
            if (resources.get(i) != resource) continue;
            resources.remove(i);
            return;
        }
    }

    /**
     * Replaces the renderer while preserving registered Java resource instances.
     *
     * @param backend requested desktop backend
     */
    public static void switchBackend(GraphicsBackend backend) {
        /*
         * Validate every scope before teardown; rollback uses the same retained
         * descriptors and does not call application init or dispose callbacks.
         */
        BgfxDesktopWindow window = BgfxGraphics.device();
        BgfxPlatform.validate(backend);
        if (resetting) throw new IllegalStateException("A graphics transition is already active");
        if (window.backend() == backend) return;
        GraphicsResource[] snapshot = resources.toArray(new GraphicsResource[0]);
        GraphicsBackend previous = window.backend();
        resetting = true;
        try {
            for (GraphicsResource resource : snapshot) resource.validateGraphicsReset(backend);
            try {
                BgfxGraphics.finishFrame();
                release(snapshot);
                BgfxGraphics.restart(backend);
                restore(snapshot);
            } catch (RuntimeException | Error failure) {
                try {
                    release(snapshot);
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                try {
                    BgfxGraphics.restart(previous);
                    restore(snapshot);
                } catch (RuntimeException | Error rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                    throw new GraphicsRecoveryException(failure);
                }
                throw new IllegalStateException("Could not switch to " + backend + "; restored " + previous, failure);
            }
        } finally {
            resetting = false;
        }
    }

    /**
     * Releases dependents before their providers, attempting all cleanup actions.
     *
     * @param snapshot ordered live resource references
     */
    private static void release(GraphicsResource[] snapshot) {
        /*
         * Continuing cleanup avoids leaving half the old resource graph live.
         */
        Throwable failure = null;
        for (int i = snapshot.length - 1; i >= 0; i--) {
            try {
                snapshot[i].releaseGraphicsResources();
            } catch (RuntimeException | Error error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw new IllegalStateException("Could not release graphics resources", failure);
    }

    /**
     * Restores providers before resources that depend on them.
     *
     * @param snapshot ordered live resource references
     */
    private static void restore(GraphicsResource[] snapshot) {
        /*
         * Stable Java objects retain scene references while native handles change.
         */
        for (GraphicsResource resource : snapshot) resource.restoreGraphicsResources();
    }

    /**
     * Clears references after application/device shutdown.
     */
    static void clear() {
        /*
         * A later JGL session must never inherit resources from a completed runtime.
         */
        resources.clear();
        BgfxTextureHandles.clear();
    }
}
