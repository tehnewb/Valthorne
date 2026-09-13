package compatibility;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import valthorne.Application;
import valthorne.JGL;
import valthorne.JGLConfiguration;
import valthorne.Keyboard;
import valthorne.Mouse;
import valthorne.Window;
import valthorne.event.EventTypes;
import valthorne.graphics.Color;
import valthorne.ui.UIRoot;
import valthorne.ui.nodes.nano.NanoButton;

/**
 * Shared Java UI consumer for trusted-input and coroutine compatibility. The
 * browser verifier holds animation frames and filesystem completion separately;
 * application listeners must retain activation without advancing gameplay or
 * entering an already suspended callback. No browser APIs enter this source.
 */
public final class CommonGestureApplication implements Application {
    private static final Path CALLBACK_FILE = Path.of(".codex-temp", "gesture-callback.txt");
    private UIRoot root;
    private Thread owner;
    private int updates, renders, armedUpdates, armedRenders, armedDelta;
    private int clicks, keys, texts, scrolls, ordinaryTasks, callbackDepth, checks;
    private boolean armed;

    public static void main(String[] args) {
        JGL.init(new CommonGestureApplication(), JGLConfiguration.defaults()
                .title("Trusted Java UI input compatibility").size(640, 480).visible(false));
        System.out.println("COMMON_GESTURE_RETURNED");
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private void frozen() {
        check(updates == armedUpdates, "Input wake must not update the application");
        check(renders == armedRenders, "Input wake must not render the application");
        check(Float.floatToIntBits(JGL.getDeltaTime()) == armedDelta, "Input wake preserves frame delta");
        check(ordinaryTasks == 0, "Ordinary tasks remain deferred until an animation frame");
        check(Thread.currentThread() == owner, "Input stays on the application thread");
    }

    @Override
    public void init() {
        owner = Thread.currentThread();
        try { Files.createDirectories(CALLBACK_FILE.getParent()); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
        root = new UIRoot();
        NanoButton button = new NanoButton("Capture pointer").action(node -> capture());
        button.getLayout().absolute().left(100).top(100).width(240).height(60);
        root.add(button);
        root.layout();
        JGL.subscribe(EventTypes.KEY_PRESS, 100, event -> {
            if (event.getKey() == Keyboard.F8) {
                armed = true;
                armedUpdates = updates;
                armedRenders = renders;
                armedDelta = Float.floatToIntBits(JGL.getDeltaTime());
                JGL.runTask(() -> {
                    ordinaryTasks++;
                    System.out.println("GESTURE_ORDINARY_TASK");
                });
                System.out.println("GESTURE_ARMED updates=" + updates + " renders=" + renders);
            } else if (event.getKey() == Keyboard.K) {
                check(callbackDepth == 0, "Keyboard input must not reenter suspended UI callback");
                frozen();
                keys++;
                System.out.println("GESTURE_KEY");
            } else if (event.getKey() == Keyboard.Q) {
                check(clicks == 1 && keys == 1 && texts == 1 && scrolls == 1, "All input routes executed once");
                check(ordinaryTasks == 1, "Deferred ordinary task executed on the resumed frame");
                check(updates > armedUpdates && renders > armedRenders, "Normal animation resumed");
                Window.requestClose();
            }
        });
        JGL.subscribe(EventTypes.TEXT_INPUT, 100, event -> {
            if ("k".equals(event.getText())) {
                check(callbackDepth == 0, "Text input must not reenter suspended UI callback");
                frozen();
                texts++;
                System.out.println("GESTURE_TEXT");
            }
        });
        JGL.subscribe(EventTypes.MOUSE_SCROLL, 100, event -> {
            if (armed) {
                check(callbackDepth == 0, "Scroll input must not reenter suspended UI callback");
                frozen();
                scrolls++;
                System.out.println("GESTURE_SCROLL");
            }
        });
        System.out.println("COMMON_GESTURE_READY");
    }

    private void capture() {
        check(armed && clicks == 0, "Arm before invoking the Java UI action");
        frozen();
        check(callbackDepth++ == 0, "UI callback must not be nested");
        try {
            Mouse.setCursorMode(Mouse.CURSOR_DISABLED);
            System.out.println("GESTURE_CALLBACK_IO");
            Files.writeString(CALLBACK_FILE, "input callback completed");
            check(Files.readString(CALLBACK_FILE).equals("input callback completed"), "UI callback can suspend for real I/O");
            check(keys == 0 && texts == 0 && scrolls == 0, "Other input stayed queued during callback I/O");
            frozen();
            clicks++;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        } finally {
            callbackDepth--;
        }
        System.out.println("GESTURE_CLICK updates=" + updates + " renders=" + renders);
    }

    @Override
    public void update(float delta) {
        updates++;
        root.update(delta);
    }

    @Override
    public void render() {
        Window.clear(Color.NAVY);
        root.draw();
        renders++;
        System.out.println("GESTURE_FRAME updates=" + updates + " renders=" + renders);
    }

    @Override
    public void dispose() {
        if (root != null) root.dispose();
        try { Files.deleteIfExists(CALLBACK_FILE); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
        System.out.println("COMMON_GESTURE_VALIDATED checks=" + checks);
    }
}
