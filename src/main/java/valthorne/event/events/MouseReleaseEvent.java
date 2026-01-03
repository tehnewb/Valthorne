package valthorne.event.events;

public class MouseReleaseEvent extends MouseEvent {
    public MouseReleaseEvent(int button, int modifiers, int x, int y) {
        super(button, modifiers, x, y);
    }
}
