package valthorne.event.events;

public class MouseDragEvent extends MouseMoveEvent {

    public MouseDragEvent(int button, int modifiers, int fromX, int fromY, int toX, int toY) {
        super(button, modifiers, fromX, fromY, toX, toY);
    }

    public int getDeltaX() {
        return this.getToX() - this.getX();
    }

    public int getDeltaY() {
        return this.getToY() - this.getY();
    }

}
