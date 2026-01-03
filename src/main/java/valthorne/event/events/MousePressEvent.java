package valthorne.event.events;

public class MousePressEvent extends MouseEvent {
    public MousePressEvent(int button, int modifiers, int x, int y) {
        super(button, modifiers, x, y);
    }
}
