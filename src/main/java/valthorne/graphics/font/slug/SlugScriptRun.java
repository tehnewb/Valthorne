package valthorne.graphics.font.slug;

/**
 * One logically contiguous, script-compatible directional shaping run. The bidi
 * level participates in Unicode visual reordering before HarfBuzz shapes the run.
 *
 * @param start inclusive absolute UTF-16 start
 * @param end exclusive absolute UTF-16 end
 * @param level resolved Unicode bidi embedding level
 */
record SlugScriptRun(int start, int end, byte level) {}
