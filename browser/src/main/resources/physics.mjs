import { BrowserJoltWorld } from "./physics-world.mjs";

import { PhysicsVector } from "./physics-vector.mjs";

// JoltInterface owns process globals (factory and registered types). Separate
// modules let independently owned worlds close in any order without unregistering
// another world's live shapes. The browser caches the downloaded WASM module.
export class BrowserPhysics {
    constructor() {
        this.worlds = new Set();
        this.loading = null;
        this.closed = false;
    }
    vector() {
        return new PhysicsVector();
    }
    initialize() {
        if (this.closed) return Promise.reject(new Error("Browser physics session is closed"));
        return (this.loading ??= import("./vendor/physics/jolt-physics.wasm.js"));
    }
    async createWorld(maxBodies, maxPairs, maxContacts) {
        const { default: createJolt } = await this.initialize();
        const J = await createJolt();
        if (this.closed) throw new Error("Browser physics session closed during initialization");
        const world = new BrowserJoltWorld(this, J, maxBodies, maxPairs, maxContacts);
        this.worlds.add(world);
        return world;
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
        this.loading = null;
        if (failures.length) throw new AggregateError(failures, "Could not close browser physics worlds");
    }
}
