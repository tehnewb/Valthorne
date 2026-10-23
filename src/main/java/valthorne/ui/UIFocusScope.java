package valthorne.ui;

import static org.lwjgl.nanovg.NanoVG.nvgBeginFrame;
import static org.lwjgl.nanovg.NanoVG.nvgCreateFont;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_ANTIALIAS;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_STENCIL_STROKES;
import static org.lwjgl.nanovg.NanoVGGL3.nvgCreate;
import static org.lwjgl.nanovg.NanoVGGL3.nvgDelete;

/**
 * One modal input boundary and the focus target that preceded it. Both nodes
 * are borrowed references; eligibility is rechecked when restoring focus.
 *
 * <p>The stack of these entries constrains routing to the active modal subtree. Removing
 * a scope may restore its previous focus only if that node is still attached and eligible.</p>
 *
 * @param node     modal subtree root
 * @param previous focus target to restore, possibly null
 * @author Albert Beaupre
 */
record UIFocusScope(UINode node, UINode previous) {
}
