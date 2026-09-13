// Jolt WASM backend for the existing PhysicsWorld3D API. Coordinates stay Z-up.
// All native value arguments are copied by Jolt; owned scratch lives with the world.
export class BrowserPhysicsWorld {
    constructor(J, capacities, collisions, contact) {
        this.J=J; this.handles=new Map(); this.joints=new Set(); this.closed=false;
        const settings=new J.JoltSettings();
        settings.mMaxBodies=capacities[0];settings.mMaxBodyPairs=capacities[1];settings.mMaxContactConstraints=capacities[2];
        const pairs=new J.ObjectLayerPairFilterTable(32), mapping=new J.BroadPhaseLayerInterfaceTable(32,2);
        const layers=[new J.BroadPhaseLayer(0),new J.BroadPhaseLayer(1)];
        for(let a=0;a<32;a++){
            mapping.MapObjectToBroadPhaseLayer(a,layers[a%2]);
            for(let b=a;b<32;b++)if((a%2||b%2)&&collisions[(a>>1)*16+(b>>1)])pairs.EnableCollision(a,b);
        }
        layers.forEach(v=>J.destroy(v));
        settings.mObjectLayerPairFilter=pairs;settings.mBroadPhaseLayerInterface=mapping;
        settings.mObjectVsBroadPhaseLayerFilter=new J.ObjectVsBroadPhaseLayerFilterTable(mapping,2,pairs,32);
        try{this.physics=new J.JoltInterface(settings);}finally{J.destroy(settings);}
        this.system=this.physics.GetPhysicsSystem();this.bodies=this.system.GetBodyInterface();
        this.vector=new J.Vec3(0,0,-9.81);this.position=new J.RVec3(0,0,0);this.rotation=new J.Quat(0,0,0,1);
        this.system.SetGravity(this.vector);this.result=new Float32Array(7);
        this.listener=new J.ContactListenerJS();
        this.listener.OnContactValidate=()=>J.ValidateResult_AcceptAllContactsForThisBodyPair;
        const capture=(type,a,b,m)=>{
            const first=J.wrapPointer(a,J.Body),second=J.wrapPointer(b,J.Body),manifold=J.wrapPointer(m,J.ContactManifold),n=manifold.mWorldSpaceNormal;
            contact(type,first.GetID().GetIndexAndSequenceNumber()|0,second.GetID().GetIndexAndSequenceNumber()|0,
                manifold.mSubShapeID1.GetValue()|0,manifold.mSubShapeID2.GetValue()|0,n.GetX(),n.GetY(),n.GetZ(),manifold.mPenetrationDepth);
        };
        this.listener.OnContactAdded=(a,b,m)=>capture(0,a,b,m);
        this.listener.OnContactPersisted=(a,b,m)=>capture(1,a,b,m);
        this.listener.OnContactRemoved=p=>{const pair=J.wrapPointer(p,J.SubShapeIDPair);contact(2,pair.GetBody1ID().GetIndexAndSequenceNumber()|0,pair.GetBody2ID().GetIndexAndSequenceNumber()|0,pair.GetSubShapeID1().GetValue()|0,pair.GetSubShapeID2().GetValue()|0,0,0,0,0);};
        this.system.SetContactListener(this.listener);
    }
    check(){if(this.closed)throw new Error('Physics world is closed');}
    body(id){this.check();const body=this.handles.get(id);if(!body)throw new Error('Rigid body has been destroyed');return body;}
    // Shape settings are reference counted, including nested rotated settings.
    shape(type,data){
        const J=this.J;let settings;
        if(type===0){const extent=new J.Vec3(data[0]/2,data[1]/2,data[2]/2);try{settings=new J.BoxShapeSettings(extent,Math.min(.05,...data.map(v=>v/4)));}finally{J.destroy(extent);}}
        else if(type===1)settings=new J.SphereShapeSettings(data[0]);
        else if(type===2||type===3){
            const inner=type===2?new J.CapsuleShapeSettings((data[1]-2*data[0])/2,data[0]):new J.CylinderShapeSettings(data[1]/2,data[0],Math.min(.05,data[0]/2,data[1]/4));
            const p=new J.Vec3(0,0,0),q=new J.Quat(Math.SQRT1_2,0,0,Math.SQRT1_2);
            try{settings=new J.RotatedTranslatedShapeSettings(p,q,inner);}finally{J.destroy(p);J.destroy(q);}
        }else if(type===4){
            settings=new J.ConvexHullShapeSettings();settings.mMaxConvexRadius=0;
            settings.mPoints.resize(data.length/3);
            for(let i=0;i<data.length;i+=3)settings.mPoints.at(i/3).Set(data[i],data[i+1],data[i+2]);
        }else{
            const triangles=new J.TriangleList();triangles.resize(data.length/9);
            const v=new J.Float3(0,0,0);
            try{for(let i=0;i<data.length;i+=3){v.x=data[i];v.y=data[i+1];v.z=data[i+2];triangles.at(Math.floor(i/9)).set_mV((i/3)%3,v);}settings=new J.MeshShapeSettings(triangles);}finally{J.destroy(v);J.destroy(triangles);}
        }
        settings.AddRef();
        try{
            const result=settings.Create();
            try{if(result.HasError())throw new Error('Invalid collision shape: '+result.GetError().c_str());const shape=result.Get();shape.AddRef();return shape;}finally{J.destroy(result);}
        }finally{settings.Release();}
    }
    create(type,data,values){
        this.check();const J=this.J,shape=this.shape(type,data),s=new J.BodyCreationSettings();
        try{
            s.SetShape(shape);s.mPosition.Set(values[0],values[1],values[2]);s.mRotation.Set(values[3],values[4],values[5],values[6]);
            s.mLinearVelocity.Set(values[7],values[8],values[9]);
            const motion=values[10];s.mMotionType=[J.EMotionType_Static,J.EMotionType_Kinematic,J.EMotionType_Dynamic][motion];
            s.mObjectLayer=values[11]*2+(motion===0?0:1);s.mFriction=values[12];s.mRestitution=values[13];
            s.mLinearDamping=values[14];s.mAngularDamping=values[15];s.mGravityFactor=values[16];
            s.mIsSensor=!!values[17];s.mAllowSleeping=!!values[18];s.mMotionQuality=values[19]?J.EMotionQuality_LinearCast:J.EMotionQuality_Discrete;
            s.mAllowedDOFs=values[20]?J.EAllowedDOFs_TranslationX|J.EAllowedDOFs_TranslationY|J.EAllowedDOFs_TranslationZ:J.EAllowedDOFs_All;
            if(motion!==0){s.mOverrideMassProperties=J.EOverrideMassProperties_CalculateInertia;s.mMassPropertiesOverride.mMass=values[21];}
            s.mCollideKinematicVsNonDynamic=motion===1;
            const body=this.bodies.CreateBody(s);
            if(!J.getPointer(body))throw new Error('Jolt could not allocate a body');
            const id=body.GetID().GetIndexAndSequenceNumber()|0;
            this.bodies.AddBody(body.GetID(),motion===0?J.EActivation_DontActivate:J.EActivation_Activate);
            this.handles.set(id,body);return id;
        }finally{J.destroy(s);shape.Release();}
    }
    read(id,kind){
        const b=this.body(id),r=this.result,p=kind===0?b.GetPosition():kind===1?b.GetLinearVelocity():b.GetAngularVelocity();
        r[0]=p.GetX();r[1]=p.GetY();r[2]=p.GetZ();
        if(kind===0){const q=b.GetRotation();r[3]=q.GetX();r[4]=q.GetY();r[5]=q.GetZ();r[6]=q.GetW();}return r;
    }
    change(id,op,x,y,z,px=0,py=0,pz=0){
        const b=this.body(id),bid=b.GetID(),J=this.J;this.vector.Set(x,y,z);
        switch(op){
            case 0:this.bodies.SetLinearVelocity(bid,this.vector);this.bodies.ActivateBody(bid);break;
            case 1:this.bodies.SetAngularVelocity(bid,this.vector);this.bodies.ActivateBody(bid);break;
            case 2:this.bodies.AddImpulse(bid,this.vector);break;
            case 3:this.position.Set(px,py,pz);this.bodies.AddImpulse(bid,this.vector,this.position);break;
            case 4:this.bodies.AddAngularImpulse(bid,this.vector);break;
            case 5:this.bodies.AddForce(bid,this.vector,J.EActivation_Activate);break;
            case 6:this.bodies.AddTorque(bid,this.vector,J.EActivation_Activate);break;
        }
    }
    pose(id,x,y,z,qx,qy,qz,qw,dt){
        const b=this.body(id);this.position.Set(x,y,z);this.rotation.Set(qx,qy,qz,qw);
        if(dt>0)this.bodies.MoveKinematic(b.GetID(),this.position,this.rotation,dt);
        else this.bodies.SetPositionAndRotation(b.GetID(),this.position,this.rotation,b.IsStatic()?this.J.EActivation_DontActivate:this.J.EActivation_Activate);
    }
    active(id,operation){const bid=this.body(id).GetID();if(operation===1)this.bodies.ActivateBody(bid);if(operation===2)this.bodies.DeactivateBody(bid);return this.bodies.IsActive(bid);}
    gravity(x,y,z){this.vector.Set(x,y,z);this.system.SetGravity(this.vector);}
    getGravity(){const p=this.system.GetGravity();this.result[0]=p.GetX();this.result[1]=p.GetY();this.result[2]=p.GetZ();return this.result;}
    step(dt){this.check();this.physics.Step(dt,1);}
    destroy(id){const body=this.body(id);this.bodies.RemoveBody(body.GetID());this.bodies.DestroyBody(body.GetID());this.handles.delete(id);}
    ray(x,y,z,dx,dy,dz,ignore){
        this.check();const J=this.J,owned=[],own=v=>(owned.push(v),v);
        try{
            const origin=own(new J.RVec3(x,y,z)),direction=own(new J.Vec3(dx,dy,dz)),ray=own(new J.RRayCast(origin,direction));
            const settings=own(new J.RayCastSettings()),collector=own(new J.CastRayClosestHitCollisionCollector());
            const bp=own(new J.BroadPhaseLayerFilter()),layer=own(new J.ObjectLayerFilter()),shape=own(new J.ShapeFilter());
            const filter=own(ignore===null?new J.BodyFilter():new J.IgnoreSingleBodyFilter(this.body(ignore).GetID()));
            this.system.GetNarrowPhaseQuery().CastRay(ray,settings,collector,bp,layer,filter,shape);
            if(!collector.HadHit())return null;
            const hit=collector.mHit,id=hit.mBodyID.GetIndexAndSequenceNumber()|0,f=hit.mFraction;
            this.position.Set(x+dx*f,y+dy*f,z+dz*f);
            const n=this.body(id).GetWorldSpaceSurfaceNormal(hit.mSubShapeID2,this.position);
            return {id,f,x:x+dx*f,y:y+dy*f,z:z+dz*f,nx:n.GetX(),ny:n.GetY(),nz:n.GetZ()};
        }finally{for(let i=owned.length-1;i>=0;i--)J.destroy(owned[i]);}
    }
    joint(a,b,ax,ay,az,bx,by,bz,min,max){
        const J=this.J,s=new J.DistanceConstraintSettings();
        try{s.mSpace=J.EConstraintSpace_WorldSpace;s.mPoint1.Set(ax,ay,az);s.mPoint2.Set(bx,by,bz);s.mMinDistance=min;s.mMaxDistance=max;
            const joint=this.bodies.CreateConstraint(s,this.body(a).GetID(),this.body(b).GetID());joint.AddRef();
            this.system.AddConstraint(joint);this.joints.add(joint);return joint;
        }finally{J.destroy(s);}
    }
    destroyJoint(joint){if(!this.joints.delete(joint))return;this.system.RemoveConstraint(joint);joint.Release();}
    close(){
        if(this.closed)return;
        this.system.SetContactListener(0);
        for(const joint of this.joints)this.destroyJoint(joint);
        for(const id of this.handles.keys())this.destroy(id);
        for(const value of [this.listener,this.vector,this.position,this.rotation,this.physics])this.J.destroy(value);
        this.closed=true;this.onClose?.();
    }
}
