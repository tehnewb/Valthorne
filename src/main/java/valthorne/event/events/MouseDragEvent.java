package valthorne.event.events;

public class MouseDragEvent extends MouseMoveEvent {

    public MouseDragEvent(int button, int modifiers, int fromX, int fromY, int toX, int toY) {
        super(button, modifiers, fromX, fromY, toX, toY);
    }

}
