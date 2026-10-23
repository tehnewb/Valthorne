import { PhysicsVector } from "./physics-vector.mjs";

// The Java handle owns this settings reference after the native body is removed.
export class BrowserSoftMesh {
    constructor(world, positions, originX, originY, compliance, iterations, radius) {
        const J = world.J;
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
                    J.destroy(vertex);
                }
            }
            const edges = this.mesh.mEdgeConstraints;
            for (let i = 0; i < count; i++) {
                const next = (i + 1) % count;
                const area =
                    (positions[next * 2] - positions[i * 2]) * (positions[count * 2 + 1] - positions[i * 2 + 1]) -
                    (positions[next * 2 + 1] - positions[i * 2 + 1]) * (positions[count * 2] - positions[i * 2]);
                if (area === 0) throw new Error("Soft mesh contains a degenerate triangle");
                for (let n = 0; n < 2; n++) {
                    const edge = new J.SoftBodySharedSettingsEdge(i, n === 0 ? next : count, compliance);
                    try {
                        edges.push_back(edge);
                    } finally {
                        J.destroy(edge);
                    }
                }
                const face = new J.SoftBodySharedSettingsFace(count, i, next, 0);
                try {
                    this.mesh.AddFace(face);
                } finally {
                    J.destroy(face);
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
            this.edges = new Array(count * 2);
            for (let i = 0; i < this.edges.length; i++) this.edges[i] = edges.at(i);
            world.handles.set(this.id, this.body);
            world.bodies.AddBody(this.body.GetID(), J.EActivation_Activate);
            world.softMeshes.add(this);
        } catch (failure) {
            if (created) {
                if (world.bodies.IsAdded(this.body.GetID())) world.bodies.RemoveBody(this.body.GetID());
                world.bodies.DestroyBody(this.body.GetID());
                world.handles.delete(this.id);
            }
            this.mesh.Release();
            this.mesh = null;
            throw failure;
        } finally {
            if (settings) J.destroy(settings);
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
            positions[i * 3] = this.originX + p.GetX();
            positions[i * 3 + 1] = this.originY + p.GetY();
            positions[i * 3 + 2] = p.GetZ();
        }
    }
    close() {
        if (this.closed) return;
        this.closed = true;
        this.mesh.Release();
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
