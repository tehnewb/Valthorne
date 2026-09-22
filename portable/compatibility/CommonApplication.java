package compatibility;

import valthorne.*;
import valthorne.event.EventTypes;
import valthorne.event.events.KeyPressEvent;
import valthorne.graphics.Color;

/**
 * This exact source is compiled against the desktop and browser engine backends.
 */
public final class CommonApplication implements Application {
    private int updates, renders, tasks, events, nativeKeys, nativeMouse;

    static void main(String[] args) {
        JGL.init(new CommonApplication(), JGLConfiguration.defaults().title("Same source / both targets").size(640, 480).visible(false));
        System.out.println("COMMON_MAIN_RETURNED");
    }

    public void init() {
        JGL.runTask(() -> tasks++);
        JGL.subscribe(EventTypes.KEY_PRESS, event -> {
            if (event.getKey() == Keyboard.A) events++;
            if (event.getKey() == Keyboard.B && Keyboard.isKeyDown(Keyboard.B)) nativeKeys++;
        });
        JGL.subscribe(EventTypes.MOUSE_PRESS, event -> {
            if (event.getButton() == Mouse.RIGHT && Mouse.isButtonDown(Mouse.RIGHT) && event.getY() == Mouse.getY())
                nativeMouse++;
        });
        JGL.publish(new KeyPressEvent(Keyboard.A, 0));
        Mouse.setCursor(Mouse.CURSOR_ARROW);
        if (Keyboard.getKeyChar(Keyboard.A) != 'a') throw new AssertionError("Key mapping differs");
    }

    public void update(float dt) {
        if (dt < 0 || Window.getWidth() <= 0 || Window.getHeight() <= 0)
            throw new AssertionError("Invalid frame state");
        if (++updates == 120) Window.requestClose();
    }

    public void render() {
        Window.clear(Color.NAVY);
        renders++;
    }

    public void dispose() {
        if (updates != 120 || renders != 120 || tasks != 1 || events != 1)
            throw new AssertionError("Lifecycle mismatch");
        System.out.println("COMMON_API_VALIDATED updates=" + updates + " renders=" + renders + " tasks=" + tasks + " events=" + events);
        System.out.println("COMMON_INPUT keys=" + nativeKeys + " mouse=" + nativeMouse);
    }
}
