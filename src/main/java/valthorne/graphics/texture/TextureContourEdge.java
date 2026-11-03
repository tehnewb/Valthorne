package valthorne.graphics.texture;

/**
 * Directed pixel-boundary segment with a mutable traversal marker. Endpoints
 * are immutable point values; stitching consumes each edge at most once.
 * <p>Direction preserves the orientation of the extracted pixel outline. The used flag
 * belongs to one stitching traversal and does not alter endpoint coordinates or
 * change the source image.</p>
 *
 * @author Albert Beaupre
 */
final class TextureContourEdge {
    final TextureContourPoint a; // Directed edge start.
    final TextureContourPoint b; // Directed edge end.
    boolean used; // Whether loop stitching has consumed this edge.

    /**
     * Stores directed boundary endpoints with an initially unused traversal marker.
     *
     * @param a start point
     * @param b end point
     */
    TextureContourEdge(TextureContourPoint a, TextureContourPoint b) {
        this.a = a;
        this.b = b;
    }
}
