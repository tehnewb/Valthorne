package valthorne.ui.nodes.nano;

import valthorne.graphics.Color;

/** One colored span produced by a code editor's line highlighter. */
public record NanoColoredRun(String text, Color color) {
}
