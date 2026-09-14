// Opt-out is explicit; normal game exports retain physics by default.
export async function loadPhysicsRuntime(enabled = true, importer = () => import('./vendor/jolt-physics.wasm-compat.js')) {
    if (!enabled) return null;
    const module = await importer();
    return module.default();
}
