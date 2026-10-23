import { BrowserSoftMesh } from "./physics-soft.mjs";
import { BrowserPhysicsQueries } from "./physics-query.mjs";

// Worlds share one bounded native module and reference-counted registration. Java retains all timing,
// interpolation, body validation, geometry synchronization and contact delivery.
export class BrowserJoltWorld {
    constructor(runtime, device, maxBodies, maxPairs, maxContacts, scratchBytes) {
        const J = device.J;
        this.device = device;
        this.failed = false;
        this.runtime = runtime;
        this.J = J;
        this.handles = new Map();
        this.constraints = new Set();
        this.softMeshes = new Set();
        this.owners = [];
        this.closed = false;
        this.listener = null;
        this.queries = null;
        const settings = new J.JoltSettings();
        const transferred = [];
        try {
            settings.mMaxBodies = maxBodies;
            settings.mMaxBodyPairs = maxPairs;
            settings.mMaxContactConstraints = maxContacts;
            settings.mMaxWorkerThreads = 0;
            settings.mTempAllocatorSize = scratchBytes;
            const pairs = new J.ObjectLayerPairFilterTable(2);
            transferred.push(pairs);
            pairs.EnableCollision(0, 1);
            pairs.EnableCollision(1, 1);
            const mapping = new J.BroadPhaseLayerInterfaceTable(2, 2);
            transferred.push(mapping);
            const layer = new J.BroadPhaseLayer(0);
            try {
                mapping.MapObjectToBroadPhaseLayer(0, layer);
                const moving = new J.BroadPhaseLayer(1);
                try {
                    mapping.MapObjectToBroadPhaseLayer(1, moving);
                } finally {
                    if (!device.failure) J.destroy(moving);
                }
            } finally {
                if (!device.failure) J.destroy(layer);
            }
            const broad = new J.ObjectVsBroadPhaseLayerFilterTable(mapping, 2, pairs, 2);
            transferred.push(broad);
            settings.mObjectLayerPairFilter = pairs;
            settings.mBroadPhaseLayerInterface = mapping;
            settings.mObjectVsBroadPhaseLayerFilter = broad;
            this.physics = new J.JoltInterface(settings);
            transferred.length = 0;
            this.system = this.physics.GetPhysicsSystem();
            this.bodies = this.system.GetBodyInterfaceNoLock();
            this.vector = this.own(new J.Vec3(0, 0, 0));
            this.position = this.own(new J.RVec3(0, 0, 0));
            this.rotation = this.own(new J.Quat(0, 0, 0, 1));
        } catch (failure) {
            if (!device.failure) {
                while (this.owners.length) J.destroy(this.owners.pop());
                if (this.physics) J.destroy(this.physics);
                while (transferred.length) J.destroy(transferred.pop());
            }
            throw failure;
        } finally {
            if (!device.failure) J.destroy(settings);
        }
    }
    own(resource) {
        this.owners.push(resource);
        return resource;
    }
    check() {
        if (this.closed) throw new Error("Physics world is closed");
        if (this.device.failure) throw this.device.failure;
    }
    isFailed() {
        return this.failed || !!this.device.failure;
    }
    isUnavailable() {
        return !!this.device.failure;
    }
    body(id) {
        this.check();
        const body = this.handles.get(id);
        if (!body) throw new Error("Physics body has been destroyed");
        return body;
    }
    v(value) {
        const x = value.getX(),
            y = value.getY(),
            z = value.getZ();
        if (!Number.isFinite(x) || !Number.isFinite(y) || !Number.isFinite(z)) {
            this.failed = true;
            throw new Error("Non-finite vector cannot enter the native physics solver");
        }
        this.vector.Set(x, y, z);
        return this.vector;
    }
    p(value) {
        const x = value.getX(),
            y = value.getY(),
            z = value.getZ();
        // Match the pinned Jolt broadphase's cLargeFloat bounds.
        if (
            !Number.isFinite(x) ||
            !Number.isFinite(y) ||
            !Number.isFinite(z) ||
            Math.abs(x) > 1e15 ||
            Math.abs(y) > 1e15 ||
            Math.abs(z) > 1e15
        ) {
            this.failed = true;
            throw new Error("Position exceeds the native physics coordinate range");
        }
        this.position.Set(x, y, z);
        return this.position;
    }
    q(value) {
        const x = value.getX(),
            y = value.getY(),
            z = value.getZ(),
            w = value.getW();
        if (!Number.isFinite(x) || !Number.isFinite(y) || !Number.isFinite(z) || !Number.isFinite(w)) {
            this.failed = true;
            throw new Error("Non-finite rotation cannot enter the native physics solver");
        }
        this.rotation.Set(x, y, z, w);
        return this.rotation;
    }
    shape(description) {
        const J = this.J;
        let settings;
        if (description.vertices) {
            settings = new J.ConvexHullShapeSettings();
            settings.mMaxConvexRadius = 0;
            const points = settings.mPoints;
            points.resize(description.vertices.length);
            for (let i = 0; i < description.vertices.length; i += 2) {
                points.at(i).Set(description.vertices[i], description.vertices[i + 1], -0.5);
                points.at(i + 1).Set(description.vertices[i], description.vertices[i + 1], 0.5);
            }
        } else if (description.circle) settings = new J.SphereShapeSettings(description.width);
        else {
            this.vector.Set(description.width * 0.5, description.height * 0.5, 0.5);
            settings = new J.BoxShapeSettings(this.vector, 0);
        }
        settings.AddRef();
        try {
            const result = settings.Create();
            try {
                if (result.HasError()) throw new Error("Invalid collision shape: " + result.GetError().c_str());
                const shape = result.Get();
                shape.AddRef();
                return shape;
            } finally {
                // WebIDL value returns borrow a static temporary, not a heap owner.
                if (!this.device.failure) result.Clear();
            }
        } finally {
            if (!this.device.failure) settings.Release();
        }
    }
    createBody(description, values) {
        this.check();
        const J = this.J;
        if (
            !Number.isFinite(values[1]) ||
            !Number.isFinite(values[2]) ||
            Math.abs(values[1]) > 1e15 ||
            Math.abs(values[2]) > 1e15
        )
            throw new RangeError("Body position exceeds the native physics coordinate range");
        if (values[0] === 2 && !Number.isFinite(Math.fround(1 / values[16])))
            throw new RangeError("Body mass is too small for finite native inverse mass");
        const shape = this.shape(description);
        let settings,
            body,
            added = false;
        try {
            settings = new J.BodyCreationSettings();
            settings.SetShape(shape);
            const motion = values[0];
            const moving = motion !== 0;
            settings.mMotionType = [J.EMotionType_Static, J.EMotionType_Kinematic, J.EMotionType_Dynamic][motion];
            settings.mPosition.Set(values[1], values[2], 0);
            settings.mRotation.Set(0, 0, Math.sin(values[3] * 0.5), Math.cos(values[3] * 0.5));
            settings.mObjectLayer = moving ? 1 : 0;
            settings.mAllowedDOFs =
                J.EAllowedDOFs_TranslationX | J.EAllowedDOFs_TranslationY | (values[4] ? 0 : J.EAllowedDOFs_RotationZ);
            settings.mLinearVelocity.Set(moving ? values[5] : 0, moving ? values[6] : 0, 0);
            settings.mAngularVelocity.Set(0, 0, moving && !values[4] ? values[7] : 0);
            settings.mFriction = values[8];
            settings.mRestitution = values[9];
            settings.mLinearDamping = values[10];
            settings.mAngularDamping = values[11];
            settings.mGravityFactor = values[12];
            settings.mAllowSleeping = !!values[13];
            settings.mIsSensor = !!values[14];
            settings.mCollideKinematicVsNonDynamic = moving;
            settings.mMotionQuality =
                values[15] && !values[14] && motion === 2 ? J.EMotionQuality_LinearCast : J.EMotionQuality_Discrete;
            if (motion === 2) {
                settings.mOverrideMassProperties = J.EOverrideMassProperties_CalculateInertia;
                settings.mInertiaMultiplier = description.circle ? 1.25 : 1;
                settings.mMassPropertiesOverride.mMass = values[16];
            }
            body = this.bodies.CreateBody(settings);
            if (!J.getPointer(body)) throw new Error("Jolt could not allocate a body");
            const id = body.GetID().GetIndexAndSequenceNumber() | 0;
            this.bodies.AddBody(body.GetID(), moving ? J.EActivation_Activate : J.EActivation_DontActivate);
            added = true;
            this.handles.set(id, body);
            return id;
        } catch (failure) {
            if (!this.device.failure && body && J.getPointer(body)) {
                if (added) this.bodies.RemoveBody(body.GetID());
                this.bodies.DestroyBody(body.GetID());
            }
            throw failure;
        } finally {
            if (!this.device.failure) {
                if (settings) J.destroy(settings);
                shape.Release();
            }
        }
    }
    createSoftBody(positions, triangles, edges, originX, originY, compliance, iterations, radius) {
        this.check();
        return new BrowserSoftMesh(this, positions, triangles, edges, originX, originY, compliance, iterations, radius);
    }
    destroyBody(id) {
        const body = this.body(id);
        this.bodies.DestroyBody(body.GetID());
        this.handles.delete(id);
    }
    addBody(id, activation) {
        this.bodies.AddBody(this.body(id).GetID(), activation);
    }
    removeBody(id) {
        this.bodies.RemoveBody(this.body(id).GetID());
    }
    isActive(id) {
        return this.bodies.IsActive(this.body(id).GetID());
    }
    activateBody(id) {
        this.bodies.ActivateBody(this.body(id).GetID());
    }
    getPositionAndRotation(id, position, rotation) {
        const body = this.body(id);
        this.bodies.GetPositionAndRotation(body.GetID(), this.position, this.rotation);
        const x = this.position.GetX(),
            y = this.position.GetY(),
            z = this.position.GetZ();
        const qx = this.rotation.GetX(),
            qy = this.rotation.GetY(),
            qz = this.rotation.GetZ(),
            qw = this.rotation.GetW();
        if (
            !Number.isFinite(x) ||
            !Number.isFinite(y) ||
            !Number.isFinite(z) ||
            !Number.isFinite(qx) ||
            !Number.isFinite(qy) ||
            !Number.isFinite(qz) ||
            !Number.isFinite(qw) ||
            Math.abs(x) > 1e15 ||
            Math.abs(y) > 1e15 ||
            Math.abs(z) > 1e15
        ) {
            this.failed = true;
            throw new Error("Jolt produced an invalid body pose; replace this physics world");
        }
        position.set(x, y, z);
        rotation.set(qx, qy, qz, qw);
    }
    setPositionAndRotation(id, position, rotation, activation) {
        this.bodies.SetPositionAndRotation(this.body(id).GetID(), this.p(position), this.q(rotation), activation);
    }
    setPosition(id, position, activation) {
        this.bodies.SetPosition(this.body(id).GetID(), this.p(position), activation);
    }
    moveKinematic(id, position, rotation, seconds) {
        this.bodies.MoveKinematic(this.body(id).GetID(), this.p(position), this.q(rotation), seconds);
    }
    getLinearVelocity(id, destination) {
        this.readVector(this.body(id).GetLinearVelocity(), destination);
    }
    readVector(value, destination) {
        const x = value.GetX(),
            y = value.GetY(),
            z = value.GetZ();
        if (!Number.isFinite(x) || !Number.isFinite(y) || !Number.isFinite(z)) {
            this.failed = true;
            throw new Error("Jolt produced a non-finite velocity; replace this physics world");
        }
        destination.set(x, y, z);
    }
    setLinearVelocity(id, x, y, z) {
        this.vector.Set(x, y, z);
        this.bodies.SetLinearVelocity(this.body(id).GetID(), this.vector);
    }
    getAngularVelocity(id, destination) {
        this.readVector(this.body(id).GetAngularVelocity(), destination);
    }
    setAngularVelocity(id, velocity) {
        this.bodies.SetAngularVelocity(this.body(id).GetID(), this.v(velocity));
    }
    addForce(id, force) {
        this.bodies.AddForce(this.body(id).GetID(), this.v(force), this.J.EActivation_Activate);
    }
    addImpulse(id, impulse, position) {
        if (position) this.bodies.AddImpulse(this.body(id).GetID(), this.v(impulse), this.p(position));
        else this.bodies.AddImpulse(this.body(id).GetID(), this.v(impulse));
    }
    addTorque(id, torque) {
        this.bodies.AddTorque(this.body(id).GetID(), this.v(torque), this.J.EActivation_Activate);
    }
    addAngularImpulse(id, impulse) {
        this.bodies.AddAngularImpulse(this.body(id).GetID(), this.v(impulse));
    }
    setMotionType(id, motion, activation) {
        this.bodies.SetMotionType(
            this.body(id).GetID(),
            [this.J.EMotionType_Static, this.J.EMotionType_Kinematic, this.J.EMotionType_Dynamic][motion],
            activation,
        );
    }
    setObjectLayer(id, layer) {
        this.bodies.SetObjectLayer(this.body(id).GetID(), layer);
    }
    setFriction(id, friction) {
        this.bodies.SetFriction(this.body(id).GetID(), friction);
    }
    getFriction(id) {
        return this.bodies.GetFriction(this.body(id).GetID());
    }
    setRestitution(id, restitution) {
        this.bodies.SetRestitution(this.body(id).GetID(), restitution);
    }
    getRestitution(id) {
        return this.bodies.GetRestitution(this.body(id).GetID());
    }
    setGravity(x, y, z) {
        this.vector.Set(x, y, z);
        this.system.SetGravity(this.vector);
    }
    update(seconds, collisionSteps) {
        this.check();
        if (this.failed) throw new Error("Physics world failed; create a replacement before stepping");
        try {
            const errors = this.physics.Step(seconds, collisionSteps);
            if (errors) this.failed = true;
            return errors;
        } catch (failure) {
            this.failed = true;
            if (failure instanceof WebAssembly.RuntimeError) this.runtime.poison(this.device, failure);
            throw failure;
        }
    }
    getNumActiveBodies() {
        return this.system.GetNumActiveBodies(this.J.EBodyType_RigidBody);
    }
    setContactListener(callback) {
        if (this.device.failure) {
            this.listener = null;
            return;
        }
        this.system.SetContactListener(0);
        if (this.listener) this.J.destroy(this.listener);
        this.listener = null;
        if (!callback) return;
        const J = this.J;
        const listener = new J.ContactListenerJS();
        listener.OnContactValidate = () => J.ValidateResult_AcceptAllContactsForThisBodyPair;
        const capture = (type, a, b, pointer) => {
            const first = J.wrapPointer(a, J.Body),
                second = J.wrapPointer(b, J.Body);
            const manifold = J.wrapPointer(pointer, J.ContactManifold),
                normal = manifold.mWorldSpaceNormal;
            callback(
                type,
                first.GetID().GetIndexAndSequenceNumber() | 0,
                second.GetID().GetIndexAndSequenceNumber() | 0,
                manifold.mSubShapeID1.GetValue() | 0,
                manifold.mSubShapeID2.GetValue() | 0,
                normal.GetX(),
                normal.GetY(),
                manifold.mPenetrationDepth,
            );
        };
        listener.OnContactAdded = (a, b, m) => capture(0, a, b, m);
        listener.OnContactPersisted = (a, b, m) => capture(1, a, b, m);
        listener.OnContactRemoved = (pointer) => {
            const pair = J.wrapPointer(pointer, J.SubShapeIDPair);
            callback(
                2,
                pair.GetBody1ID().GetIndexAndSequenceNumber() | 0,
                pair.GetBody2ID().GetIndexAndSequenceNumber() | 0,
                pair.GetSubShapeID1().GetValue() | 0,
                pair.GetSubShapeID2().GetValue() | 0,
                0,
                0,
                0,
            );
        };
        this.listener = listener;
        this.system.SetContactListener(listener);
    }
    optimizeBroadPhase() {
        this.system.OptimizeBroadPhase();
    }
    createConstraint(first, second, distance, firstX, firstY, secondX, secondY, minimum, maximum) {
        const J = this.J;
        const settings = distance ? new J.DistanceConstraintSettings() : new J.PointConstraintSettings();
        settings.AddRef();
        try {
            settings.mSpace = J.EConstraintSpace_WorldSpace;
            settings.mPoint1.Set(firstX, firstY, 0);
            settings.mPoint2.Set(secondX, secondY, 0);
            if (distance) {
                settings.mMinDistance = minimum;
                settings.mMaxDistance = maximum;
            }
            const native = this.bodies.CreateConstraint(settings, this.body(first).GetID(), this.body(second).GetID());
            const constraint = J.castObject(native, distance ? J.DistanceConstraint : J.PointConstraint);
            if (!J.getPointer(constraint)) throw new Error("Jolt could not create a constraint");
            constraint.AddRef();
            const result = new BrowserConstraint(this, constraint, distance);
            this.constraints.add(result);
            return result;
        } finally {
            if (!this.device.failure) settings.Release();
        }
    }
    addConstraint(constraint) {
        this.check();
        this.system.AddConstraint(constraint.native);
        constraint.added = true;
    }
    removeConstraint(constraint) {
        if (constraint.added && !this.device.failure) this.system.RemoveConstraint(constraint.native);
        constraint.added = false;
    }
    prepareQueries(callback) {
        if (!this.queries) this.queries = new BrowserPhysicsQueries(this, callback);
    }
    raycast(x, y, dx, dy) {
        return this.queries.raycast(x, y, dx, dy);
    }
    rayBody() {
        return this.queries.rayBody();
    }
    rayFraction() {
        return this.queries.rayFraction();
    }
    queryPoint(x, y) {
        this.queries.point(x, y);
    }
    queryBounds(minX, minY, maxX, maxY) {
        this.queries.queryBounds(minX, minY, maxX, maxY);
    }
    closeQueries() {
        this.queries?.close();
        this.queries = null;
    }
    close() {
        if (this.closed) return;
        const failures = [];
        const release = (operation) => {
            try {
                operation();
            } catch (failure) {
                failures.push(failure);
            }
        };
        release(() => this.setContactListener(null));
        release(() => this.closeQueries());
        for (const constraint of this.constraints) release(() => constraint.close());
        for (const [id, body] of this.handles) {
            if (this.device.failure) break;
            release(() => {
                if (this.bodies.IsAdded(body.GetID())) this.bodies.RemoveBody(body.GetID());
            });
            release(() => this.destroyBody(id));
        }
        for (const mesh of this.softMeshes) release(() => mesh.close());
        this.closed = true;
        this.runtime.worlds.delete(this);
        while (this.owners.length) {
            const owned = this.owners.pop();
            if (!this.device.failure) release(() => this.J.destroy(owned));
        }
        if (!this.device.failure) release(() => this.J.destroy(this.physics));
        this.physics = this.system = this.bodies = null;
        this.vector = this.position = this.rotation = null;
        this.J = null;
        if (this.device.failure && this.runtime.device === this.device) this.runtime.device = null;
        this.handles.clear();
        this.constraints.clear();
        this.softMeshes.clear();
        if (failures.length) throw new AggregateError(failures, "Could not close Jolt native resources");
    }
}

class BrowserConstraint {
    constructor(world, native, distance) {
        this.world = world;
        this.native = native;
        this.distance = distance;
        this.added = false;
    }
    isDistance() {
        return this.distance;
    }
    getEnabled() {
        return this.native.GetEnabled();
    }
    setEnabled(enabled) {
        this.native.SetEnabled(enabled);
    }
    getMinDistance() {
        return this.native.GetMinDistance();
    }
    getMaxDistance() {
        return this.native.GetMaxDistance();
    }
    setDistance(minimum, maximum) {
        this.native.SetDistance(minimum, maximum);
    }
    close() {
        if (!this.native) return;
        this.world.removeConstraint(this);
        if (!this.world.device.failure) this.native.Release();
        this.native = null;
        this.world.constraints.delete(this);
    }
}
