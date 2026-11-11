package valthorne.browser.physics;

import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

/**
 * Loads Jolt WASM only when an application first requests physics. Initialization
 * suspends the Java caller, keeping the browser responsive and preserving its
 * owner coroutine. Module failures propagate to that caller. Worlds belong to
 * the current application host, which also closes leaked worlds during teardown.
 * Worlds share one heap starting at 16 MiB and bounded at 256 MiB. Native
 * allocation aborts invalidate its worlds; replacement worlds use a fresh module.
 */
public final class BrowserPhysics {
    /**
     * Bridge activation code requesting that Jolt wake the affected body.
     */
    public static final int ACTIVATE = 0;

    /**
     * Bridge activation code preserving native sleep while changing membership.
     */
    public static final int DONT_ACTIVATE = 1;

    /**
     * Prevents instantiation of the browser physics entry points.
     */
    private BrowserPhysics() {
    }

    /**
     * Suspends until the current host has initialized its optional Jolt module.
     */
    @Async
    public static native void initialize();

    /**
     * Resumes the creating Java coroutine after native module initialization.
     * @param callback suspended Java continuation
     */
    private static void initialize(AsyncCallback<Void> callback) {
        /*
         * Complete the same Java continuation after loading rather than starting
         * a second game frame while the WASM module is still unavailable.
         */
        request(error -> {
            if (error == null) callback.complete(null);
            else callback.error(new IllegalStateException("Unable to initialize browser Jolt: " + error));
        });
    }

    /**
     * Starts lazy initialization owned by the current browser host.
     * @param callback one-shot completion callback
     */
    @JSBody(params = {"callback"}, script = "globalThis.valthorneHost.physics.initialize().then(()=>callback(null),error=>callback(String(error)));")
    private static native void request(PhysicsReadyCallback callback);

    /**
     * Allocates plain numeric scratch without initializing or retaining WASM.
     * @return reusable vector with identity quaternion W
     */
    @JSBody(script = "return globalThis.valthorneHost.physics.vector();")
    public static native PhysicsVector vector();

    /**
     * Copies validated world capacities into a new initialized WASM world.
     * @param maxBodies native body capacity
     * @param maxPairs native broadphase pair capacity
     * @param maxContacts native contact constraint capacity
     * @param scratchBytes initial temporary arena capacity, with native heap fallback
     * @return world owned by both the Java handle and current host
     */
    @Async
    public static native BrowserPhysicsWorld createWorld(int maxBodies, int maxPairs, int maxContacts, int scratchBytes);

    /**
     * Preserves owner coroutine identity while creating a world on the shared module.
     * @param maxBodies native body capacity
     * @param maxPairs native pair capacity
     * @param maxContacts native contact capacity
     * @param scratchBytes initial temporary arena capacity, with native heap fallback
     * @param callback suspended world constructor
     */
    private static void createWorld(int maxBodies, int maxPairs, int maxContacts, int scratchBytes, AsyncCallback<BrowserPhysicsWorld> callback) {
        /*
         * Worlds share reference-counted native registration; module creation must not
         * block the browser or register another Java application frame.
         */
        requestWorld(maxBodies, maxPairs, maxContacts, scratchBytes, (world, error) -> {
            if (error == null) callback.complete(world);
            else callback.error(new IllegalStateException("Unable to create browser Jolt world: " + error));
        });
    }

    /**
     * Requests a world belonging to the active browser application host.
     * @param maxBodies native body capacity
     * @param maxPairs native pair capacity
     * @param maxContacts native contact capacity
     * @param scratchBytes initial temporary arena capacity, with native heap fallback
     * @param callback one-shot creation result
     */
    @JSBody(params = {"maxBodies", "maxPairs", "maxContacts", "scratchBytes", "callback"}, script = "globalThis.valthorneHost.physics.createWorld(maxBodies,maxPairs,maxContacts,scratchBytes).then(world=>callback(world,null),error=>callback(null,String(error)));")
    private static native void requestWorld(int maxBodies, int maxPairs, int maxContacts, int scratchBytes, PhysicsWorldResult callback);

    /**
     * Describes collision geometry for synchronous native body creation.
     * @param circle whether the width is a disk radius
     * @param width full box width or circle radius in meters
     * @param height full box height in meters
     * @param vertices copied convex XY outline, or null for box and disk
     * @return borrowed immutable descriptor consumed during body creation
     */
    @JSBody(params = {"circle", "width", "height", "vertices"}, script = "return {circle:circle,width:width,height:height,vertices:vertices};")
    public static native JSObject shape(boolean circle, float width, float height, float[] vertices);
}
