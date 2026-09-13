package compatibility;
import valthorne.*;
import valthorne.graphics.Color;
import valthorne.graphics.model.*;
import valthorne.graphics.texture.*;
import valthorne.camera.PerspectiveCamera;
public final class CommonRasterApplication implements Application {
 private SceneRenderer3D renderer;private ShadowMap3D shadow;private Texture texture;
 private final Scene3D scene=new Scene3D();private final PerspectiveCamera camera=new PerspectiveCamera();private int frames,checks,count;private final long[] samples=new long[80];
 private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
 public static void main(String[] args){try{JGL.init(new CommonRasterApplication(),JGLConfiguration.defaults().title("Shared raster rendering").size(640,480).visible(false));System.out.println("COMMON_RASTER_RETURNED");}catch(Throwable error){error.printStackTrace();throw error;}}
 public void init(){Window.setSwapInterval(SwapInterval.OFF);check(Window.getGraphicsCapabilities().raster(),"Raster capability");renderer=new SceneRenderer3D(SceneRenderer3D.Backend.RASTER);check(renderer.getBackend()==SceneRenderer3D.Backend.RASTER,"Explicit raster backend");
  camera.setPosition(8,-12,9);camera.lookAt(0,0,0,0,0,1);camera.setClipPlanes(.1f,100);camera.rebuild(640,480);
  scene.add(new ModelInstance3D().setModel(ModelBuilder3D.box(16,16,.4f)).setPosition(0,0,-.2f));
  scene.add(new ModelInstance3D().setModel(ModelBuilder3D.box(2,2,2)).setPosition(-2,0,1).setMaterial(new Material3D().setTint(Color.RED)));
  scene.add(new ModelInstance3D().setModel(ModelBuilder3D.box(2,2,3)).setPosition(2,1,1.5f).setMaterial(new Material3D().setTint(Color.BLUE)));
  texture=new Texture("portable/compatibility/assets/colors.png");scene.add(new BillboardSprite3D().setPosition(0,-3,2).setSize(2,2).setTextureRegion(new TextureRegion(texture)));
  scene.addLight(new PointLight3D().setPosition(0,-4,6).setIntensity(8).setRange(25));
  shadow=new ShadowMap3D(256);shadow.getCamera().setPosition(0,-8,10);shadow.getCamera().lookAt(0,0,0,0,0,1);shadow.getCamera().setWorldHeight(20);shadow.getCamera().setClipPlanes(.1f,50);
  check(shadow.renderIfChanged(scene,1),"Initial shadow pass");check(!shadow.renderIfChanged(scene,1)&&shadow.getRenderCount()==1,"Shadow cache");
  renderer.rasterState().setFog(30,80,0).setShadowMap(shadow).setLightDirection(0,-.6f,.8f).setDirectionalLight(Color.WHITE);
  renderer.rasterState().getLighting().setEnvironment(new Color(.025f,.025f,.025f,1),new Color(.01f,.01f,.01f,1));
  System.out.println("COMMON_RASTER_READY");}
 public void update(float dt){if(++frames==120){shadow.setStrength(0);System.out.println("RASTER_SHADOW_DISABLED");}if(frames>=180)Window.requestClose();}
 public void render(){long start=System.nanoTime();Window.clear3D(Color.NAVY);renderer.render(scene,camera);if(frames==2)check(renderer.rasterState().getLighting().getUploadCount()>0,"Tiled lighting upload");if(frames>20&&count<80)samples[count++]=System.nanoTime()-start;}
 public void dispose(){if(renderer!=null)renderer.close();if(shadow!=null)shadow.close();if(texture!=null)texture.dispose();if(count>0){java.util.Arrays.sort(samples);long total=0;for(long value:samples)total+=value;System.out.println("RASTER_BENCHMARK samples="+count+" meanSubmitMs="+(total/1e6/count)+" p95SubmitMs="+(samples[76]/1e6));}System.out.println("COMMON_RASTER_VALIDATED checks="+checks);}
}
