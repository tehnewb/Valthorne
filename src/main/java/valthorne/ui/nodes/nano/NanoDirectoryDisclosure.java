package valthorne.ui.nodes.nano;

import valthorne.event.events.MousePressEvent;
import valthorne.ui.NanoUtility;

import java.nio.file.Path;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Independent directory disclosure hit target with a crisp vector triangle and
 * no surrounding button chrome. It borrows its owner's colors and expansion
 * state and creates no icon texture or native resource.
 *
 * @author Albert Beaupre
 */
final class NanoDirectoryDisclosure extends NanoButton {
    private final NanoDirectoryTree tree; // Stable owner supplying cached expansion and callback behavior.
    private final Path path; // Immutable directory represented by this expansion control.

    /**
     * Creates an independently focusable expansion target with no text glyph.
     *
     * @param tree directory hierarchy owning this disclosure
     * @param path directory expanded or collapsed by activation
     */
    NanoDirectoryDisclosure(NanoDirectoryTree tree, Path path) {
        this.tree = tree;
        this.path = path;
        action(button -> tree.toggleRow(path));
        setTooltip("Expand or collapse " + (path.getFileName() == null ? path : path.getFileName()));
    }

    @Override
    public void onMousePress(MousePressEvent event) {
        if (!tree.isDisabled()) tree.focusRow(path);
    }

    @Override
    public void draw(long vg) {
        if (!isVisible() || vg == 0 || !tree.canExpand(path)) return;
        float centerX = getAbsoluteX() + getWidth() * .5f;
        float centerY = getAbsoluteY() + getHeight() * .5f;
        nvgFillColor(vg, NanoUtility.color1(tree.disclosureColor(isHovered() || isFocused() || tree.isRowSelected(path))));
        nvgBeginPath(vg);
        if (tree.isExpanded(path)) {
            nvgMoveTo(vg, centerX - 4, centerY - 2);
            nvgLineTo(vg, centerX + 4, centerY - 2);
            nvgLineTo(vg, centerX, centerY + 3);
        } else {
            nvgMoveTo(vg, centerX - 2, centerY - 4);
            nvgLineTo(vg, centerX + 3, centerY);
            nvgLineTo(vg, centerX - 2, centerY + 4);
        }
        nvgClosePath(vg);
        nvgFill(vg);
    }
}
