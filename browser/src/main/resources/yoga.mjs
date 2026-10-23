import Yoga from "./vendor/yoga/dist/src/index.js";

/** Retains official Yoga nodes and configurations under stable engine identifiers. */
export class BrowserYoga {
    constructor() {
        this.objects = new Map();
        this.next = 1;
    }
    add(value) {
        const id = this.next++;
        this.objects.set(id, value);
        return id;
    }
    config() {
        return this.add(Yoga.Config.create());
    }
    node(config) {
        return this.add(Yoga.Node.create(this.get(config)));
    }
    get(id) {
        const object = this.objects.get(id);
        if (!object) throw new Error("The Yoga resource has been disposed");
        return object;
    }
    free(id) {
        const object = this.objects.get(id);
        if (!object) return;
        object.free();
        this.objects.delete(id);
    }
    close() {
        for (const [id, object] of this.objects) if (typeof object.getChildCount === "function") this.free(id);
        for (const id of this.objects.keys()) this.free(id);
    }
}
