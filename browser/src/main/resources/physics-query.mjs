// Reuses native filters, collectors and ray storage. Soft bodies are excluded
// from the public rigid-body query API before selecting the closest ray hit.
export class BrowserPhysicsQueries {
    constructor(world, callback) {
        this.world = world;
        this.callback = callback;
        this.owners = [];
        const J = world.J;
        try {
            this.broadFilter = this.own(new J.BroadPhaseLayerFilter());
            this.objectFilter = this.own(new J.ObjectLayerFilter());
            this.bodyFilter = this.own(new J.BodyFilterJS());
            this.bodyFilter.ShouldCollide = (pointer) => {
                const id = J.wrapPointer(pointer, J.BodyID).GetIndexAndSequenceNumber() | 0;
                const body = world.handles.get(id);
                return !!body && body.GetBodyType() === J.EBodyType_RigidBody;
            };
            this.bodyFilter.ShouldCollideLocked = (pointer) =>
                J.wrapPointer(pointer, J.Body).GetBodyType() === J.EBodyType_RigidBody;
            this.shapeFilter = this.own(new J.ShapeFilter());
            this.bounds = this.own(new J.AABox());
            this.collector = this.own(new J.CollideShapeBodyCollectorJS());
            this.collector.Reset = () => this.collector.ResetEarlyOutFraction();
            this.collector.AddHit = (pointer) =>
                this.callback(J.wrapPointer(pointer, J.BodyID).GetIndexAndSequenceNumber() | 0);
            this.ray = this.own(new J.RRayCast());
            this.raySettings = this.own(new J.RayCastSettings());
            this.rayCollector = this.own(new J.CastRayClosestHitCollisionCollector());
        } catch (failure) {
            this.close();
            throw failure;
        }
    }
    own(resource) {
        this.owners.push(resource);
        return resource;
    }
    raycast(x, y, dx, dy) {
        this.ray.mOrigin.Set(x, y, 0);
        this.ray.mDirection.Set(dx, dy, 0);
        this.rayCollector.Reset();
        this.world.system
            .GetNarrowPhaseQueryNoLock()
            .CastRay(
                this.ray,
                this.raySettings,
                this.rayCollector,
                this.broadFilter,
                this.objectFilter,
                this.bodyFilter,
                this.shapeFilter,
            );
        return this.rayCollector.HadHit();
    }
    rayBody() {
        return this.rayCollector.mHit.mBodyID.GetIndexAndSequenceNumber() | 0;
    }
    rayFraction() {
        return this.rayCollector.mHit.mFraction;
    }
    point(x, y) {
        this.collector.Reset();
        this.world.vector.Set(x, y, 0);
        this.world.system
            .GetBroadPhaseQuery()
            .CollidePoint(this.world.vector, this.collector, this.broadFilter, this.objectFilter);
    }
    queryBounds(minX, minY, maxX, maxY) {
        this.collector.Reset();
        this.bounds.mMin.Set(minX, minY, -1);
        this.bounds.mMax.Set(maxX, maxY, 1);
        this.world.system
            .GetBroadPhaseQuery()
            .CollideAABox(this.bounds, this.collector, this.broadFilter, this.objectFilter);
    }
    close() {
        while (this.owners.length) this.world.J.destroy(this.owners.pop());
        this.callback = null;
    }
}
