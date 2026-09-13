package valthorne.portable;

import valthorne.Application;

/** Portable first-person integration scene. All gameplay runs on the fixed Java update loop. */
public final class FirstPersonDemo implements Application {
    private final InteractiveScene scene;
    private final PlatformServices platform;
    private final AssetService assets;
    private final MediaService media;
    private MediaService.Image crosshair;
    private MediaService.Sound shotSound;
    private PhysicsBody player;
    private final PhysicsBody[] targets=new PhysicsBody[8];
    private float yaw,pitch,cooldown,reload,elapsed;
    private int ammo=24,hits,shots,lightColor=0x65d9ff;
    private String status="Click Enter arena, then WASD / mouse / Space / R";
    public FirstPersonDemo(InteractiveScene scene,PlatformServices platform,AssetService assets,MediaService media){this.scene=scene;this.platform=platform;this.assets=assets;this.media=media;}
    @Override public void init(){
        reset();
        media.loadImage("ui/crosshair.png",new MediaService.Listener<>(){public void loaded(MediaService.Image image){crosshair=image;}public void failed(String reason){status="Crosshair: "+reason;}});
        media.loadSound("audio/impact.wav",new MediaService.Listener<>(){public void loaded(MediaService.Sound sound){shotSound=sound;}public void failed(String reason){status="Sound: "+reason;}});
    }
    public void reset(){
        scene.clear();scene.box(0,-.5f,0,12,.5f,12,0x526477,false);
        scene.box(-12,2,0,.5f,2,12,0x293949,false);scene.box(12,2,0,.5f,2,12,0x293949,false);
        scene.box(0,2,-12,12,2,.5f,0x293949,false);scene.box(0,2,12,12,2,.5f,0x293949,false);
        player=scene.rigidBox(0,1,8,.34f,.9f,.34f,0x98aabb,true,true);
        for(int i=0;i<targets.length;i++)targets[i]=scene.rigidBox((i%4-1.5f)*3,1.2f,-2-(i/4)*4,.55f,1,.55f,0xeab47b,true,false);
        assets.loadGlb("models/tree.glb",new AssetService.Listener(){
            public void loaded(ModelAsset model){model.transform(-8,0,-8,0,2);}
            public void failed(String reason){if(!reason.equals("cancelled"))status="Tree load: "+reason;}
        });
        yaw=pitch=cooldown=reload=elapsed=0;ammo=24;hits=shots=0;
    }
    private boolean key(String key){return platform.keyDown(key);}
    @Override public void update(float dt){
        elapsed+=dt;cooldown=Math.max(0,cooldown-dt);
        yaw+=platform.takeLookX()*.0022f;pitch=Math.max(-1.45f,Math.min(1.45f,pitch-platform.takeLookY()*.0022f));
        float forward=(key("KeyW")?1:0)-(key("KeyS")?1:0),strafe=(key("KeyD")?1:0)-(key("KeyA")?1:0);
        float length=(float)Math.sqrt(forward*forward+strafe*strafe),speed=key("ShiftLeft")?7.2f:4.4f;
        if(length>0){forward*=speed/length;strafe*=speed/length;}
        float vy=player.velocityY();
        if(platform.takeKeyPress("Space")&&scene.rayDistance(player.x(),player.y(),player.z(),0,-1,0,1.02f,player)>=0)vy=5;
        player.velocity((float)Math.sin(yaw)*forward+(float)Math.cos(yaw)*strafe,vy,-(float)Math.cos(yaw)*forward+(float)Math.sin(yaw)*strafe);
        if(player.y()<-10)player.position(0,2,8);
        if(platform.takeKeyPress("KeyR")&&ammo<24&&reload==0)reload=1.5f;
        if(reload>0){reload=Math.max(0,reload-dt);if(reload==0){ammo=24;platform.tone(600,.08f,.1f,0);}}
        if(platform.buttonDown(0)&&cooldown==0&&reload==0&&ammo>0)shoot();
        scene.step(dt);
    }
    private void shoot(){
        cooldown=.12f;ammo--;shots++;
        float cp=(float)Math.cos(pitch),dx=(float)Math.sin(yaw)*cp,dy=(float)Math.sin(pitch),dz=-(float)Math.cos(yaw)*cp;
        float x=player.x(),y=player.y()+.65f,z=player.z();
        var hit=scene.castRay(x,y,z,dx,dy,dz,65,player);
        if(shotSound!=null)shotSound.play(.2f,0);else platform.tone(95,.08f,.16f,0);
        if(hit==null)return;
        float distance=hit.distance();
        float hx=x+dx*distance,hy=y+dy*distance,hz=z+dz*distance;
        scene.particles(hx-dx*.08f,hy-dy*.08f,hz-dz*.08f,lightColor,10,true,true);
        for(PhysicsBody target:targets){
            if(target.equals(hit.body())){target.impulse(dx*30,dy*30+3,dz*30);hits++;break;}
        }
        status="Jolt impact / illuminated particles";
    }
    @Override public void render(){
        scene.camera(player.x(),player.y()+.65f,player.z(),yaw,pitch,72);
        scene.light(0,5,-3,lightColor,90000);scene.render();
        platform.beginOverlay();
        if(crosshair!=null)crosshair.draw(platform.viewportWidth()/2f-12,platform.viewportHeight()/2f-12,24,24,1);
        platform.rectangle(24,100,330,116,0x101924,.85f);
        platform.text("AMMO "+ammo+" / 24"+(reload>0?"   RELOADING":""),40,129,20,0xffffff);
        platform.text("Hits "+hits+"  Shots "+shots,40,156,16,0x8edbe9);
        platform.text(status,40,187,12,0xffffff);
    }
    public int seconds(){return (int)elapsed;}
    public void setLightColor(int rgb){lightColor=rgb;}
    public int hits(){return hits;}public int shots(){return shots;}public int ammo(){return ammo;}
    @Override public void dispose(){if(crosshair!=null)crosshair.close();if(shotSound!=null)shotSound.close();scene.close();}
}
