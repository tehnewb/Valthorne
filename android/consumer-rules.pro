# JOML probes optional JVM Unsafe implementations and falls back when unavailable.
# Android does not provide the internal JDK implementation.
-dontwarn jdk.internal.misc.Unsafe

# Jolt resolves these callback methods from native code rather than Java callers.
-keepclassmembers class com.github.stephengold.joltjni.CustomCollideShapeBodyCollector {
    public void addHit(int);
}
-keepclassmembers class com.github.stephengold.joltjni.CustomContactListener {
    public void onContactAdded(long, long, long, long);
    public void onContactPersisted(long, long, long, long);
    public void onContactRemoved(long);
    public int onContactValidate(long, long, double, double, double, long);
}
-keepclassmembers class com.github.stephengold.joltjni.BodyFilter {
    public boolean shouldCollide(int);
}
