package valthorne.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import valthorne.Application;
import valthorne.portable.FrameLoop;
import java.util.Objects;

/** Runs a Java application after the asynchronous browser host has initialized. */
public final class WebRuntime {
    private WebRuntime() {}
    @FunctionalInterface public interface Input {void update(int commands, int rgb);}
    @JSFunctor public interface Frame extends JSObject {void run(double delta, int commands, int color);}
    @JSFunctor public interface Shutdown extends JSObject {void run();}

    /** Commands: bit 1 drop, bit 2 reset, bit 4 pause. The supplied host owns their DOM bindings. */
    public static void start(Application app, Input input, Runnable afterFrame) {
        Objects.requireNonNull(input); Objects.requireNonNull(afterFrame);
        var loop = new FrameLoop(app, 1f / 60, 5);
        loop.start();
        connect((delta, commands, color) -> {
            input.update(commands, color);
            loop.setPaused((commands & 4) != 0);
            loop.frame(delta);
            afterFrame.run();
        }, loop::close);
    }

    @JSBody(params = {"frame", "shutdown"}, script = "globalThis.valthorneHost.connect(frame, shutdown);")
    private static native void connect(Frame frame, Shutdown shutdown);
    @JSBody(params = {"bodies", "seconds"}, script = "globalThis.valthorneHost.report(bodies, seconds);")
    public static native void report(int bodies, int seconds);
}
