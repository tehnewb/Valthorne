// Numeric scratch stays outside native memory, so configuration never loads WASM.
export class PhysicsVector {
    constructor() {
        this.set(0, 0, 0, 1);
    }
    set(x, y, z, w = this.w) {
        this.a = x;
        this.b = y;
        this.c = z;
        this.w = w;
        return this;
    }
    getX() {
        return this.a;
    }
    getY() {
        return this.b;
    }
    getZ() {
        return this.c;
    }
    getW() {
        return this.w;
    }
    x() {
        return this.a;
    }
    y() {
        return this.b;
    }
    setZ(z) {
        this.c = z;
    }
    addInPlace(x, y, z) {
        this.a += x;
        this.b += y;
        this.c += z;
    }
    copy(value) {
        return this.set(value.GetX(), value.GetY(), value.GetZ());
    }
}
