package compatibility;
import valthorne.*;
import valthorne.graphics.Color;
import valthorne.graphics.texture.*;
import valthorne.graphics.radiance.*;
import java.nio.ByteBuffer;
public final class CommonRadianceApplication implements Application {
 private RadianceSceneBuffer scene;private RadianceCascades lighting;private RadianceCascadeSettings settings;private Texture white;private TextureBatch batch;
 private int frames,checks,count;private long[] samples=new long[80];
 private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
 public static void main(String[] args){try{JGL.init(new CommonRadianceApplication(),JGLConfiguration.defaults().title("Shared radiance cascades").size(640,480).contextVersion(4,3).visible(false));System.out.println("COMMON_RADIANCE_RETURNED");}catch(Throwable error){error.printStackTrace();throw error;}}
 public void init(){
  Window.setSwapInterval(SwapInterval.OFF);scene=new RadianceSceneBuffer(128,96);settings=new RadianceCascadeSettings().setInternalScale(1).setBaseProbeSpacing(2).setBaseRayCount(8).setBilinearFix(true).setMaxLevels(6).setIntensity(2);
  lighting=new RadianceCascades(128,96,settings);white=new Texture(new TextureData(ByteBuffer.wrap(new byte[]{-1,-1,-1,-1}),1,1));batch=new TextureBatch(32);
  check(scene.getWidth()==128&&scene.getHeight()==96,"Scene dimensions");check(lighting.getLevels().size()>1,"Cascade hierarchy");check(lighting.getLightTexture().getWidth()==128,"Output dimensions");
  Texture old=lighting.getLightTexture();lighting.resize(64,48);scene.resize(64,48);check(lighting.getLightTexture().getWidth()==64&&scene.getWidth()==64,"Resize dimensions");lighting.resize(128,96);scene.resize(128,96);check(lighting.getLightTexture()!=old,"Resize replaces output");
  Window.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0,128,0,96,-1,1).get(new float[16]));System.out.println("COMMON_RADIANCE_READY");
 }
 public void update(float dt){frames++;if(frames==121)System.out.println("RADIANCE_OCCLUDER_DISABLED");if(frames==241){settings.setIntensity(0);System.out.println("RADIANCE_INTENSITY_ZERO");}if(frames>=360)Window.requestClose();}
 public void render(){
  long start=System.nanoTime();scene.clear();scene.begin();try{batch.begin();batch.setColor(Color.RED);batch.draw(white,16,40,12,16);batch.setColor(Color.BLUE);batch.draw(white,100,40,12,16);if(frames<=120){batch.setColor(Color.BLACK);batch.draw(white,60,12,8,72);}batch.setColor(Color.WHITE);batch.end();}finally{scene.end();}
  lighting.render(scene);Window.clear(Color.BLACK);batch.begin();batch.draw(lighting.getLightTexture(),0,0,128,96);batch.end();
  if(frames>20&&count<80)samples[count++]=System.nanoTime()-start;
 }
 public void dispose(){if(lighting!=null)lighting.dispose();if(scene!=null)scene.dispose();if(batch!=null)batch.dispose();if(white!=null)white.dispose();if(count>0){java.util.Arrays.sort(samples);long total=0;for(long v:samples)total+=v;System.out.println("RADIANCE_BENCHMARK samples="+count+" meanSubmitMs="+(total/1e6/count)+" p95SubmitMs="+(samples[76]/1e6));}System.out.println("COMMON_RADIANCE_VALIDATED checks="+checks);}
}
