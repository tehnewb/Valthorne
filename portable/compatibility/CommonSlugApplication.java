package compatibility;
import valthorne.*;
import valthorne.graphics.Color;
import valthorne.graphics.font.slug.*;
import valthorne.io.file.ValthorneFiles;
import org.joml.Matrix4f;
import valthorne.ui.UIRoot;
import valthorne.ui.nodes.SlugLabel;
public final class CommonSlugApplication implements Application {
 private SlugFont font;private SlugBatch batch;private SlugTextRun run;private UIRoot root;private int frames,checks;
 private final long[][] samples=new long[2][80];private final int[] counts=new int[2];private final Matrix4f projection=new Matrix4f().setOrtho2D(0,640,0,480);
 private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
 public static void main(String[] args){try{JGL.init(new CommonSlugApplication(),JGLConfiguration.defaults().title("Shared curve text").size(640,480).visible(false));System.out.println("COMMON_SLUG_RETURNED");}catch(Throwable error){error.printStackTrace();throw error;}}
 public void init(){Window.setSwapInterval(SwapInterval.OFF);font=SlugFont.load(ValthorneFiles.readBytes("ui/AtkinsonHyperlegible-Regular.ttf"),32,96);batch=new SlugBatch(128);run=font.createRun("Curve-rendered text",42);
  check(font.getWidth("Hello",40)>50,"Font measurement");check(font.getHeight("Hello\nworld",40)>font.getHeight("Hello",40),"Multiline layout");check(font.getWidth(" ",40)>0,"Space advance");
  root=new UIRoot();var label=new SlugLabel(font,batch,"Original SlugLabel UI",22).color(Color.GREEN);label.getLayout().absolute().left(30).top(385);root.add(label);root.layout();check(label.getWidth()>100,"Slug UI layout");System.out.println("COMMON_SLUG_READY width="+font.getWidth("Hello",40));}
 public void update(float dt){root.update(dt);if(++frames>=200)Window.requestClose();}
 public void render(){long start=System.nanoTime();int phase=Math.min(1,(frames-1)/100);Window.clear(Color.NAVY);batch.begin(projection,640,480);if(phase==0)font.draw(batch,"Curve-rendered text",30,320,42,Color.WHITE);else run.draw(batch,30,320,Color.WHITE);font.draw(batch,"AVATAR 0123456789",30,230,32,Color.YELLOW);font.draw(batch,"Small & crisp",30,150,16,Color.WHITE);batch.end();if(frames==2)check(batch.getGlyphsSubmitted()>20&&batch.getDrawCalls()>0,"Glyph batching");if((frames-1)%100>=20&&counts[phase]<80)samples[phase][counts[phase]++]=System.nanoTime()-start;root.draw();}
 public void dispose(){if(root!=null)root.dispose();if(batch!=null)batch.dispose();if(font!=null)font.dispose();for(int phase=0;phase<2;phase++)if(counts[phase]>0){java.util.Arrays.sort(samples[phase]);long total=0;for(long sample:samples[phase])total+=sample;System.out.println("SLUG_BENCHMARK retained="+(phase==1)+" samples="+counts[phase]+" meanSubmitMs="+(total/1e6/counts[phase])+" p95SubmitMs="+(samples[phase][76]/1e6));}System.out.println("COMMON_SLUG_VALIDATED checks="+checks);}
}
