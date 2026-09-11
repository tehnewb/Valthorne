package valthorne.ui;

/**
 * Immutable snapshot of CPU timing and drawing counters published by a UI root.
 * Layout counters accumulate until the root records a draw's statistics, then
 * reset. Rendering counters come from that draw's mixed-backend context, while
 * texture draw calls are the batch's cumulative-counter difference for the draw.
 *
 * <p>Durations use nanoseconds from CPU wall-clock measurements, not GPU timer
 * queries. NanoVG flushes and texture draw calls are separate measures and must
 * not be interpreted as equivalent units of work. Node counts track visible-node
 * dispatch attempts, not unique nodes or GPU primitives.</p>
 *
 * <p>The draw timer starts after style refresh and any required layout. It
 * includes inspector setup, viewport binding, tree and inspection drawing, and
 * batch end, but finishes before viewport unbinding.</p>
 *
 * <p>The root records statistics during draw cleanup when a render context was
 * created and batch end succeeds; a snapshot can therefore describe a partially
 * drawn tree if a node callback threw. Construction itself performs no validation,
 * and the root initially exposes an all-zero snapshot.</p>
 *
 * @param layoutPasses     accumulated Yoga calculation passes since the previous recorded draw
 * @param layoutNanos      accumulated CPU time spent in root layout calls, in nanoseconds
 * @param renderNanos      elapsed CPU time measured by the root's draw timer, in nanoseconds
 * @param nodesDrawn       visible-node dispatch attempts made through the render context
 * @param backendSwitches  transitions between texture painting and NanoVG painting
 * @param nanoFlushes      NanoVG end-frame submissions made when returning to texture painting
 * @param textureDrawCalls texture-batch draw calls recorded during this draw
 * @author Albert Beaupre
 * @see UIRoot#getFrameStats()
 */
public record UIFrameStats(long layoutPasses, long layoutNanos, long renderNanos, int nodesDrawn, int backendSwitches, int nanoFlushes, long textureDrawCalls) {
}
