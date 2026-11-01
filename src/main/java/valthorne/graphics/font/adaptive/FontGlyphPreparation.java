package valthorne.graphics.font.adaptive;

import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * Bounded asynchronous glyph preparation for one font. Two shared daemon workers
 * serve all fonts; each font admits only one worker task at a time and owns an
 * independent native face. Workers never touch OpenGL or the drawing face.
 * At most 256 requests are outstanding. Completed pixels use at most 4 MiB,
 * with one additional in-progress image retained while publication is paused.
 * Backpressure yields the shared worker instead of blocking other fonts.
 * The graphics owner polls completed images and frees their transferred pixels.
 * Closing discards queued work and ready pixels immediately; an active native
 * operation releases its own face and pixels when it returns, without joining
 * a worker on the render thread. Failures propagate through the owner's poll.
 * Synchronization only occurs during preparation; completed warm draws skip it.
 * Visible glyphs and their completed pixels precede speculative default-size
 * prewarming. Tasks yield after four glyphs so other fonts share the CPU pool.
 */
final class FontGlyphPreparation implements AutoCloseable {
    /**
     * Maximum outstanding requests, including ready and in-progress images.
     */
    private static final int MAX_PENDING = 256;
    /**
     * Completed-image budget, excluding the single in-progress native image.
     */
    private static final int MAX_READY_BYTES = 4 * 1024 * 1024;
    /**
     * Native jobs per pool turn, preventing one font from monopolizing a worker.
     */
    private static final int TASK_SLICE = 4;
    /**
     * Small shared CPU pool; daemon workers do not keep applications alive.
     */
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().name("font-preparation-", 0).factory());

    private final ArrayDeque<FontGlyphRequest> requests = new ArrayDeque<>(); // Guarded request order.
    private final ArrayDeque<FontGlyphResult> ready = new ArrayDeque<>(); // Guarded completed ownership transfers.
    private final long[] pending = new long[MAX_PENDING]; // Guarded primitive deduplication keys; zero means unused.
    private byte[] source; // Encoded snapshot retained until the worker creates its own native face.
    private AdaptiveNativeFace face; // Worker-owned face, accessed only by the serialized task or idle cleanup.
    private FontGlyphResult paused; // Prepared image waiting for publication space, or null.
    private Throwable failure; // Worker failure reported to the graphics owner, or null.
    private int pendingCount; // Number of keys admitted but not yet consumed.
    private int readyBytes; // Native pixel bytes currently held by the ready queue.
    private boolean running; // Whether the shared pool owns this font's native face.
    private boolean closed; // Rejects new work and publication after disposal.

    /**
     * Retains an immutable encoded snapshot without starting a worker yet.
     * @param source independently owned encoded bytes
     */
    FontGlyphPreparation(byte[] source) { this.source = source; }

    /**
     * Enqueues a new image unless already pending or admission is full.
     * @param key nonzero glyph/raster cache key
     * @param glyph shaped OpenType index
     * @param raster coverage size or zero for MSDF
     * @param hinting coverage policy
     * @param priority true for visible glyphs, false for speculative prewarming
     * @return whether the key is pending after this call
     */
    synchronized boolean request(long key, int glyph, int raster, FontHinting hinting, boolean priority) {
        if (closed || failure != null) return false;
        int vacant = -1;
        for (int i = 0; i < pending.length; i++) {
            if (pending[i] == key) return true;
            if (pending[i] == 0) vacant = i;
        }
        if (vacant < 0) return false;
        pending[vacant] = key;
        pendingCount++;
        FontGlyphRequest request = new FontGlyphRequest(key, glyph, raster, hinting, priority);
        if (priority) requests.addFirst(request);
        else requests.addLast(request);
        schedule();
        return true;
    }

    /**
     * Transfers one completed image without waiting for a native operation.
     * @return owned prepared pixels, or null if nothing is ready
     * @throws IllegalStateException if a background operation failed
     */
    synchronized FontGlyphResult poll() {
        if (failure != null) throw new IllegalStateException("Automatic font preparation failed", failure);
        FontGlyphResult result = ready.pollFirst();
        if (result == null) return null;
        readyBytes -= pixelBytes(result);
        for (int i = 0; i < pending.length; i++) {
            if (pending[i] != result.key()) continue;
            pending[i] = 0;
            pendingCount--;
            break;
        }
        if (paused != null && readyBytes + pixelBytes(paused) <= MAX_READY_BYTES) {
            if (paused.priority()) ready.addFirst(paused);
            else ready.addLast(paused);
            readyBytes += pixelBytes(paused);
            paused = null;
        }
        schedule();
        return result;
    }

    /**
     * Reports admitted work, including unpublished worker failures.
     * @return whether another owner poll is required
     */
    synchronized boolean hasPending() { return pendingCount != 0 || failure != null; }

    /**
     * Schedules a serialized drain while holding this object's monitor.
     */
    private void schedule() {
        if (running || closed || failure != null || paused != null || requests.isEmpty()) return;
        running = true;
        WORKERS.execute(this::prepare);
    }

    /**
     * Processes native work outside the monitor, publishing only complete images.
     * The running flag prevents another task or idle cleanup from using the face.
     */
    private void prepare() {
        int processed = 0;
        while (true) {
            FontGlyphRequest request;
            synchronized (this) {
                if (closed || failure != null) {
                    stop();
                    return;
                }
                request = requests.pollFirst();
                if (request == null) {
                    running = false;
                    return;
                }
            }
            FontRaster image;
            try {
                if (face == null) {
                    face = new AdaptiveNativeFace(source);
                    source = null;
                }
                image = request.raster() == 0 ? face.msdf(request.glyph()) : face.bitmap(request.glyph(), request.raster(), request.hinting());
            } catch (RuntimeException | Error problem) {
                synchronized (this) {
                    failure = problem;
                    requests.clear();
                    stop();
                }
                return;
            }
            synchronized (this) {
                if (closed) {
                    if (image.pixels() != null) memFree(image.pixels());
                    stop();
                    return;
                }
                FontGlyphResult result = new FontGlyphResult(request.key(), image, request.priority());
                if (readyBytes + pixelBytes(result) > MAX_READY_BYTES) {
                    paused = result;
                    running = false;
                    return;
                }
                if (result.priority()) ready.addFirst(result);
                else ready.addLast(result);
                readyBytes += pixelBytes(result);
                if (++processed == TASK_SLICE) {
                    running = false;
                    schedule();
                    return;
                }
            }
        }
    }

    /**
     * Releases an idle native face after closure or failure under the monitor.
     */
    private void stop() {
        running = false;
        source = null;
        if (face == null) return;
        try {
            face.close();
        } catch (RuntimeException | Error problem) {
            if (failure == null) throw problem;
            failure.addSuppressed(problem);
        } finally {
            face = null;
        }
    }

    /**
     * Measures the owned native buffer, including its actual pixel format.
     * @param result prepared ownership transfer
     * @return native pixel bytes, zero for whitespace
     */
    private int pixelBytes(FontGlyphResult result) {
        return result.image().pixels() == null ? 0 : result.image().pixels().remaining();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        requests.clear();
        FontGlyphResult result;
        while ((result = ready.pollFirst()) != null)
            if (result.image().pixels() != null) memFree(result.image().pixels());
        if (paused != null && paused.image().pixels() != null) memFree(paused.image().pixels());
        paused = null;
        readyBytes = 0;
        if (!running) stop();
    }
}
