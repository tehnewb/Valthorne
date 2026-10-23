import { PhysicsVector } from "./physics-vector.mjs";

// The Java handle owns this settings reference after the native body is removed.
export class BrowserSoftMesh {
    constructor(world, positions, triangles, edgeIndices, originX, originY, compliance, iterations, radius) {
        const J = world.J;
        for (let i = 0; i < positions.length; i++)
            if (!Number.isFinite(positions[i]) || Math.abs(positions[i]) > 1e15)
                throw new RangeError("Soft body exceeds the native physics coordinate range");
        this.world = world;
        this.originX = originX;
        this.originY = originY;
        this.closed = false;
        this.mesh = new J.SoftBodySharedSettings();
        this.mesh.AddRef();
        let settings;
        let created = false;
        try {
            const count = positions.length / 2 - 1;
            for (let i = 0; i <= count; i++) {
                const vertex = new J.SoftBodySharedSettingsVertex();
                const p = vertex.mPosition;
                p.x = positions[i * 2] - originX;
                p.y = positions[i * 2 + 1] - originY;
                p.z = 0;
                vertex.mInvMass = count + 1;
                try {
                    this.mesh.mVertices.push_back(vertex);
                } finally {
                    if (!world.device.failure) J.destroy(vertex);
                }
            }
            const edges = this.mesh.mEdgeConstraints;
            for (let i = 0; i < edgeIndices.length; i += 2) {
                const edge = new J.SoftBodySharedSettingsEdge(edgeIndices[i], edgeIndices[i + 1], compliance);
                try {
                    edges.push_back(edge);
                } finally {
                    if (!world.device.failure) J.destroy(edge);
                }
            }
            for (let i = 0; i < triangles.length; i += 3) {
                const face = new J.SoftBodySharedSettingsFace(triangles[i], triangles[i + 1], triangles[i + 2], 0);
                try {
                    this.mesh.AddFace(face);
                } finally {
                    if (!world.device.failure) J.destroy(face);
                }
            }
            this.mesh.CalculateEdgeLengths();
            this.mesh.Optimize();
            world.position.Set(originX, originY, 0);
            world.rotation.Set(0, 0, 0, 1);
            settings = new J.SoftBodyCreationSettings(this.mesh, world.position, world.rotation, 1);
            settings.mMakeRotationIdentity = true;
            settings.mUpdatePosition = false;
            settings.mFacesDoubleSided = true;
            settings.mPressure = 0;
            settings.mNumIterations = iterations;
            settings.mVertexRadius = radius;
            this.body = world.bodies.CreateSoftBody(settings);
            if (!J.getPointer(this.body)) throw new Error("Jolt could not allocate a soft body");
            created = true;
            this.id = this.body.GetID().GetIndexAndSequenceNumber() | 0;
            this.properties = J.castObject(this.body.GetMotionProperties(), J.SoftBodyMotionProperties);
            this.vertices = new Array(count + 1);
            for (let i = 0; i <= count; i++) this.vertices[i] = new BrowserSoftVertex(this.properties.GetVertex(i));
            this.edges = new Array(edges.size());
            for (let i = 0; i < this.edges.length; i++) this.edges[i] = edges.at(i);
            world.handles.set(this.id, this.body);
            world.bodies.AddBody(this.body.GetID(), J.EActivation_Activate);
            world.softMeshes.add(this);
        } catch (failure) {
            if (created && !world.device.failure) {
                if (world.bodies.IsAdded(this.body.GetID())) world.bodies.RemoveBody(this.body.GetID());
                world.bodies.DestroyBody(this.body.GetID());
                world.handles.delete(this.id);
            }
            if (!world.device.failure) this.mesh.Release();
            this.mesh = null;
            throw failure;
        } finally {
            if (settings && !world.device.failure) J.destroy(settings);
        }
    }
    getId() {
        return this.id;
    }
    getVertex(index) {
        return this.vertices[index];
    }
    setCompliance(value) {
        for (const edge of this.edges) edge.mCompliance = value;
    }
    setNumIterations(value) {
        this.properties.SetNumIterations(value);
    }
    setLinearDamping(value) {
        this.properties.SetLinearDamping(value);
    }
    setGravityFactor(value) {
        this.properties.SetGravityFactor(value);
    }
    setAllowSleeping(value) {
        this.body.SetAllowSleeping(value);
    }
    capture(positions) {
        for (let i = 0; i < this.vertices.length; i++) {
            const p = this.vertices[i].position;
            const x = Math.fround(this.originX + p.GetX()),
                y = Math.fround(this.originY + p.GetY()),
                z = p.GetZ();
            const velocity = this.vertices[i].velocity;
            if (
                !Number.isFinite(x) ||
                !Number.isFinite(y) ||
                !Number.isFinite(z) ||
                !Number.isFinite(velocity.GetX()) ||
                !Number.isFinite(velocity.GetY()) ||
                !Number.isFinite(velocity.GetZ()) ||
                Math.abs(x) > 1e15 ||
                Math.abs(y) > 1e15 ||
                Math.abs(z) > 1e15
            ) {
                this.world.failed = true;
                throw new Error("Jolt produced an invalid soft-body state; replace this physics world");
            }
            positions[i * 3] = x;
            positions[i * 3 + 1] = y;
            positions[i * 3 + 2] = z;
        }
    }
    close() {
        if (this.closed) return;
        this.closed = true;
        if (!this.world.device.failure) this.mesh.Release();
        this.mesh = null;
        for (const vertex of this.vertices) vertex.close();
        this.vertices = this.edges = null;
        this.properties = this.body = null;
        this.world.softMeshes.delete(this);
    }
}

class BrowserSoftVertex {
    constructor(vertex) {
        this.vertex = vertex;
        this.position = vertex.mPosition;
        this.velocity = vertex.mVelocity;
        this.scratch = new PhysicsVector();
    }
    setPosition(p) {
        this.position.Set(p.getX(), p.getY(), p.getZ());
    }
    getVelocity() {
        return this.scratch.copy(this.velocity);
    }
    setVelocity(v) {
        this.velocity.Set(v.getX(), v.getY(), v.getZ());
    }
    getInvMass() {
        return this.vertex.mInvMass;
    }
    setInvMass(value) {
        this.vertex.mInvMass = value;
    }
    close() {
        this.vertex = this.position = this.velocity = null;
    }
}
