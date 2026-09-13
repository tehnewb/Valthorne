package compatibility;
import valthorne.*;
import valthorne.camera.PerspectiveCamera;
import valthorne.graphics.Color;
import valthorne.graphics.model.*;
public final class CommonModelApplication implements Application {
 private ObjModel3D model;private SceneRenderer3D renderer;private final Scene3D scene=new Scene3D();private final PerspectiveCamera camera=new PerspectiveCamera();private int frames,checks;
 public static void main(String[] args){try{JGL.init(new CommonModelApplication(),JGLConfiguration.defaults().title("Shared model import").contextVersion(4,3).size(640,480).visible(false));System.out.println("COMMON_MODEL_RETURNED");}catch(Throwable e){e.printStackTrace();throw e;}}
 private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
 public void init(){
  model=new ModelLoader().load(ModelParameters.fromPath("portable/compatibility/assets/cube.obj","cube"));
  check(model.getTriangleCount()==12,"Quad triangulation");check(model.getParts().size()==2,"Material grouping");check(model.getParts().get(0).material().getTexture()==null,"Deferred texture upload");
  model.uploadTextures();var texture=model.getParts().get(0).material().getTexture();check(texture!=null,"MTL texture loaded");model.uploadTextures();check(texture==model.getParts().get(0).material().getTexture(),"Cached texture upload");
  check(Math.abs(model.getLocalBounds().maxZ-2)<.001f,"Original coordinates preserved");
  renderer=new SceneRenderer3D();renderer.filament().setEnvironmentIntensity(500);camera.setPosition(5,-9,6);camera.lookAt(0,0,1,0,0,1);camera.setClipPlanes(.1f,100);
  scene.add(new ModelInstance3D().setModel(model).setPosition(-1.8f,0,0));scene.add(new ModelInstance3D().setModel(model).setPosition(1.8f,0,0).setYawRadians(.5f).setMaterial(new Material3D().setTint(new Color(.65f,1,.65f,1))));
  scene.add(new ModelInstance3D().setModel(ModelBuilder3D.box(15,15,.5f)).setPosition(0,0,-.25f));scene.addLight(new PointLight3D().setPosition(0,-3,6).setIntensity(500).setRange(25).setCastsShadows(true));
  System.out.println("COMMON_MODEL_READY");
 }
 public void update(float dt){if(++frames>=180)Window.requestClose();}
 public void render(){renderer.render(scene,camera);if(frames==2)check(renderer.filament().getCachedMeshCount()==3,"OBJ parts shared between instances");}
 public void dispose(){if(renderer!=null)renderer.close();if(model!=null){model.dispose();model.dispose();}System.out.println("COMMON_MODEL_VALIDATED checks="+checks);}
}
