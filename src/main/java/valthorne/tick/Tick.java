package valthorne.tick;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A manually updated timer with conditional execution and explicit lifecycle states.
 *
 * <p>Call {@link #update(float)} with your frame delta in seconds. A running tick
 * accumulates scaled time and executes its callback when its delay is reached.
 * A paused tick retains callback progress without accumulating callback time.
 * Its optional action schedule continues to advance. A stopped tick
 * does nothing until explicitly started or restarted.</p>
 *
 * <h2>Conditions</h2>
 * <ul>
 *     <li>{@link #endIf(Predicate)} ends a running or paused tick.</li>
 *     <li>{@link #pauseIf(Predicate)} pauses a running tick and prevents automatic
 *         continuation while the condition remains true.</li>
 *     <li>{@link #continueIf(Predicate)} resumes an already paused tick.</li>
 *     <li>{@link #runIf(Predicate)} gates time accumulation and execution without
 *         changing the lifecycle state.</li>
 *     <li>{@link #skipIf(Predicate)} consumes a due interval without executing its
 *         callback or incrementing its iteration count.</li>
 * </ul>
 *
 * <p>Repeated registrations are combined with OR, except {@code runIf}, whose
 * conditions are combined with AND. Conditions may be evaluated multiple times
 * in one update and should be side-effect-free. Lifecycle conditions are checked
 * before time accumulation, after accumulation, and after each processed interval.
 * Continuation conditions are checked only for a paused tick at update entry.
 * Execution limits and end conditions take priority over pause conditions; pause
 * conditions take priority over continuation conditions.</p>
 *
 * <h2>Timing</h2>
 * <p>By default, at most one interval is processed per update. Missed whole
 * intervals are discarded, but the fractional remainder is retained. Enable
 * {@link #catchUp(boolean)} to process multiple due intervals; the work is bounded
 * by {@link #maxTicksPerUpdate(int)}, including skipped intervals. Unprocessed
 * catch-up time is retained for subsequent updates.</p>
 *
 * <p>A zero delay processes exactly one interval per eligible update, including
 * an update with a zero delta. A zero time scale freezes time and execution but
 * still permits condition-driven lifecycle changes. When an update automatically
 * resumes a paused tick, that update's delta is accepted; deltas from preceding
 * paused updates are never accumulated.</p>
 *
 * <h2>Timed action sequences</h2>
 * <pre>{@code
 * Tick tick = new Tick()
 *         .delay(1.0f)
 *         .callback(t -> System.out.println(t.getIterations()))
 *         .after(1).hours().thenPause()
 *         .after(2).minutes().thenContinue()
 *         .after(30).seconds().thenStop();
 * tick.start();
 * }</pre>
 * <p>Each {@code after} appends to this tick's single ordered schedule. The first
 * wait begins when updates start; later waits begin after the preceding action.
 * Actions are one-shot requests, not permanently true conditions. The schedule
 * uses <em>unscaled update time</em>: it advances while running or paused, even
 * when a run condition fails or the callback time scale is zero. Stopping freezes
 * it. No background thread or wall clock is used; keep calling {@code update}.</p>
 *
 * <p>Frames are split at scheduled action boundaries so that a delayed resume
 * does not credit paused seconds to the callback timer. Due callback intervals
 * at a boundary are processed before the scheduled action, subject to the global
 * per-update interval budget. Zero-wait actions run before accepting more time.
 * End conditions and iteration limits may end the tick before a due action runs.
 * Scheduled continuation also respects pause conditions. A blocked continuation
 * request is consumed, not retried. Existing {@code continueIf} conditions remain
 * active on subsequent updates and can resume a timed pause early.</p>
 *
 * <p>The separate {@link #maxActionsPerUpdate(int)} limit bounds schedule work.
 * Unprocessed update time is retained only when this action budget is exhausted.
 * Callback-induced interruptions and stops discard the unprocessed part of the
 * current frame. {@link #start()} retains schedule progress; {@link #reset()}
 * and {@link #restart()} rewind all registered steps, including runtime additions.
 * {@link #clearSchedule()} removes the schedule without affecting conditions.</p>
 *
 * <h2>Allocation and hot paths</h2>
 * <p>Normal updates allocate no timer-owned objects. Predicate storage and packed
 * schedule arrays are allocated lazily during configuration. An unconditional
 * tick without pending actions takes a dedicated time-accumulation path. Register
 * predicates, callbacks, and action sequences outside hot loops where possible.
 * Allocations performed by user callbacks and predicates are outside this guarantee.
 * This class deliberately has no threads, locks, atomics, reflection, or native code.</p>
 *
 * <h2>Callbacks and safety</h2>
 * <p>Lifecycle callbacks run after their state change is committed. Tick callbacks
 * see a one-based iteration count because it is incremented before invocation.
 * Pausing, ending, restarting, resetting, changing timing configuration, or
 * modifying the action schedule from
 * a callback interrupts the current update, even if the callback subsequently
 * restores the previous state. Remaining catch-up callbacks are not invoked from
 * the interrupted update.</p>
 *
 * <p>Exceptions from user code propagate to the caller. An interval and iteration
 * committed before a callback throws are not rolled back. Recursive calls to
 * {@code update} on this same tick are rejected. This class is not thread-safe;
 * update and configure an instance on its owning thread.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Tick tick = new Tick()
 *         .delay(1.0f)
 *         .repeat(5)
 *         .pauseIf(t -> game.isPaused())
 *         .continueIf(t -> !game.isPaused())
 *         .endIf(t -> player.isDead())
 *         .callback(t -> System.out.println("Tick: " + t.getIterations()))
 *         .onEnd(t -> System.out.println("Finished"));
 *
 * tick.start();
 * // Inside your game loop; keep updating it even while it is paused:
 * tick.update(delta);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since February 14th, 2026
 */
public class Tick {

    private float delay; // Seconds between executions; zero means once per eligible update.
    private double elapsed; // Unconsumed running time, including retained catch-up backlog.
    private double activeTime; // Total scaled time accepted while running and permitted.
    private long iterations; // Accepted, non-skipped iterations in the current run.
    private long skippedIterations; // Explicitly skipped due intervals in the current run.
    private long iterationLimit = Long.MAX_VALUE; // Effective iteration ceiling.
    private boolean unlimitedIterations = true; // Whether the public limit is the -1 sentinel.
    private float timeScale = 1f; // Multiplier applied to accepted frame deltas.
    private boolean catchUp; // Whether multiple positive-delay intervals may run per update.
    private int maxTicksPerUpdate = 64; // Maximum interval attempts in one catch-up update.
    private State state = State.STOPPED; // Current lifecycle state.
    private long revision; // Version used to detect changes during user callbacks.
    private boolean updating; // Guard against recursive updates on this instance.
    private boolean updateInterrupted; // Whether user code invalidated the current update.
    private int intervalAttempts; // Interval attempts consumed across this update's segments.
    private TickSchedule schedule; // Lazily allocated ordered action schedule.
    private int maxActionsPerUpdate = 64; // Maximum scheduled actions processed per update.
    private Consumer<Tick> callback; // Action invoked after an accepted iteration is committed.
    private Consumer<Tick> onStart; // Listener invoked when starting or restarting.
    private Consumer<Tick> onPause; // Listener invoked after transitioning to paused.
    private Consumer<Tick> onContinue; // Listener invoked after transitioning to running.
    private Consumer<Tick> onEnd; // Listener invoked after transitioning to stopped.
    private TickConditions conditions; // Optional lazily allocated predicate storage.

    /**
     * Creates a stopped tick with zero delay, normal speed, and no iteration limit.
     */
    public Tick() {
    }

    /**
     * Advances callback timing and the optional action schedule.
     *
     * <p>Stopped ticks do nothing. Running and paused ticks evaluate lifecycle
     * conditions and advance scheduled waits using the unscaled delta. Only a
     * running tick permitted by every run condition accepts scaled callback time.
     * Scheduled boundaries divide a frame into segments; callbacks due at a
     * positive-time boundary are handled before its scheduled action.</p>
     *
     * <p>Interval and action budgets apply to this entire call, not each segment.
     * A schedule-budget overrun retains the unprocessed frame time for another
     * update. Lifecycle or timing changes made by user code instead interrupt the
     * call and discard its unprocessed time. Committed intervals and actions are
     * not rolled back when user code throws. The recursion guard is always cleared.</p>
     *
     * @param delta finite, nonnegative time in seconds since the preceding frame
     * @throws IllegalArgumentException if delta is negative, NaN, or infinite
     * @throws IllegalStateException    if this tick is already inside an update
     */
    public void update(float delta) {
        requireNonNegativeFinite(delta, "delta");
        if (updating) throw new IllegalStateException("Tick.update cannot be called recursively.");
        if (state == State.STOPPED) return;

        updating = true;
        try {
            TickSchedule sequence = schedule;
            if (conditions == null && (sequence == null || (sequence.index == sequence.size && sequence.backlog == 0.0))) {
                updateSimple(delta);
                return;
            }
            updateConfigured(delta);
        } finally {
            updating = false;
        }
    }

    /**
     * Advances an unscheduled, unconditional timer without visiting predicate groups.
     *
     * <p>Most frames only add time and compare the deadline. The general callback
     * processor is entered only when an interval is due; it preserves callback
     * mutation checks and newly installed conditions. The update recursion guard
     * is already held by the caller.</p>
     *
     * @param delta validated unscaled seconds
     */
    private void updateSimple(float delta) {
        if (iterations >= iterationLimit) {
            end();
            return;
        }
        if (state != State.RUNNING || timeScale == 0f) return;
        double scaledDelta = (double) delta * timeScale;
        elapsed += scaledDelta;
        activeTime += scaledDelta;
        if (delay > 0f && elapsed < delay) return;
        updateInterrupted = false;
        intervalAttempts = 0;
        processCallbacks();
    }

    /**
     * Advances a conditional tick or a tick with pending scheduled actions.
     *
     * @param delta validated unscaled seconds
     */
    private void updateConfigured(float delta) {
        updateInterrupted = false;
        intervalAttempts = 0;
        boolean allowed = canAdvance(true);
        if (updateInterrupted || state == State.STOPPED) return;

        TickSchedule sequence = schedule;
        double remaining = delta;
        if (sequence != null) {
            remaining += sequence.backlog;
            sequence.backlog = 0.0;
        }
        int actions = 0;
        while (state != State.STOPPED && !updateInterrupted) {
            if (sequence == null || sequence.index >= sequence.size) {
                advanceCallbacks(remaining, allowed);
                return;
            }
            if (actions >= maxActionsPerUpdate) {
                sequence.backlog = remaining;
                return;
            }
            double seconds = sequence.delays[sequence.index];
            double wait = Math.max(0.0, seconds - sequence.elapsed);
            if (remaining < wait) {
                sequence.elapsed += remaining;
                advanceCallbacks(remaining, allowed);
                return;
            }
            if (wait > 0.0) {
                sequence.elapsed = seconds;
                remaining -= wait;
                advanceCallbacks(wait, allowed);
                if (updateInterrupted || state == State.STOPPED) return;
            }
            allowed = canAdvance(false);
            if (updateInterrupted || state == State.STOPPED) return;

            Object action = sequence.actions[sequence.index++];
            sequence.elapsed = 0.0;
            actions++;
            perform(action);
            if (updateInterrupted || state == State.STOPPED) return;
            allowed = canAdvance(false);
        }
    }

    /**
     * Accepts the running portion of a frame and processes due callback intervals.
     *
     * @param delta   unscaled seconds in this segment
     * @param allowed whether entry conditions permit accepting callback time
     */
    private void advanceCallbacks(double delta, boolean allowed) {
        if (!allowed || state != State.RUNNING || timeScale == 0f) return;

        double scaledDelta = delta * timeScale;
        elapsed += scaledDelta;
        activeTime += scaledDelta;
        if (canAdvance(false)) processCallbacks();
    }

    /**
     * Processes due intervals after the caller has accepted time and checked gates.
     * Normal one-interval consumption uses subtraction; modulo is reserved for
     * discarding multiple missed intervals when catch-up is disabled.
     */
    private void processCallbacks() {
        while (state == State.RUNNING) {
            double interval = delay;
            if (interval > 0.0 && elapsed < interval) return;

            // Splitting a frame must never grant extra per-update attempts.
            if (intervalAttempts > 0 && (interval == 0.0 || !catchUp)) {
                elapsed = interval == 0.0 ? 0.0 : elapsed % interval;
                return;
            }
            if (catchUp && intervalAttempts >= maxTicksPerUpdate) return;

            long expectedRevision = revision;
            TickConditions checks = conditions;
            boolean skip = checks != null && checks.skipCondition != null && checks.skipCondition.test(this);
            if (!unchanged(expectedRevision) || state != State.RUNNING) return;

            // Commit the interval before user code so exceptions cannot replay it.
            if (interval == 0.0) elapsed = 0.0;
            else if (catchUp || elapsed < interval + interval) elapsed -= interval;
            else elapsed %= interval;

            intervalAttempts++;
            if (skip) {
                if (skippedIterations != Long.MAX_VALUE) skippedIterations++;
            } else {
                iterations++;
                if (callback != null) callback.accept(this);
            }
            if (!unchanged(expectedRevision) || state != State.RUNNING) return;
            if (!canAdvance(false)) return;
            if (interval == 0.0 || !catchUp || intervalAttempts >= maxTicksPerUpdate) return;
        }
    }

    /**
     * Executes one compact action slot without allocating a wrapper object.
     *
     * <p>Slots contain either one of the built-in enum constants or a validated
     * {@code Consumer<Tick>}. Only appendAction can create slots. The unchecked
     * cast is confined to this internal tagged representation.</p>
     *
     * @param action already-consumed action
     */
    @SuppressWarnings("unchecked")
    private void perform(Object action) {
        long expectedRevision = revision;
        if (action == ActionKind.PAUSE) {
            if (state == State.RUNNING) expectedRevision++;
            pause();
        } else if (action == ActionKind.CONTINUE) {
            if (state == State.PAUSED) {
                TickConditions checks = conditions;
                boolean blocked = checks != null && checks.pauseCondition != null && checks.pauseCondition.test(this);
                if (!unchanged(expectedRevision) || blocked) return;
                expectedRevision++;
                resume();
            }
        } else if (action == ActionKind.STOP) {
            if (state != State.STOPPED) expectedRevision++;
            end();
        } else {
            ((Consumer<Tick>) action).accept(this);
        }
        unchanged(expectedRevision);
    }

    /**
     * Marks this update interrupted when user code changed lifecycle or timing.
     *
     * @param expectedRevision revision expected after controlled internal changes
     * @return true if no unexpected change occurred
     */
    private boolean unchanged(long expectedRevision) {
        if (revision == expectedRevision) return true;
        updateInterrupted = true;
        return false;
    }

    /**
     * Applies lifecycle conditions and returns whether this update may advance.
     *
     * <p>A revision check also prevents a condition that improperly mutates the
     * tick from causing further processing against an obsolete run. Predicates
     * should nevertheless be read-only; predicate mutation is not a scheduling API.</p>
     *
     * @param allowContinuation whether this check may automatically resume a pause
     * @return true when running and permitted by every run condition
     */
    private boolean canAdvance(boolean allowContinuation) {
        if (state == State.STOPPED) return false;

        // End rather than let the public iteration counter overflow.
        if (iterations >= iterationLimit) {
            long beforeEnd = revision;
            end();
            unchanged(beforeEnd + 1);
            return false;
        }

        if (conditions == null) return state == State.RUNNING;

        long expectedRevision = revision;
        boolean shouldEnd = conditions != null && conditions.endCondition != null && conditions.endCondition.test(this);
        if (!unchanged(expectedRevision)) return false;
        if (shouldEnd) {
            long beforeEnd = revision;
            end();
            unchanged(beforeEnd + 1);
            return false;
        }

        boolean shouldPause = conditions != null && conditions.pauseCondition != null && conditions.pauseCondition.test(this);
        if (!unchanged(expectedRevision)) return false;
        if (shouldPause) {
            long afterPause = revision + (state == State.RUNNING ? 1 : 0);
            pause();
            unchanged(afterPause);
            return false;
        }

        if (state == State.PAUSED) {
            if (!allowContinuation) return false;
            boolean shouldContinue = conditions != null && conditions.continueCondition != null && conditions.continueCondition.test(this);
            if (!unchanged(expectedRevision) || !shouldContinue) return false;

            resume();
            // resume itself changes the revision once; a listener may change it again.
            if (!unchanged(expectedRevision + 1) || state != State.RUNNING) return false;

            // The continuation listener may have installed new conditions or limits.
            return canAdvance(false);
        }

        boolean allowed = conditions == null || conditions.runCondition == null || conditions.runCondition.test(this);
        return unchanged(expectedRevision) && state == State.RUNNING && allowed;
    }

    /**
     * Starts without resetting time, iteration counters, or schedule progress.
     *
     * <p>Starting a stopped tick invokes {@code onStart}. Starting a paused tick
     * delegates to {@link #resume()} and invokes {@code onContinue}. Starting an
     * already running tick does nothing. Existing conditions and limits remain
     * installed; use {@link #restart()} to begin again with zero counters.</p>
     */
    public void start() {
        if (state == State.PAUSED) {
            resume();
        } else if (state == State.STOPPED) {
            state = State.RUNNING;
            revision++;
            if (onStart != null) onStart.accept(this);
        }
    }

    /**
     * Begins a new run with zero time and counters, replaying the registered schedule.
     *
     * <p>All configuration, predicates, and listeners are preserved. This invokes
     * {@code onStart}, even if the previous run was already running, but does not
     * invoke {@code onEnd} for the replaced run. Calling this from a callback
     * interrupts processing of the old run immediately after that callback returns.</p>
     */
    public void restart() {
        reset();
        start();
    }

    /**
     * Silently stops, clears all timing and counters, and rewinds the schedule.
     *
     * <p>Conditions, timing configuration, limits, and callbacks are preserved.
     * All registered schedule steps are retained and rewound, including steps
     * appended at runtime. No lifecycle listener is invoked. Use {@link #end()} first when an end
     * notification is required before resetting.</p>
     */
    public void reset() {
        state = State.STOPPED;
        elapsed = 0.0;
        activeTime = 0.0;
        iterations = 0;
        skippedIterations = 0;
        if (schedule != null) schedule.rewind();
        revision++;
    }

    /**
     * Pauses a running tick while retaining all timing progress and counters.
     *
     * <p>The state changes before {@code onPause} runs. Calling this while paused
     * or stopped has no effect. Keep calling {@code update} while paused so that
     * {@code continueIf}, {@code endIf}, and scheduled actions remain active.</p>
     */
    public void pause() {
        if (state != State.RUNNING) return;
        state = State.PAUSED;
        revision++;
        if (onPause != null) onPause.accept(this);
    }

    /**
     * Resumes a paused tick without resetting its retained timing progress.
     *
     * <p>The state changes before {@code onContinue} runs. This explicit request
     * does not evaluate predicates; a still-true pause condition can pause the
     * tick again on its next update. Running and stopped ticks are unchanged.</p>
     */
    public void resume() {
        if (state != State.PAUSED) return;
        state = State.RUNNING;
        revision++;
        if (onContinue != null) onContinue.accept(this);
    }

    /**
     * Ends the current run by transitioning a running or paused tick to stopped.
     *
     * <p>Timing progress and counters are retained. {@code onEnd} runs once for
     * the transition; calling this on a stopped tick does nothing. An ended tick
     * can be explicitly started or restarted, but cannot automatically continue.
     * The schedule is frozen, including any steps after a scheduled stop.</p>
     */
    public void end() {
        if (state == State.STOPPED) return;
        state = State.STOPPED;
        revision++;
        if (onEnd != null) onEnd.accept(this);
    }

    /**
     * Stops the tick; retained as the original lifecycle name for {@link #end()}.
     */
    public void stop() {
        end();
    }

    /**
     * Adds an end condition, OR-combined with previously registered end conditions.
     *
     * <p>Any matching condition ends either a running or paused tick. Conditions
     * are checked before time accumulation, after time accumulation, and after
     * each processed interval, so a callback can cause an immediate end by changing
     * data that an end condition reads. Evaluation short-circuits in registration order.</p>
     *
     * @param condition non-null, side-effect-free end predicate
     * @return this tick for chaining
     * @throws NullPointerException if condition is null
     */
    public Tick endIf(Predicate<Tick> condition) {
        Objects.requireNonNull(condition, "condition");
        TickConditions checks = ensureConditions();
        checks.endCondition = checks.endCondition == null ? condition : checks.endCondition.or(condition);
        return this;
    }

    /**
     * Adds a pause condition, OR-combined with existing pause conditions.
     *
     * <p>Any matching condition pauses a running tick or keeps a paused tick
     * paused. Pause conditions take priority over automatic continuation. A
     * condition becoming false does not resume a tick by itself; supply a
     * {@code continueIf} condition or call {@link #resume()}.</p>
     *
     * @param condition non-null, side-effect-free pause predicate
     * @return this tick for chaining
     * @throws NullPointerException if condition is null
     */
    public Tick pauseIf(Predicate<Tick> condition) {
        Objects.requireNonNull(condition, "condition");
        TickConditions checks = ensureConditions();
        checks.pauseCondition = checks.pauseCondition == null ? condition : checks.pauseCondition.or(condition);
        return this;
    }

    /**
     * Adds a continuation condition, OR-combined with existing continuation conditions.
     *
     * <p>At update entry, any matching continuation condition resumes an already
     * paused tick, provided no end or pause condition takes precedence. It never
     * starts a stopped tick and is not evaluated while the tick is running.</p>
     *
     * @param condition non-null, side-effect-free continuation predicate
     * @return this tick for chaining
     * @throws NullPointerException if condition is null
     */
    public Tick continueIf(Predicate<Tick> condition) {
        Objects.requireNonNull(condition, "condition");
        TickConditions checks = ensureConditions();
        checks.continueCondition = checks.continueCondition == null ? condition : checks.continueCondition.or(condition);
        return this;
    }

    /**
     * Adds an execution gate, AND-combined with existing run conditions.
     *
     * <p>Every gate must pass for time to accumulate or callbacks to execute.
     * Failing a gate retains existing progress but leaves the state running; it
     * does not invoke pause or continuation listeners. End and pause conditions
     * are evaluated before these gates.</p>
     *
     * @param condition non-null, side-effect-free execution predicate
     * @return this tick for chaining
     * @throws NullPointerException if condition is null
     */
    public Tick runIf(Predicate<Tick> condition) {
        Objects.requireNonNull(condition, "condition");
        TickConditions checks = ensureConditions();
        checks.runCondition = checks.runCondition == null ? condition : checks.runCondition.and(condition);
        return this;
    }

    /**
     * Adds a skip condition, OR-combined with existing skip conditions.
     *
     * <p>Evaluated only when an interval is due, before consuming that interval.
     * A match consumes the interval but does not invoke the callback or increment
     * {@code iterations}. It increments {@code skippedIterations} instead. Skips
     * count toward the per-update catch-up work limit, but not {@link #repeat(long)}.</p>
     *
     * @param condition non-null, side-effect-free skip predicate
     * @return this tick for chaining
     * @throws NullPointerException if condition is null
     */
    public Tick skipIf(Predicate<Tick> condition) {
        Objects.requireNonNull(condition, "condition");
        TickConditions checks = ensureConditions();
        checks.skipCondition = checks.skipCondition == null ? condition : checks.skipCondition.or(condition);
        return this;
    }

    /**
     * Clears every predicate group without changing state, time, or iteration limits.
     *
     * @return this tick for chaining
     */
    public Tick clearConditions() {
        conditions = null;
        return this;
    }

    /**
     * Removes every registered end condition without changing the iteration limit.
     *
     * @return this tick after removing all end predicates
     */
    public Tick clearEndConditions() {
        if (conditions != null) {
            conditions.endCondition = null;
            releaseEmptyConditions();
        }
        return this;
    }

    /**
     * Removes every registered pause condition without automatically resuming the tick.
     *
     * @return this tick after removing all pause predicates
     */
    public Tick clearPauseConditions() {
        if (conditions != null) {
            conditions.pauseCondition = null;
            releaseEmptyConditions();
        }
        return this;
    }

    /**
     * Removes every registered automatic continuation condition.
     *
     * @return this tick after removing all continuation predicates
     */
    public Tick clearContinueConditions() {
        if (conditions != null) {
            conditions.continueCondition = null;
            releaseEmptyConditions();
        }
        return this;
    }

    /**
     * Removes every execution gate so that running no longer requires a run predicate.
     *
     * @return this tick after removing all run predicates
     */
    public Tick clearRunConditions() {
        if (conditions != null) {
            conditions.runCondition = null;
            releaseEmptyConditions();
        }
        return this;
    }

    /**
     * Removes every registered skip condition so due intervals are no longer explicitly skipped.
     *
     * @return this tick after removing all skip predicates
     */
    public Tick clearSkipConditions() {
        if (conditions != null) {
            conditions.skipCondition = null;
            releaseEmptyConditions();
        }
        return this;
    }

    /**
     * Replaces the end predicate using the original API's setter semantics.
     *
     * <p>Unlike repeated {@code endIf} calls, this replaces the whole end-condition
     * group. Passing null clears it, preserving the original API's behavior.</p>
     *
     * @param condition replacement predicate, or null to clear all end predicates
     * @return this tick for chaining
     * @deprecated Use {@link #endIf(Predicate)}; clear existing predicates first
     * when replacement rather than additive registration is intended.
     */
    @Deprecated
    public Tick stopCondition(Predicate<Tick> condition) {
        if (condition == null) return clearEndConditions();
        ensureConditions().endCondition = condition;
        return this;
    }

    /**
     * Replaces the action invoked for each accepted tick iteration.
     *
     * <p>The iteration count is incremented before invocation. A null callback
     * is allowed; the tick still tracks non-skipped iterations and enforces its
     * repeat limit. An exception propagates without undoing the accepted iteration.</p>
     *
     * @param callback action to invoke, or null to remove it
     * @return this tick for chaining
     */
    public Tick callback(Consumer<Tick> callback) {
        this.callback = callback;
        return this;
    }

    /**
     * Replaces the listener for starts from stopped and explicit restarts.
     *
     * @param listener action invoked after starting, or null to remove it
     * @return this tick for chaining
     */
    public Tick onStart(Consumer<Tick> listener) {
        onStart = listener;
        return this;
    }

    /**
     * Replaces the listener for transitions from running to paused.
     *
     * @param listener action invoked after pausing, or null to remove it
     * @return this tick for chaining
     */
    public Tick onPause(Consumer<Tick> listener) {
        onPause = listener;
        return this;
    }

    /**
     * Replaces the listener for manual or automatic continuation from paused.
     *
     * @param listener action invoked after continuation, or null to remove it
     * @return this tick for chaining
     */
    public Tick onContinue(Consumer<Tick> listener) {
        onContinue = listener;
        return this;
    }

    /**
     * Replaces the listener for transitions from running or paused to stopped.
     *
     * @param listener action invoked after ending, or null to remove it
     * @return this tick for chaining
     */
    public Tick onEnd(Consumer<Tick> listener) {
        onEnd = listener;
        return this;
    }

    /**
     * Changes the delay without resetting retained time or iteration counters.
     *
     * <p>Zero means once per eligible update. A changed value interrupts an update
     * currently executing user code; the new interval is used on the next update.
     * Existing elapsed time is interpreted against the new delay.</p>
     *
     * @param delay finite, nonnegative interval in seconds
     * @return this tick for chaining
     * @throws IllegalArgumentException if delay is negative, NaN, or infinite
     */
    public Tick delay(float delay) {
        requireNonNegativeFinite(delay, "delay");
        if (this.delay != delay) {
            this.delay = delay;
            revision++;
        }
        return this;
    }

    /**
     * Sets a total non-skipped iteration limit for a run, without resetting counters.
     *
     * <p>For example, {@code repeat(5)} permits five iterations in total, not five
     * additional iterations. Zero permits none; minus one removes the limit.
     * Limits are checked during {@code update}, including immediately after a
     * callback, unless that callback interrupted the update with a lifecycle or
     * timing change. Restarting resets the counter but preserves this limit.</p>
     *
     * @param iterations maximum iterations, or -1 for unlimited
     * @return this tick for chaining
     * @throws IllegalArgumentException if iterations is less than -1
     */
    public Tick repeat(long iterations) {
        if (iterations < -1) throw new IllegalArgumentException("iterations must be -1 or nonnegative.");
        unlimitedIterations = iterations == -1;
        iterationLimit = unlimitedIterations ? Long.MAX_VALUE : iterations;
        return this;
    }

    /**
     * Changes the multiplier applied to subsequently accepted frame deltas.
     *
     * <p>One is normal speed, one half is half speed, and zero freezes both time
     * and execution while still checking lifecycle conditions and advancing
     * unscaled schedule waits. Existing elapsed
     * time is not rescaled. A changed value interrupts the current update when
     * called from user code during that update.</p>
     *
     * @param scale finite, nonnegative time multiplier
     * @return this tick for chaining
     * @throws IllegalArgumentException if scale is negative, NaN, or infinite
     */
    public Tick timeScale(float scale) {
        requireNonNegativeFinite(scale, "scale");
        if (timeScale != scale) {
            timeScale = scale;
            revision++;
        }
        return this;
    }

    /**
     * Enables or disables processing multiple due positive-delay intervals per update.
     *
     * <p>Disabled by default. When disabled, one interval is processed and any
     * additional whole intervals are discarded, preserving only the fractional
     * remainder. When enabled, the work limit applies and excess backlog remains.
     * Zero-delay ticks always process at most one interval. Changing this setting
     * interrupts an update currently executing user code.</p>
     *
     * @param enabled whether to process missed intervals
     * @return this tick for chaining
     */
    public Tick catchUp(boolean enabled) {
        if (catchUp != enabled) {
            catchUp = enabled;
            revision++;
        }
        return this;
    }

    /**
     * Sets the maximum number of interval attempts in a catch-up update.
     *
     * <p>The default is 64. Skipped intervals also consume this budget, preventing
     * a skip condition from bypassing the work bound. Remaining elapsed time is
     * retained. Changing the budget interrupts an update currently executing user
     * code; disabling catch-up still restricts processing to one interval.</p>
     *
     * @param maximum positive maximum number of attempts per update
     * @return this tick for chaining
     * @throws IllegalArgumentException if maximum is less than one
     */
    public Tick maxTicksPerUpdate(int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("maximum must be at least 1.");
        if (maxTicksPerUpdate != maximum) {
            maxTicksPerUpdate = maximum;
            revision++;
        }
        return this;
    }

    /**
     * Returns the current lifecycle state without evaluating any conditions.
     *
     * @return the current lifecycle state
     */
    public State getState() {
        return state;
    }

    /**
     * Returns whether the tick is stopped rather than running or paused.
     *
     * @return true only when explicitly stopped or ended, not merely paused
     */
    public boolean isStopped() {
        return state == State.STOPPED;
    }

    /**
     * Returns whether the lifecycle state is running, independently of execution gates.
     *
     * @return true when running, even if a run condition currently prevents progress
     */
    public boolean isRunning() {
        return state == State.RUNNING;
    }

    /**
     * Returns whether the tick is explicitly paused.
     *
     * @return true when paused and retaining its timing progress
     */
    public boolean isPaused() {
        return state == State.PAUSED;
    }

    /**
     * Returns whether updates still observe lifecycle conditions.
     *
     * @return true when running or paused, and therefore still observing conditions
     */
    public boolean isActive() {
        return state != State.STOPPED;
    }

    /**
     * Returns the number of accepted iterations since the last reset or restart.
     *
     * @return the accepted, non-skipped iteration count; the first callback sees one
     */
    public long getIterations() {
        return iterations;
    }

    /**
     * Returns the number of intervals rejected by skip conditions in the current run.
     *
     * @return explicitly skipped due intervals, excluding discarded missed intervals
     */
    public long getSkippedIterations() {
        return skippedIterations;
    }

    /**
     * Returns the configured total iteration limit without evaluating it.
     *
     * @return the configured total iteration limit, or -1 for unlimited
     */
    public long getIterationLimit() {
        return unlimitedIterations ? -1 : iterationLimit;
    }

    /**
     * Returns the current delay between tick iterations.
     *
     * @return the current interval in seconds, or zero for once per eligible update
     */
    public float getDelay() {
        return delay;
    }

    /**
     * Returns unconsumed scaled time, including retained catch-up backlog.
     *
     * <p>This can exceed the configured delay when catch-up is bounded or an
     * update is interrupted. It is retained across pause, resume, end, and start.</p>
     *
     * @return accumulated, unconsumed seconds
     */
    public double getElapsed() {
        return elapsed;
    }

    /**
     * Returns total scaled frame time accepted during the current run.
     *
     * <p>Frame portions spent paused or stopped, or rejected by run conditions,
     * contribute no callback time. Skipped intervals do not remove accepted time. During
     * catch-up, callbacks see the total accepted through the current schedule
     * segment, rather than interpolated per-interval times. Without a schedule,
     * the whole update is one segment. Restart and reset clear this value.</p>
     *
     * @return accumulated active seconds
     */
    public double getActiveTime() {
        return activeTime;
    }

    /**
     * Returns interval progress clamped to the range zero through one.
     *
     * @return one for a zero delay or due interval, otherwise elapsed divided by delay
     */
    public float getProgress() {
        return delay == 0f ? 1f : (float) Math.min(1.0, elapsed / delay);
    }

    /**
     * Returns the additional scaled time required to reach the next interval.
     *
     * <p>This is not wall-clock time; it ignores time scale, conditions, and state.
     * Zero indicates a due interval or a zero-delay tick.</p>
     *
     * @return remaining scaled seconds, never negative
     */
    public double getRemaining() {
        return Math.max(0.0, (double) delay - elapsed);
    }

    /**
     * Returns the configured time multiplier.
     *
     * @return the multiplier applied to accepted frame deltas
     */
    public float getTimeScale() {
        return timeScale;
    }

    /**
     * Returns whether catch-up processing is enabled.
     *
     * @return whether positive-delay ticks process multiple due intervals per update
     */
    public boolean isCatchUp() {
        return catchUp;
    }

    /**
     * Returns the configured catch-up work budget, including skipped intervals.
     *
     * @return the configured maximum catch-up interval attempts per update
     */
    public int getMaxTicksPerUpdate() {
        return maxTicksPerUpdate;
    }

    /**
     * Begins a fluent delayed action definition on this tick's single schedule.
     *
     * <p>Select a unit, then finish with {@code thenPause}, {@code thenContinue},
     * {@code thenStop}, {@code thenEnd}, or {@code then(Consumer)}. Only the final
     * method registers the step. Every terminal method returns this tick, allowing
     * further {@code after} calls or ordinary configuration calls.</p>
     *
     * <p>Steps are sequential even when registered by separate statements. A newly
     * appended step waits behind all pending steps; when none remain, its wait
     * begins with subsequently supplied update time, never with past idle time.
     * Registering inside a callback interrupts the current update. Register a
     * reusable sequence once, rather than rebuilding it on every iteration.</p>
     *
     * @param amount finite, nonnegative duration in the subsequently selected unit
     * @return a single-use unit-selection builder
     * @throws IllegalArgumentException if amount is negative or non-finite
     */
    public After after(double amount) {
        requireNonNegativeFinite(amount, "amount");
        return new After(this, amount);
    }

    /**
     * Removes all scheduled actions and their progress without changing lifecycle.
     *
     * <p>Callback time, counters, predicates, and listeners are unchanged. Retained
     * schedule-budget backlog is discarded. Existing uncommitted builders remain
     * usable. Calling this during user code interrupts the current update.</p>
     *
     * @return this tick for chaining
     */
    public Tick clearSchedule() {
        schedule = null;
        revision++;
        return this;
    }

    /**
     * Sets the maximum number of scheduled actions executed by one update.
     *
     * <p>The default is 64. Zero-duration and no-op actions count toward this
     * limit. Remaining frame time is retained for a later update; it is not
     * credited to either clock until processed. This budget is independent of
     * the callback interval budget. Changing it interrupts the current update.</p>
     *
     * @param maximum positive maximum number of actions per update
     * @return this tick for chaining
     * @throws IllegalArgumentException if maximum is less than one
     */
    public Tick maxActionsPerUpdate(int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("maximum must be at least 1.");
        if (maxActionsPerUpdate != maximum) {
            maxActionsPerUpdate = maximum;
            revision++;
        }
        return this;
    }

    /**
     * Returns the number of unconsumed schedule steps, including the current wait.
     *
     * @return the number of remaining actions; stopped ticks may still have actions
     */
    public int getPendingActions() {
        return schedule == null ? 0 : schedule.size - schedule.index;
    }

    /**
     * Returns the unscaled time already consumed by the current scheduled wait.
     *
     * @return elapsed wait seconds, or zero after the schedule is exhausted
     */
    public double getScheduleElapsed() {
        return schedule == null ? 0.0 : schedule.elapsed;
    }

    /**
     * Returns unprocessed frame time retained after reaching the action budget.
     *
     * @return unscaled seconds awaiting processing by a later update
     */
    public double getScheduleBacklog() {
        return schedule == null ? 0.0 : schedule.backlog;
    }

    /**
     * Returns the configured per-update scheduled action limit.
     *
     * @return maximum scheduled actions processed in a single update
     */
    public int getMaxActionsPerUpdate() {
        return maxActionsPerUpdate;
    }

    /**
     * Registers a fully defined step; completed definitions remain for replay.
     *
     * @param seconds finite, nonnegative wait in seconds
     * @param kind    built-in operation or custom callback marker
     * @param action  callback for a custom step, otherwise null
     * @return this tick for chaining
     */
    final Tick appendAction(double seconds, ActionKind kind, Consumer<Tick> action) {
        if (schedule == null) schedule = new TickSchedule();
        schedule.append(seconds, kind == ActionKind.CUSTOM ? action : kind);
        revision++;
        return this;
    }

    /**
     * Allocates predicate storage only when needed during configuration.
     *
     * @return this tick's predicate storage
     */
    private TickConditions ensureConditions() {
        if (conditions == null) conditions = new TickConditions();
        return conditions;
    }

    /**
     * Releases predicate storage after its last group is cleared.
     */
    private void releaseEmptyConditions() {
        if (conditions.isEmpty()) conditions = null;
    }

    /**
     * Validates a double-precision duration before registration or conversion.
     *
     * @param value duration to validate
     * @param name  parameter name used in error messages
     * @throws IllegalArgumentException if value is negative or non-finite
     */
    static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0)
            throw new IllegalArgumentException(name + " must be finite and nonnegative.");
    }

    /**
     * Validates a floating-point duration or multiplier.
     *
     * @param value value to validate
     * @param name  parameter name included in the exception message
     * @throws IllegalArgumentException if value is negative or non-finite
     */
    private static void requireNonNegativeFinite(float value, String name) {
        if (!Float.isFinite(value) || value < 0f)
            throw new IllegalArgumentException(name + " must be finite and nonnegative.");
    }
}
