package valthorne;

import org.teavm.jso.*;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import valthorne.event.*;
import java.util.ArrayDeque;
import java.util.Objects;

/** Browser runtime backend for the existing JGL API, selected by the web build. */
public class JGL {
    private static final EventPublisher events=new EventPublisher();
    private static final ArrayDeque<Runnable> tasks=new ArrayDeque<>();
    private static Application application;
    private static boolean running,closing;
    private static float delta,elapsed,fpsTime;
    private static double startTime;
    private static AsyncCallback<Double> pendingFrame;
    private static int frames;
    private static short fps;
    @JSFunctor private interface Frame extends JSObject {void accept(double dt);}
    @JSFunctor private interface Shutdown extends JSObject {void accept();}
    public static void init(Application app,String title,int width,int height){init(app,JGLConfiguration.defaults().title(title).size(width,height));}
    public static void init(Application app,JGLConfiguration config){
        Objects.requireNonNull(app);Objects.requireNonNull(config);
        if(running)throw new IllegalStateException("JGL is already running");
        running=true;closing=false;application=app;startTime=clock();
        try{Window.configure(config);Keyboard.init();Mouse.init();app.init();connect(JGL::nextFrame,JGL::requestClose);while(running)frame(awaitFrame());}catch(RuntimeException|Error failure){try{shutdown();}catch(Throwable cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    private static void frame(double dt){
        if(!running)return;
        try{
            Runnable task;while((task=tasks.poll())!=null)task.run();
            if(closing){shutdown();return;}
            delta=(float)dt;elapsed+=delta;
            application.update(delta);application.render();
            Audio.update();
            Mouse.resetScroll();
            fpsTime+=delta;frames++;if(fpsTime>=1){fps=(short)Math.min(Short.MAX_VALUE,Math.round(frames/fpsTime));frames=0;fpsTime=0;}
            if(closing)shutdown();
        }catch(RuntimeException|Error failure){stop(failure);throw failure;}
    }
    static void requestClose(){closing=true;if(pendingFrame!=null)nextFrame(0);}
    static boolean shouldClose(){return closing||!running;}
    private static void shutdown(){stop(null);}
    private static void stop(Throwable failure){
        if(!running)return;running=false;
        try{application.dispose();}catch(RuntimeException|Error error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
        try{Audio.dispose();}catch(RuntimeException|Error error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
        application=null;resetState();
        try{closeHost();}catch(RuntimeException|Error error){if(failure==null)failure=error;else failure.addSuppressed(error);}
        pendingFrame=null;
        if(failure instanceof RuntimeException error)throw error;
        else if(failure instanceof Error error)throw error;
    }
    // Keep init, update, render and dispose on the same TeaVM coroutine. Native
    // callbacks only deliver timing; game callbacks may safely suspend for I/O.
    @Async private static native double awaitFrame();
    private static void awaitFrame(AsyncCallback<Double> callback){if(closing)callback.complete(0d);else pendingFrame=callback;}
    private static void nextFrame(double dt){var callback=pendingFrame;pendingFrame=null;if(callback!=null)callback.complete(dt);}
    public static void runTask(Runnable task){tasks.add(Objects.requireNonNull(task));}
    public static <T extends Event> void subscribe(EventType<T> type,EventHandler<? super T> listener){events.register(type,listener);}
    public static <T extends Event> void subscribe(EventType<T> type,int priority,EventHandler<? super T> listener){events.register(type,priority,listener);}
    public static <T extends Event> void unsubscribe(EventType<T> type,EventHandler<? super T> listener){events.unregister(type,listener);}
    public static void publish(Event event){events.publish(Objects.requireNonNull(event));}
    static void postEvent(Event event){runTask(()->publish(event));}
    public static float getTime(){return running?(float)((clock()-startTime)/1000):0;}
    public static float getDeltaTime(){return delta;}
    public static short getFramesPerSecond(){return fps;}
    static void resetState(){tasks.clear();events.clear();delta=elapsed=fpsTime=0;frames=0;fps=0;running=closing=false;}
    @JSBody(params={"frame","shutdown"},script="valthorneHost.connectApplication(frame,shutdown);") private static native void connect(Frame frame,Shutdown shutdown);
    @JSBody(script="valthorneHost.close();") private static native void closeHost();
    @JSBody(script="return performance.now();") private static native double clock();
}
