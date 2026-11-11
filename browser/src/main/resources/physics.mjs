import { BrowserJoltWorld } from "./physics-world.mjs";
import { PhysicsVector } from "./physics-vector.mjs";

// The pinned native build reference-counts registration across worlds. One
// session shares a heap starting at 16 MiB, growing only as needed to 256 MiB.
export class BrowserPhysics {
    constructor() {
        this.worlds = new Set();
        this.loading = null;
        this.device = null;
        this.closed = false;
    }
    vector() {
        return new PhysicsVector();
    }
    initialize() {
        if (this.closed) return Promise.reject(new Error("Browser physics session is closed"));
        if (this.device?.failure) this.device = null;
        if (this.device) return Promise.resolve(this.device);
        return (this.loading ??= this.load());
    }
    async load() {
        const device = { J: null, failure: null };
        try {
            const { default: createJolt } = await import("./native/valthorne-jolt.mjs");
            device.J = await createJolt({
                onAbort: (message) => {
                    this.poison(device, new Error("Jolt native memory exhausted or aborted: " + message));
                },
            });
            if (this.closed) throw new Error("Browser physics session closed during initialization");
            this.device = device;
            return device;
        } finally {
            this.loading = null;
        }
    }
    async createWorld(maxBodies, maxPairs, maxContacts, scratchBytes) {
        for (const value of [maxBodies, maxPairs, maxContacts, scratchBytes])
            if (!Number.isInteger(value) || value <= 0)
                throw new RangeError("Physics capacities must be positive integers");
        const device = await this.initialize();
        if (this.closed) throw new Error("Browser physics session is closed");
        const limits = device.J.JoltInterface.prototype;
        if (maxBodies > 0x800000 || maxPairs > limits.sGetMaxPairs() || maxContacts > limits.sGetMaxContacts())
            throw new RangeError("Physics capacity exceeds the native identifier or constraint limit");
        try {
            const world = new BrowserJoltWorld(this, device, maxBodies, maxPairs, maxContacts, scratchBytes);
            this.worlds.add(world);
            return world;
        } catch (failure) {
            if (failure instanceof WebAssembly.RuntimeError) this.poison(device, failure);
            throw failure;
        }
    }
    poison(device, failure) {
        device.failure ??= failure;
        device.J = null;
        if (this.device === device) this.device = null;
        const failures = [];
        for (const world of this.worlds) {
            if (world.device !== device) continue;
            try {
                world.close();
            } catch (cleanup) {
                failures.push(cleanup);
            }
        }
        if (failures.length)
            device.failure = new AggregateError([device.failure, ...failures], "Jolt abort cleanup failed");
    }
    close() {
        if (this.closed) return;
        this.closed = true;
        const failures = [];
        for (const world of this.worlds) {
            try {
                world.close();
            } catch (failure) {
                failures.push(failure);
            }
        }
        this.worlds.clear();
        if (this.device) this.device.J = null;
        this.loading = this.device = null;
        if (failures.length) throw new AggregateError(failures, "Could not close browser physics worlds");
    }
}
