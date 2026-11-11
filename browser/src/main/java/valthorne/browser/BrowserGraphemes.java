package valthorne.browser;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSByRef;
import org.teavm.jso.JSObject;

/**
 * Supplies browser-native extended grapheme boundaries to the shared text editor.
 * One immutable Intl segmenter serves all models on the browser UI coroutine and
 * never retains edited strings. Offsets use UTF-16 units, matching Java indices.
 * Callers retain their boundary arrays; writes borrow the backing typed array
 * only for the duration of the call and never allocate a copied result array.
 *
 * <p>The return count includes the initial zero boundary. If storage is too small,
 * the prefix is filled and the required count is returned so callers can grow and
 * retry. ASCII editors retain their existing direct-index fast path. A browser
 * without Intl.Segmenter fails explicitly when non-ASCII editing is first used.</p>
 */
public final class BrowserGraphemes {
    /**
     * Shared lazy-initialized browser segmenter, holding no text or model state.
     */
    private static final JSObject SEGMENTER = createSegmenter();

    /**
     * Prevents constructing stateless segmentation bridges.
     */
    private BrowserGraphemes() {
    }

    /**
     * Writes the available prefix of grapheme boundaries without copying storage.
     *
     * @param text nonnull text to segment
     * @param boundaries nonempty caller-owned destination
     * @return required boundary count, including the initial zero
     * @throws NullPointerException if text or storage is null
     * @throws IllegalArgumentException if storage is empty
     */
    public static int fill(String text, int[] boundaries) {
        /*
         * Borrow the existing typed array for one synchronous call; the segmenter
         * exposes UTF-16 offsets without per-boundary JavaScript/Java crossings.
         */
        if (text == null) throw new NullPointerException("text");
        if (boundaries == null) throw new NullPointerException("boundaries");
        if (boundaries.length == 0) throw new IllegalArgumentException("Boundary storage must not be empty");
        return fill(SEGMENTER, text, boundaries);
    }

    /**
     * Creates the immutable Unicode segmenter when this bridge is first needed.
     *
     * @return retained browser segmenter
     */
    @JSBody(script = "if (typeof Intl.Segmenter !== 'function') throw new Error('Unicode text editing requires Intl.Segmenter'); return new Intl.Segmenter(undefined, {granularity:'grapheme'});")
    private static native JSObject createSegmenter();

    /**
     * Fills borrowed storage and counts remaining boundaries without retaining it.
     *
     * @param segmenter immutable browser segmenter
     * @param text nonnull text whose final boundary equals its UTF-16 length
     * @param boundaries borrowed destination, never retained by JavaScript
     * @return complete required boundary count
     */
    @JSBody(params = {"segmenter", "text", "boundaries"}, script = "var count=1, iterator=segmenter.segment(text)[Symbol.iterator](), next=iterator.next(); boundaries[0]=0; while (!next.done) { var part=next.value; if (count<boundaries.length) boundaries[count]=part.index+part.segment.length; count++; next=iterator.next(); } return count;")
    private static native int fill(JSObject segmenter, String text, @JSByRef int[] boundaries);
}
