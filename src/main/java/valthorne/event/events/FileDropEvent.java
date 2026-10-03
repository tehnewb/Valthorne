package valthorne.event.events;

import java.nio.file.Path;
import java.util.List;
import valthorne.event.Event;
import valthorne.event.EventTypes;

/**
 * Desktop file-drop notification owning immutable paths copied from the native
 * callback. Coordinates use the window's bottom-left content origin, matching UI
 * pointer picking. Publishing occurs on the window thread; consumers may retain
 * the paths for background imports after the native callback returns.
 */
public final class FileDropEvent extends Event {
    private final List<Path> paths; // Owned immutable drop payload.
    private final float x; // Window-relative horizontal drop coordinate.
    private final float y; // Bottom-origin vertical drop coordinate.

    /**
     * Copies external paths into a consumable desktop input notification.
     *
     * @param paths non-null dropped paths
     * @param x window-relative horizontal coordinate
     * @param y window-relative vertical coordinate
     */
    public FileDropEvent(List<Path> paths, float x, float y) {
        super(EventTypes.FILE_DROP);
        this.paths = List.copyOf(paths);
        this.x = x;
        this.y = y;
    }

    /**
     * Returns paths independent of the native callback's temporary memory.
     *
     * @return immutable dropped paths
     */
    public List<Path> getPaths() {return paths;}

    /**
     * Returns the horizontal drop position.
     *
     * @return window-relative coordinate
     */
    public float getX() {return x;}

    /**
     * Returns the vertical drop position.
     *
     * @return bottom-origin window-relative coordinate
     */
    public float getY() {return y;}
}
