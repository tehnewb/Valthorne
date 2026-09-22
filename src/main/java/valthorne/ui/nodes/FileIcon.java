package valthorne.ui.nodes;

import valthorne.ui.Canvas2D;
import valthorne.ui.nodes.nano.NanoNode;

/**
 * Small vector folder/document marker shared by chooser trees and detail rows.
 * It borrows the root's NanoVG context and allocates no texture or native resource.
 * @author Albert Beaupre
 */
final class FileIcon extends Panel implements NanoNode {
    private final boolean directory; // Whether to paint a yellow folder instead of a document.

    /**
     * Creates a noninteractive marker whose bounds are assigned by the owning row.
     * @param directory true for folders, false for ordinary files
     */
    FileIcon(boolean directory) { this.directory = directory; setClickable(false); }

    /**
     * Paints a recognizable folder tab/body or document outline in top-left coordinates.
     * @param vg borrowed active vector context
     */
    @Override public void draw(long vg) {
        float x = getAbsoluteX(), y = getAbsoluteY();
        if (directory) {
            Canvas2D.color(vg, 0xd6a63e, 1); Canvas2D.beginPath(vg); Canvas2D.rect(vg, x, y + 2, 7, 4); Canvas2D.fill(vg);
            Canvas2D.beginPath(vg); Canvas2D.rect(vg, x, y + 5, 15, 11); Canvas2D.fill(vg);
            Canvas2D.color(vg, 0xffd975, 1); Canvas2D.beginPath(vg); Canvas2D.rect(vg, x + 1, y + 6, 13, 9); Canvas2D.fill(vg);
        } else {
            Canvas2D.color(vg, 0x8dacc8, 1); Canvas2D.beginPath(vg); Canvas2D.rect(vg, x + 2, y + 1, 11, 15); Canvas2D.fill(vg);
            Canvas2D.color(vg, 0xf7fbff, 1); Canvas2D.beginPath(vg); Canvas2D.rect(vg, x + 3, y + 2, 9, 13); Canvas2D.fill(vg);
        }
    }
}
