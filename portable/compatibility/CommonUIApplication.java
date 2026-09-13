package compatibility;
import valthorne.*;
import valthorne.graphics.Color;
import valthorne.ui.*;
import valthorne.ui.nodes.*;
import valthorne.ui.nodes.nano.*;
import valthorne.ui.theme.ProfessionalTheme;
public final class CommonUIApplication implements Application {
 private UIRoot root;private ProfessionalTheme theme;private NanoTextField field;private NanoSlider slider;private int frames,checks,clicks;
 private final double[] samples=new double[80];
 public static void main(String[] args){try{JGL.init(new CommonUIApplication(),JGLConfiguration.defaults().title("Shared engine UI").size(640,480).visible(false));System.out.println("COMMON_UI_RETURNED");}catch(Throwable e){e.printStackTrace();throw e;}}
 private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
 private void place(UINode node,float x,float y,float w,float h){node.getLayout().absolute().left(x).top(y).width(w).height(h);root.add(node);}
 public void init(){
  Window.setSwapInterval(SwapInterval.OFF);
  root=new UIRoot();theme=new ProfessionalTheme();root.setTheme(theme.create());
  var title=new NanoLabel("One game. Desktop and web.");place(title,24,20,500,40);
  var button=new NanoButton("Vector button").action(n->{clicks++;System.out.println("UI_VECTOR_CLICK");});place(button,24,80,240,48);
  var textureButton=new Button("Texture button").action(n->{clicks++;System.out.println("UI_TEXTURE_CLICK");});place(textureButton,300,80,250,48);
  field=new NanoTextField("Type here");place(field,24,156,526,48);
  slider=new NanoSlider().value(.25f);place(slider,24,240,526,40);
  var checkbox=new NanoCheckbox();place(checkbox,24,315,32,32);
  var label=new NanoLabel("Vector and texture controls share layout and input");place(label,24,386,580,40);
  root.layout();check(Math.abs(button.getWidth()-240)<1,"Yoga width");check(Math.abs(button.getHeight()-48)<1,"Yoga height");check(root.getNanoVGHandle()!=0,"Vector context");
  var editor=new valthorne.ui.behavior.TextEditModel();editor.text("A\ud83d\udc69\u200d\ud83d\udcbb e\u0301");check(editor.next(1)==6,"Emoji grapheme boundary");check(editor.previous(9)==7,"Combining mark boundary");
  System.out.println("COMMON_UI_READY");
 }
 public void update(float dt){root.update(dt);if(++frames>=480)Window.requestClose();}
 public void render(){Window.clear(new Color(0xFF111827));long start=System.nanoTime();if(frames>=130&&frames<210)root.layout();root.draw();int index=frames>=130?frames-130:frames-30;if(index>=0&&index<80)samples[index]=(System.nanoTime()-start)/1e6;if(frames==109||frames==209){double sum=0;for(double sample:samples)sum+=sample;java.util.Arrays.sort(samples);System.out.println("UI_BENCHMARK forcedLayout="+(frames==209)+" meanMs="+sum/80+" p95Ms="+samples[75]);}}
 public void dispose(){if(root!=null)root.dispose();if(theme!=null)theme.close();System.out.println("COMMON_UI_VALIDATED checks="+checks+" clicks="+clicks+" text="+(field==null?"":field.getText())+" slider="+(slider==null?0:slider.getValue()));}
}
