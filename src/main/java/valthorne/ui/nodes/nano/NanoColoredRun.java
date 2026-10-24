package valthorne.ui.nodes.nano;

import valthorne.graphics.Color;

/**
 * One colored source span produced by a code editor's line highlighter. Ordered
 * spans concatenate to the unchanged source line; coloring does not reshape text.
 *
 * @param text nonnull source substring
 * @param color borrowed foreground tint
 */
public record NanoColoredRun(String text, Color color) {
}
