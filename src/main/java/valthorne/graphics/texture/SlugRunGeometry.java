package valthorne.graphics.texture;

import org.joml.Matrix4f;
import valthorne.graphics.font.slug.SlugTextRun;

import java.lang.ref.WeakReference;

/**
 * One batch-owned retained submission for a repeatedly drawn text run. Sample
 * bytes are valid only while the atlas generation, layout, projection, viewport,
 * tint, clipping and placement match. This is CPU geometry, not a rendered text
 * image; coverage samples still come from the shared glyph atlas.
 *
 * <p>Entries are reused in a fixed-size set-associative table. The owner bounds
 * their combined byte arrays and never modifies a borrowed text run.</p>
 */
final class SlugRunGeometry {
    final Matrix4f projection = new Matrix4f(); // Exact combined projection associated with the sample bytes.
    WeakReference<SlugTextRun> run; // Weak layout identity avoids retaining disposed fonts through old submissions.
    int identity; // Pending admission fingerprint; collisions can only admit a cold copy early.
    long revision; // Layout revision invalidates externally rebuilt glyph offsets.
    float size; // Retained layout em size.
    float x; // World baseline X.
    float y; // World baseline Y.
    int color; // Packed tint and opacity.
    boolean clipped; // Whether the submitted clip is enabled.
    float clipX; // World clip minimum X.
    float clipY; // World clip minimum Y.
    float clipW; // World clip width.
    float clipH; // World clip height.
    float cosine; // Text transform cosine used for world-space clipping.
    float sine; // Text transform sine used for world-space clipping.
    float transformX; // Text transform horizontal offset.
    float transformY; // Text transform vertical offset.
    int viewportX; // Physical viewport origin X.
    int viewportY; // Physical viewport origin Y.
    int viewportW; // Physical viewport width.
    int viewportH; // Physical viewport height.
    long generation; // Atlas generation referenced by retained sample coordinates.
    long scope; // Most recent drawing scope observing this key.
    byte[] bytes; // Bounded packed glyph instances, or null until repeated reuse.
    boolean complete; // Whether packed storage represents every drawable glyph of its source revision.
    int count; // Instance count represented by the packed bytes.
}
