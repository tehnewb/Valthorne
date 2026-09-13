package compatibility;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.primitives.Rayf;
import valthorne.*;
import valthorne.graphics.Color;
import valthorne.graphics.model.*;
import valthorne.math.physics.*;
import valthorne.graphics.particle.ParticleEmitter3D;

/** Identical source exercises both native Jolt and browser Jolt through the engine API. */
public final class CommonPhysicsApplication implements Application {
    private static int checks;
    public static void main(String[] args){try{JGL.init(new CommonPhysicsApplication(),JGLConfiguration.defaults().title("Common physics validation").size(640,480).visible(false));System.out.println("COMMON_PHYSICS_VALIDATED checks="+checks);}catch(Throwable failure){failure.printStackTrace();throw failure;}}
    private static void require(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static void near(float value,float expected,float tolerance,String message){require(Math.abs(value-expected)<=tolerance,message+": "+value);}
    public void init(){
        PhysicsWorld3D.Settings settings=new PhysicsWorld3D.Settings(1f/60,8,64,2048,2048,0);
        try(PhysicsWorld3D world=new PhysicsWorld3D(settings);PhysicsWorld3D other=new PhysicsWorld3D(settings)){
            near(world.getGravity().z,-9.81f,.001f,"Z-up gravity");
            var ground=world.createBody(new BodySettings3D(CollisionShape3D.box(30,30,1),MotionType3D.STATIC).setPosition(0,0,-.5f));
            var box=world.createBody(new BodySettings3D(CollisionShape3D.box(1,1,1),MotionType3D.DYNAMIC).setPosition(0,0,4).setMass(2).setRotationLocked(true));
            ModelInstance3D model=new ModelInstance3D().setScale(2);
            box.bind(model).setUserData("box");require(box.getUserData().equals("box"),"User data");
            int[] contact={0,0,0},before={0},after={0};
            world.addContactListener(e->{contact[e.type().ordinal()]++;require(e.bodyA()!=null&&e.bodyB()!=null,"Contact bodies");});
            world.addBeforeStepListener(w->before[0]++);world.addAfterStepListener(w->after[0]++);
            world.optimizeBroadPhase();
            for(int i=0;i<180;i++)world.step();
            near(box.getPosition().z,.5f,.06f,"Resting on floor");near(model.getPosition().z,box.getPosition().z,1e-5f,"Bound pose");near(model.getScale().x,2,0,"Binding preserves scale");
            require(contact[0]>0,"Added contact");require(contact[1]>0,"Persisted contact");require(before[0]==180&&after[0]==180,"Step callbacks");
            require(other.getStepCount()==0&&other.getBodyCount()==0,"Independent worlds");
            var ray=new Rayf(0,0,5,0,0,-2);var hit=world.raycast(ray,10);
            require(hit!=null&&hit.body()==box,"Ray closest body");near(hit.distance(),4,.1f,"Normalized ray distance");near(hit.normal().z,1,.01f,"Ray normal");
            require(world.raycast(ray,10,box).body()==ground,"Ignored ray body");
            box.setTransform(new Vector3f(0,0,5),new Quaternionf(0,0,0,2));near(box.getRotation().w,1,.0001f,"Normalized rotation");
            box.setLinearVelocity(0,0,0).addImpulse(new Vector3f(2,0,0));near(box.getLinearVelocity().x,1,.01f,"Mass impulse");
            box.addImpulse(new Vector3f(0,1,0),new Vector3f(0,0,5));box.addForce(new Vector3f(2,0,0));box.addTorque(new Vector3f(0,0,1));box.addAngularImpulse(new Vector3f(0,0,1));box.setAngularVelocity(new Vector3f());
            require(box.getAngularVelocity(new Vector3f()).isFinite(),"Angular velocity");box.sleep();require(!box.isActive(),"Sleep");box.activate();require(box.isActive(),"Activation");box.unbind();
            var kinematic=world.createBody(new BodySettings3D(CollisionShape3D.box(1,1,1),MotionType3D.KINEMATIC).setPosition(6,0,2));
            kinematic.moveKinematic(new Vector3f(7,0,2),new Quaternionf());world.step();near(kinematic.getPosition().x,7,.01f,"Kinematic move");
            var joint=world.createDistanceJoint(ground,box,new Vector3f(0,0,8),box.getPosition(),3,3);require(!joint.isDestroyed(),"Joint live");joint.close();require(joint.isDestroyed(),"Joint closed");
            var autoJoint=world.createDistanceJoint(ground,box,new Vector3f(0,0,8),box.getPosition(),0,10);box.close();require(autoJoint.isDestroyed()&&box.isDestroyed(),"Body closes joints");box.close();
            CollisionShape3D[] shapes={CollisionShape3D.sphere(.5f),CollisionShape3D.capsule(.3f,2),CollisionShape3D.cylinder(.4f,2),CollisionShape3D.convexHull(ModelBuilder3D.box(1,1,1))};
            for(int i=0;i<shapes.length;i++)world.createBody(new BodySettings3D(shapes[i],MotionType3D.DYNAMIC).setPosition(-6+i*3,0,3));
            var mesh=world.createBody(new BodySettings3D(CollisionShape3D.mesh(ModelBuilder3D.box(2,2,1)),MotionType3D.STATIC).setPosition(10,0,1));
            require(world.raycast(new Rayf(10,0,5,0,0,-1),10).body()==mesh,"Triangle mesh ray");
            for(int i=0;i<180;i++)world.step();
            near(world.raycast(new Rayf(-3,0,5,0,0,-1),10).position().z,2,.12f,"Capsule Z axis");
            long previous=world.getStepCount();require(world.update(1)==8,"Catch-up limit");require(world.getDroppedTime()>.8,"Dropped time");require(world.getStepCount()==previous+8,"Step count");world.syncModels(true);
            world.setGravity(new Vector3f(0,0,0));near(world.getGravity().length(),0,0,"Gravity setter");
            require(world.getBodies().size()==world.getBodyCount(),"Body snapshot");
        }
        CollisionLayers3D layers=new CollisionLayers3D().setCollision(0,1,false);
        try(PhysicsWorld3D world=new PhysicsWorld3D(settings,layers)){
            world.createBody(new BodySettings3D(CollisionShape3D.box(10,10,1),MotionType3D.STATIC));
            var body=world.createBody(new BodySettings3D(CollisionShape3D.sphere(.5f),MotionType3D.DYNAMIC).setLayer(1).setPosition(0,0,2));
            for(int i=0;i<90;i++)world.step();require(body.getPosition().z<0,"Layer filtering");
        }
        try(PhysicsWorld3D world=new PhysicsWorld3D(settings)){
            int[] births={0};Scene3D scene=new Scene3D();Model3D model=ModelBuilder3D.box(.1f,.1f,.1f);
            try(ParticleEmitter3D emitter=new ParticleEmitter3D(8,p->{p.setModel(model).setLifetime(.25f).setLightEnabled(true).setLightOffset(0,0,.2f);p.getPosition().set(births[0]++,0,3);p.getLight().setIntensity(2);})){
                emitter.attach(scene).setPhysics(world,p->new BodySettings3D(CollisionShape3D.sphere(.05f),MotionType3D.DYNAMIC));
                require(emitter.burst(3)==3&&world.getBodyCount()==3&&scene.getLights().size()==3,"Physical particle creation");
                world.step();var particle=emitter.getParticles().get(0);
                require(particle.getPosition().z<3,"Particle gravity");near(particle.getLight().getPosition().z,particle.getPosition().z+.2f,.0001f,"Particle light follows body");
                for(int i=0;i<20;i++)world.step();require(emitter.getParticleCount()==0&&world.getBodyCount()==0&&scene.size()==0&&scene.getLights().isEmpty(),"Particle expiry releases all resources");
            }
            int[] sensors={0};world.addContactListener(e->{if(e.bodyA().isSensor()||e.bodyB().isSensor())sensors[0]++;});
            world.createBody(new BodySettings3D(CollisionShape3D.box(10,10,1),MotionType3D.STATIC).setSensor(true));
            var body=world.createBody(new BodySettings3D(CollisionShape3D.sphere(.25f),MotionType3D.DYNAMIC).setPosition(0,0,2));
            for(int i=0;i<80;i++)world.step();require(sensors[0]>0&&body.getPosition().z<0,"Sensors notify without blocking");
        }
    }
    public void update(float dt){Window.requestClose();}
    public void render(){Window.clear(Color.NAVY);}
    public void dispose(){}
}
