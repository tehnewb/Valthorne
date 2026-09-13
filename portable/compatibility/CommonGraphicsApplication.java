package compatibility;
import java.nio.ByteBuffer;
import java.util.Base64;
import valthorne.*;
import valthorne.graphics.*;
import valthorne.graphics.texture.*;
import valthorne.graphics.shader.*;

/** Exact same image, sprite, shader and framebuffer application for both targets. */
public final class CommonGraphicsApplication implements Application {
 private Texture texture,white;private TextureBatch batch;private FrameBuffer target;private Shader custom;private NinePatchTexture patch;
 private int frames,checks;private long start;
 private final long[][] samples=new long[3][60];private final int[] sampleCounts=new int[3];
 private static final byte[] PNG=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAE0lEQVR4nGP4z8DwHwyBNAg0AABJSQl4KKDbdwAAAABJRU5ErkJggg==");
 public static void main(String[] args){try{JGL.init(new CommonGraphicsApplication(),JGLConfiguration.defaults().title("Shared graphics compatibility").size(640,480).swapInterval(SwapInterval.OFF).visible(false));System.out.println("COMMON_GRAPHICS_RETURNED");}catch(Throwable error){error.printStackTrace();throw error;}}
 private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
 public void init(){
  TextureData normal=TextureData.load(PNG,false),flipped=TextureData.load(PNG,true),path=TextureData.load("portable/compatibility/assets/colors.png",false);
  check(normal.width()==2&&normal.height()==2,"Image dimensions");
  check((normal.buffer().get(0)&255)==255&&(normal.buffer().get(2)&255)==0,"Top red pixel");
  check((flipped.buffer().get(0)&255)==0&&(flipped.buffer().get(2)&255)==255,"Vertical flip");
  check((normal.buffer().get(15)&255)==128,"Alpha channel");
  check(normal.buffer().equals(path.buffer()),"Path and byte decoders agree");
  boolean rejected=false;try{TextureData.load(new byte[]{1,2,3});}catch(RuntimeException expected){rejected=true;}check(rejected,"Invalid images rejected");
  texture=new Texture(PNG);check(texture.getWidth()==2&&texture.getHeight()==2,"Texture dimensions");
  for(TextureFilter filter:TextureFilter.values()){texture.setFilter(filter);check(texture.getFilter()==filter,"Filter selection");}texture.reset();
  ByteBuffer pixels=ByteBuffer.allocateDirect(4);pixels.putInt(-1).flip();white=new Texture(new TextureData(pixels,1,1));
  patch=flipped.asNinePatchTexture(1,1,1,1);normal.dispose();path.dispose();
  batch=new TextureBatch(64,4);target=new FrameBuffer(640,480,true);
  custom=new Shader(TextureBatchContract.defaultVertexShader(),TextureBatchContract.buildDefaultFragmentShader(4).replace("void main()", "void main()"));
  TextureBatchContract.bindAttributes(custom);check(custom.reload(),"Shader reload");int program=custom.getProgramID();for(int i=0;i<3;i++)check(!custom.reload(custom.getVertexSource(),"invalid shader"),"Reject invalid reload");check(custom.getProgramID()==program,"Failed reload preserves working program");TextureBatchContract.bindSamplers(custom,4);
  float[] projection=new float[16];Window.copyProjectionMatrix(projection);Window.setProjectionMatrix(projection);check(projection[15]==1,"Projection");start=System.nanoTime();
 }
 public void update(float dt){frames++;if(frames==10){int before=frames;TextureData loaded=TextureData.load(PNG);check(frames==before,"Frames do not overlap asynchronous image decoding");loaded.dispose();}if(frames>=240||Keyboard.isKeyDown(Keyboard.ESCAPE))Window.requestClose();}
 public void render(){
  long before=System.nanoTime();
  Window.clear(Color.BLACK);
  batch.begin();batch.draw(texture,20,20,160,160);
  batch.beginScissor(220,40,60,80);batch.draw(white,200,20,120,120,Color.RED);batch.endScissor();
  batch.pushTranslation(340,20);batch.draw(white,0,0,100,100,new Color(0,1,0,.5f));batch.popTranslation();
  batch.draw(texture,460,20,120,120,60,60,45,Color.WHITE);
  batch.draw(white,-1000,-1000,10,10);batch.end();
  target.begin();Window.clear(Color.BLUE);batch.begin();batch.draw(white,0,0,320,240,Color.RED);batch.end();target.end();
  target.draw(20,240,160,120);
  batch.setShader(custom);batch.begin();batch.draw(texture,220,240,120,120);batch.end();batch.setShader(null);
  batch.begin();batch.draw(patch,380,240,100,100);batch.end();
  int scenario=Math.min(2,(frames-1)/80);
  if(scenario>0){batch.begin();for(int i=0;i<2000;i++)batch.draw(white,scenario==1?(i%100)*6:-1000,(i/100)*6,4,4);batch.end();}
  if((frames-1)%80>=20&&sampleCounts[scenario]<60)samples[scenario][sampleCounts[scenario]++]=System.nanoTime()-before;
  if(frames==2){check(batch.getTotalCulledSprites()>=2,"Offscreen sprite culling");System.out.println("COMMON_GRAPHICS_READY");}
 }
 public void dispose(){
  String[] scenarios={"graphics","2000-visible-sprites","2000-culled-sprites"};
  for(int i=0;i<3;i++)if(sampleCounts[i]>0){java.util.Arrays.sort(samples[i],0,sampleCounts[i]);long total=0;for(int j=0;j<sampleCounts[i];j++)total+=samples[i][j];System.out.println("GRAPHICS_BENCHMARK scenario="+scenarios[i]+" samples="+sampleCounts[i]+" meanSubmitMs="+(total/1e6/sampleCounts[i])+" p95SubmitMs="+(samples[i][Math.min(sampleCounts[i]-1,(int)(sampleCounts[i]*.95))]/1e6));}
  if(batch!=null){check(batch.getTotalDrawCalls()>0,"Draw calls recorded");System.out.println("COMMON_GRAPHICS_VALIDATED checks="+checks+" draws="+batch.getTotalDrawCalls()+" culled="+batch.getTotalCulledSprites()+" meanMs="+((System.nanoTime()-start)/1e6/frames));batch.dispose();}
  if(custom!=null)custom.dispose();if(target!=null)target.dispose();if(texture!=null){texture.dispose();texture.dispose();}if(patch!=null)patch.dispose();if(white!=null)white.dispose();ImmediateTextureRenderer.dispose();
 }
}
