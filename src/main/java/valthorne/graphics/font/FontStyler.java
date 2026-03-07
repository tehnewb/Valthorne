package valthorne.graphics.font;

/**
 * Represents a functional interface for applying custom styling to glyphs during rendering.
 * Implementations of this interface define the logic for modifying glyph styles based
 * on their associated context.
 * <p>
 * The {@code style} method allows adjustment of glyph visual attributes such as
 * color, scale, offset, or visibility by operating on the {@code GlyphStyle} object,
 * using information provided in the {@code GlyphContext}.
 * <p>
 * This interface is typically used to define transformations or effects to be applied
 * to individual glyphs in a textual rendering pipeline.
 * <p>
 * Example usage:
 * <pre>{@code
 * FontStyler styler = (style, ctx) -> {
 *     style.setOffset(ctx.getCharIndex() * 2.0f, 0f);
 *     style.setScale(1.5f, 1.5f);
 *     style.setColor(1.0f, 0.5f, 0.0f, 1.0f);
 * };
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 5th, 2026
 */
@FunctionalInterface
public interface FontStyler {

    /**
     * Applies a styling operation to a glyph's visual properties.
     * <p>
     * This method modifies the attributes of the provided {@code GlyphStyle} instance,
     * such as its position, scale, color, or visibility, using information from the
     * associated {@code GlyphContext}. The {@code GlyphContext} contains metadata
     * about the glyph being styled, such as its character, indices, and position
     * within the text layout.
     *
     * @param style the {@code GlyphStyle} object that represents the visual attributes of the glyph.
     *              This object is modified by the implementation to apply the desired styling.
     * @param ctx   the {@code GlyphContext} providing metadata about the glyph, including its
     *              character, indices, and positional context, to inform the styling operation.
     */
    void style(GlyphStyle style, GlyphContext ctx);
}