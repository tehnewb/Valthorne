package valthorne.math.physics;

import com.github.stephengold.joltjni.Jolt;

/**
 * Initializes the Android Jolt library packaged by the matching Jolt-JNI AAR.
 * Registration happens once per application process; individual worlds retain
 * their existing ownership and disposal behavior. Android loads the correct ABI
 * directly from the installed application, without desktop library extraction.
 */
public final class JoltRuntime {
    /**
     * Whether native registration completed successfully under the class monitor.
     */
    private static boolean initialized;

    /**
     * Prevents construction of the process-wide physics bootstrap.
     */
    private JoltRuntime() {
    }

    /**
     * Loads and registers the single-precision Android physics runtime once.
     * Failed loading or registration propagates to the caller.
     */
    public static synchronized void initialize() {
        if (initialized) return;
        System.loadLibrary("joltjni");
        Jolt.registerDefaultAllocator();
        Jolt.installDefaultTraceCallback();
        if (!Jolt.newFactory()) throw new IllegalStateException("Unable to create Jolt factory");
        Jolt.registerTypes();
        initialized = true;
    }
}
