package valthorne.ui.nodes.nano;

/** Draws a compact command icon in a NanoPopupMenu row. */
@FunctionalInterface
public interface NanoPopupMenuIcon {
    void draw(long vg, float x, float y, float size);
}
