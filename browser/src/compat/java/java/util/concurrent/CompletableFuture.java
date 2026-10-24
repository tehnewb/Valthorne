package java.util.concurrent;

import java.util.*;
import java.util.function.*;

/**
 * Completable stages for TeaVM's cooperative Java threads, preserving the asynchronous
 * lifecycle APIs used by the shared engine. Waiting suspends the calling coroutine,
 * leaving the browser event loop free to finish rendering and network I/O.
 *
 * <p>Ordinary observers execute on the completing coroutine. Explicit asynchronous
 * operations use their supplied executor or create an independent cooperative Java
 * coroutine. Cancellation skips work that has not started and never interrupts an
 * already running action. Dependent failures use CompletionException, while get
 * preserves the interruptible Future contract and exposes ExecutionException.</p>
 *
 * <p>Observers allocate only when registered. Completion detaches their storage;
 * an iterative per-coroutine queue bounds the stack depth of dependent chains.
 * Minimal stages protect the source's outcome; toCompletableFuture returns an
 * independent mutable copy. This implementation targets the single browser agent
 * and does not provide native worker-thread or shared-memory execution.</p>
 *
 * <pre>{@code
 * CompletableFuture.supplyAsync(() -> loadAsset())
 *         .thenApply(asset -> prepare(asset))
 *         .whenComplete((value, failure) -> publish(value, failure));
 * }</pre>
 * @param <T> successful result type
 */
public class CompletableFuture<T> implements Future<T>, CompletionStage<T> {
    private boolean done; // Whether a normal, exceptional, or cancelled outcome is published.
    private T value; // Normal result, including null, retained after completion.
    private Throwable failure; // Exceptional result, or null for normal or pending completion.
    private ArrayList<Runnable> dependents; // Lazily allocated pending observers, detached on completion.

    /**
     * Starts asynchronous work on independent cooperative Java coroutines.
     */
    private static final Executor ASYNC = action -> new Thread(action).start();

    /**
     * Retains one iterative completion dispatcher per cooperative Java coroutine.
     */
    private static final ThreadLocal<CompletableDispatch> dispatchers = ThreadLocal.withInitial(CompletableDispatch::new);

    /**
     * Creates an incomplete cooperative future.
     */
    public CompletableFuture() {
    }

    /**
     * Drains completion callbacks iteratively so dependent chains cannot exhaust the JavaScript stack.
     * @param actions detached observer list, or null
     */
    private static void dispatch(List<Runnable> actions) {
        /*
         * A dependent completion may enqueue more callbacks; one per-coroutine drain bounds recursive stack growth.
         */
        if (actions == null) return;
        CompletableDispatch dispatcher = dispatchers.get();
        dispatcher.queue.addAll(actions);
        if (dispatcher.draining) return;
        dispatcher.draining = true;
        try {
            Runnable action;
            while ((action = dispatcher.queue.poll()) != null) action.run();
        } finally {
            dispatcher.draining = false;
        }
    }

    /**
     * Registers a completion observer, or dispatches it immediately after an already completed result.
     * @param action nonnull completion action
     */
    private void listen(Runnable action) {
        synchronized (this) {
            if (!done) {
                if (dependents == null) dependents = new ArrayList<>();
                dependents.add(action);
                return;
            }
        }
        dispatch(Collections.singletonList(action));
    }

    /**
     * Detaches an observer that lost an either-stage race, releasing retained stage references.
     * @param action nonnull completion action
     */
    private void unlisten(Runnable action) {
        synchronized (this) {
            if (dependents != null) {
                dependents.remove(action);
                if (dependents.isEmpty()) dependents = null;
            }
        }
    }

    /**
     * Publishes one result and detaches observers before waking waiting coroutines.
     * @param result normal completion value
     * @param error nonnull exceptional outcome
     * @param force whether an existing outcome may be replaced
     * @return completion operation result
     */
    private boolean finish(T result, Throwable error, boolean force) {
        List<Runnable> actions;
        synchronized (this) {
            if (done && !force) return false;
            done = true;
            value = result;
            failure = error;
            actions = dependents;
            dependents = null;
            notifyAll();
        }
        dispatch(actions);
        return true;
    }

    /**
     * Completes this future normally if no outcome has already been published.
     * @param value normal completion value, including null
     * @return completion operation result
     */
    public boolean complete(T value) {
        return finish(value, null, false);
    }

    /**
     * Completes this future with a nonnull exceptional outcome if still incomplete.
     * @param error nonnull exceptional outcome
     * @return completion operation result
     */
    public boolean completeExceptionally(Throwable error) {
        return finish(null, Objects.requireNonNull(error), false);
    }

    @Override
    public boolean cancel(boolean interrupt) {
        synchronized (this) {
            if (done) return failure instanceof CancellationException;
        }
        return completeExceptionally(new CancellationException());
    }

    @Override
    public synchronized boolean isCancelled() {
        return failure instanceof CancellationException;
    }

    @Override
    public synchronized boolean isDone() {
        return done;
    }

    /**
     * Reports exceptional completion, including cancellation.
     * @return completion operation result
     */
    public synchronized boolean isCompletedExceptionally() {
        return failure != null;
    }

    /**
     * Replaces the stored normal outcome without rerunning observers already delivered.
     * @param value normal completion value, including null
     */
    public void obtrudeValue(T value) {
        finish(value, null, true);
    }

    /**
     * Replaces the stored exceptional outcome without rerunning observers already delivered.
     * @param error nonnull exceptional outcome
     */
    public void obtrudeException(Throwable error) {
        finish(null, Objects.requireNonNull(error), true);
    }

    /**
     * Returns an estimate of observers currently retained by this future.
     * @return completion operation result
     */
    public synchronized int getNumberOfDependents() {
        return dependents == null ? 0 : dependents.size();
    }

    /**
     * Preserves existing completion wrappers while adapting another failure for stage propagation.
     * @param error nonnull exceptional outcome
     * @return completion operation result
     */
    private static CompletionException wrap(Throwable error) {
        /*
         * Retain a single CompletionException layer so joins and dependent stages expose a consistent cause.
         */
        return error instanceof CompletionException
                ? (CompletionException) error
                : new CompletionException(error);
    }

    /**
     * Returns the terminal value or throws an unchecked completion failure.
     * @return completion operation result
     */
    private T reportJoin() {
        if (failure instanceof CancellationException) throw (CancellationException) failure;
        if (failure != null) throw wrap(failure);
        return value;
    }

    /**
     * Returns the terminal value or adapts its cause for an interruptible future wait.
     * @return completion operation result
     */
    private T reportGet() throws ExecutionException {
        if (failure instanceof CancellationException) throw (CancellationException) failure;
        if (failure != null)
            throw new ExecutionException(
                    failure instanceof CompletionException && failure.getCause() != null
                            ? failure.getCause()
                            : failure);
        return value;
    }

    @Override
    public synchronized T get() throws InterruptedException, ExecutionException {
        if (Thread.interrupted()) throw new InterruptedException();
        while (!done) wait();
        return reportGet();
    }

    @Override
    public synchronized T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        long remaining = Objects.requireNonNull(unit).toNanos(timeout);
        if (Thread.interrupted()) throw new InterruptedException();
        long start = System.nanoTime();
        while (!done) {
            if (remaining <= 0) throw new TimeoutException();
            wait(Math.max(1, remaining / 1000000));
            remaining = unit.toNanos(timeout) - (System.nanoTime() - start);
        }
        return reportGet();
    }

    /**
     * Waits cooperatively for completion while restoring any observed interruption afterward.
     * @return completion operation result
     */
    public T join() {
        boolean interrupted = false;
        try {
            synchronized (this) {
                while (!done)
                    try {
                        wait();
                    } catch (InterruptedException error) {
                        interrupted = true;
                    }
                return reportJoin();
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns the terminal value or failure immediately, using the fallback while incomplete.
     * @param fallback value used while incomplete or after the deadline
     * @return completion operation result
     */
    public synchronized T getNow(T fallback) {
        return done ? reportJoin() : fallback;
    }

    /**
     * Creates an independent incomplete destination for a dependent operation.
     * @return independently owned completion stage
     */
    public <U> CompletableFuture<U> newIncompleteFuture() {
        return new CompletableFuture<>();
    }

    /**
     * Returns the executor that starts one cooperative Java coroutine per submitted action.
     * @return completion operation result
     */
    public Executor defaultExecutor() {
        return ASYNC;
    }

    @Override
    public CompletableFuture<T> toCompletableFuture() {
        return this;
    }

    /**
     * Copies this outcome into an independently completable future.
     * @return independently owned completion stage
     */
    public CompletableFuture<T> copy() {
        return thenApply(Function.identity());
    }

    /**
     * Exposes this outcome through a stage whose source cannot be completed by callers.
     * @return independently owned completion stage
     */
    public CompletionStage<T> minimalCompletionStage() {
        return new MinimalCompletableStage<>(this);
    }

    /**
     * Creates an independently owned future containing an immediate normal result.
     * @param value normal completion value, including null
     * @return independently owned completion stage
     */
    public static <U> CompletableFuture<U> completedFuture(U value) {
        /*
         * Publish before returning so observers registered later use the ordinary completed-future dispatch path.
         */
        CompletableFuture<U> result = new CompletableFuture<>();
        result.complete(value);
        return result;
    }

    /**
     * Creates an independently owned future containing an immediate exceptional result.
     * @param error nonnull exceptional outcome
     * @return independently owned completion stage
     */
    public static <U> CompletableFuture<U> failedFuture(Throwable error) {
        /*
         * Use normal completion publication so readiness failures and queued command failures share one observer path.
         */
        CompletableFuture<U> result = new CompletableFuture<>();
        result.completeExceptionally(error);
        return result;
    }

    /**
     * Returns a read-only stage containing an immediate normal result.
     * @param value normal completion value, including null
     * @return independently owned completion stage
     */
    public static <U> CompletionStage<U> completedStage(U value) {
        /*
         * Keep the source outcome private; callers requesting a mutable future receive their own copy.
         */
        return completedFuture(value).minimalCompletionStage();
    }

    /**
     * Returns a read-only stage containing an immediate exceptional result.
     * @param error nonnull exceptional outcome
     * @return independently owned completion stage
     */
    public static <U> CompletionStage<U> failedStage(Throwable error) {
        /*
         * Expose failure observation without granting mutation of the retained exceptional outcome.
         */
        return CompletableFuture.<U>failedFuture(error).minimalCompletionStage();
    }

    /**
     * Schedules value production and completes its result with the value or wrapped failure.
     * @param fn nonnull completion mapping
     * @return independently owned completion stage
     */
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> fn) {
        /*
         * Check cancellation before producing a value, and preserve failures thrown by the supplied operation.
         */
        return supplyAsync(fn, ASYNC);
    }

    /**
     * Schedules value production and completes its result with the value or wrapped failure.
     * @param fn nonnull completion mapping
     * @param executor nonnull cooperative execution target
     * @return independently owned completion stage
     */
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> fn, Executor executor) {
        /*
         * Check cancellation before producing a value, and preserve failures thrown by the supplied operation.
         */
        Objects.requireNonNull(fn);
        Objects.requireNonNull(executor);
        CompletableFuture<U> result = new CompletableFuture<>();
        executor.execute(
                () -> {
                    if (result.isDone()) return;
                    try {
                        result.complete(fn.get());
                    } catch (Throwable error) {
                        result.completeExceptionally(wrap(error));
                    }
                });
        return result;
    }

    /**
     * Schedules an action and reports its normal or exceptional completion.
     * @param action nonnull completion action
     * @return independently owned completion stage
     */
    public static CompletableFuture<Void> runAsync(Runnable action) {
        /*
         * Reuse the value-producing dispatch path with a null result to keep cancellation and failure behavior identical.
         */
        return runAsync(action, ASYNC);
    }

    /**
     * Schedules an action and reports its normal or exceptional completion.
     * @param action nonnull completion action
     * @param executor nonnull cooperative execution target
     * @return independently owned completion stage
     */
    public static CompletableFuture<Void> runAsync(Runnable action, Executor executor) {
        /*
         * Reuse the value-producing dispatch path with a null result to keep cancellation and failure behavior identical.
         */
        Objects.requireNonNull(action);
        return supplyAsync(
                () -> {
                    action.run();
                    return null;
                },
                executor);
    }

    /**
     * Runs a completion mapping on the requested executor, preserving result ownership and failures.
     * @param fn nonnull completion mapping
     * @param executor nonnull cooperative execution target
     * @return independently owned completion stage
     */
    private <U> CompletableFuture<U> transform(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        CompletableFuture<U> result = newIncompleteFuture();
        listen(
                () -> {
                    if (result.isDone()) return;
                    Runnable task =
                            () -> {
                                if (result.isDone()) return;
                                try {
                                    result.complete(fn.apply(value, failure));
                                } catch (Throwable error) {
                                    result.completeExceptionally(wrap(error));
                                }
                            };
                    try {
                        if (executor == null) task.run();
                        else executor.execute(task);
                    } catch (Throwable error) {
                        result.completeExceptionally(wrap(error));
                    }
                });
        return result;
    }

    /**
     * Maps successful completion and propagates an upstream failure without invoking the mapper.
     * @param fn nonnull completion mapping
     * @param executor nonnull cooperative execution target
     * @return independently owned completion stage
     */
    private <U> CompletableFuture<U> apply(Function<? super T, ? extends U> fn, Executor executor) {
        Objects.requireNonNull(fn);
        return transform(
                (value, error) -> {
                    if (error != null) throw wrap(error);
                    return fn.apply(value);
                },
                executor);
    }

    @Override
    public <U> CompletableFuture<U> thenApply(Function<? super T, ? extends U> fn) {
        return apply(fn, null);
    }

    @Override
    public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return apply(fn, defaultExecutor());
    }

    @Override
    public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return apply(fn, Objects.requireNonNull(executor));
    }

    @Override
    public CompletableFuture<Void> thenAccept(Consumer<? super T> action) {
        Objects.requireNonNull(action);
        return thenApply(
                v -> {
                    action.accept(v);
                    return null;
                });
    }

    @Override
    public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action) {
        return thenAcceptAsync(action, defaultExecutor());
    }

    @Override
    public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        Objects.requireNonNull(action);
        return thenApplyAsync(
                v -> {
                    action.accept(v);
                    return null;
                },
                executor);
    }

    @Override
    public CompletableFuture<Void> thenRun(Runnable action) {
        Objects.requireNonNull(action);
        return thenApply(
                v -> {
                    action.run();
                    return null;
                });
    }

    @Override
    public CompletableFuture<Void> thenRunAsync(Runnable action) {
        return thenRunAsync(action, defaultExecutor());
    }

    @Override
    public CompletableFuture<Void> thenRunAsync(Runnable action, Executor executor) {
        Objects.requireNonNull(action);
        return thenApplyAsync(
                v -> {
                    action.run();
                    return null;
                },
                executor);
    }

    @Override
    public <U> CompletableFuture<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return transform(Objects.requireNonNull(fn), null);
    }

    @Override
    public <U> CompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return transform(Objects.requireNonNull(fn), defaultExecutor());
    }

    @Override
    public <U> CompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return transform(Objects.requireNonNull(fn), Objects.requireNonNull(executor));
    }

    /**
     * Observes either outcome while preserving upstream failure precedence.
     * @param action nonnull completion action
     * @param executor nonnull cooperative execution target
     * @return independently owned completion stage
     */
    private CompletableFuture<T> observe(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        Objects.requireNonNull(action);
        return transform(
                (v, e) -> {
                    try {
                        action.accept(v, e);
                    } catch (Throwable next) {
                        if (e == null) throw wrap(next);
                        if (e != next) e.addSuppressed(next);
                    }
                    if (e != null) throw wrap(e);
                    return v;
                },
                executor);
    }

    @Override
    public CompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return observe(action, null);
    }

    @Override
    public CompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return observe(action, defaultExecutor());
    }

    @Override
    public CompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return observe(action, Objects.requireNonNull(executor));
    }

    @Override
    public CompletableFuture<T> exceptionally(Function<Throwable, ? extends T> fn) {
        Objects.requireNonNull(fn);
        return handle((v, e) -> e == null ? v : fn.apply(e));
    }

    @Override
    public CompletableFuture<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
        return exceptionallyAsync(fn, defaultExecutor());
    }

    @Override
    public CompletableFuture<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
        Objects.requireNonNull(fn);
        return handleAsync((v, e) -> e == null ? v : fn.apply(e), executor);
    }

    /**
     * Flattens a completion mapping into one independent result and forwards nested failures.
     * @param fn nonnull completion mapping
     * @param executor nonnull cooperative execution target
     * @return independently owned completion stage
     */
    private <U> CompletableFuture<U> compose(BiFunction<? super T, Throwable, ? extends CompletionStage<U>> fn, Executor executor) {
        CompletableFuture<U> result = newIncompleteFuture();
        transform(
                        (v, e) -> {
                            CompletionStage<U> next = Objects.requireNonNull(fn.apply(v, e));
                            next.whenComplete(
                                    (value, error) -> {
                                        if (error == null) result.complete(value);
                                        else result.completeExceptionally(wrap(error));
                                    });
                            return null;
                        },
                        executor)
                .whenComplete(
                        (v, e) -> {
                            if (e != null) result.completeExceptionally(wrap(e));
                        });
        return result;
    }

    /**
     * Flattens a successful stage mapping while bypassing it after upstream failure.
     * @param fn nonnull completion mapping
     * @param executor nonnull cooperative execution target
     * @return independently owned completion stage
     */
    private <U> CompletableFuture<U> composeSuccess(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        Objects.requireNonNull(fn);
        return compose(
                (v, e) -> {
                    if (e != null) throw wrap(e);
                    return fn.apply(v);
                },
                executor);
    }

    @Override
    public <U> CompletableFuture<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return composeSuccess(fn, null);
    }

    @Override
    public <U> CompletableFuture<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return composeSuccess(fn, defaultExecutor());
    }

    @Override
    public <U> CompletableFuture<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return composeSuccess(fn, Objects.requireNonNull(executor));
    }

    @Override
    public CompletableFuture<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn) {
        Objects.requireNonNull(fn);
        return compose((v, e) -> e == null ? completedFuture(v) : fn.apply(e), null);
    }

    @Override
    public CompletableFuture<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return exceptionallyComposeAsync(fn, defaultExecutor());
    }

    @Override
    public CompletableFuture<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor) {
        Objects.requireNonNull(fn);
        return compose(
                (v, e) -> e == null ? completedFuture(v) : fn.apply(e),
                Objects.requireNonNull(executor));
    }

    /**
     * Completes after every input has finished; any exceptional input makes the combined result fail.
     * @param values input futures
     * @return independently owned completion stage
     */
    public static CompletableFuture<Void> allOf(CompletableFuture<?>... values) {
        /*
         * Count every input completion before selecting the first exceptional outcome in stable input order.
         */
        CompletableFuture<?>[] sources = values.clone();
        for (var source : sources) Objects.requireNonNull(source);
        CompletableFuture<Void> result = new CompletableFuture<>();
        if (sources.length == 0) {
            result.complete(null);
            return result;
        }
        int[] remaining = {sources.length};
        for (var source : sources)
            source.listen(
                    () -> {
                        if (--remaining[0] != 0) return;
                        for (var item : sources)
                            if (item.failure != null) {
                                result.completeExceptionally(wrap(item.failure));
                                return;
                            }
                        result.complete(null);
                    });
        return result;
    }

    /**
     * Completes with the first observed outcome and removes observers retained by losing inputs.
     * @param values input futures
     * @return independently owned completion stage
     */
    public static CompletableFuture<Object> anyOf(CompletableFuture<?>... values) {
        /*
         * Detach losing observers after the winning outcome to avoid retaining unrelated unfinished inputs.
         */
        CompletableFuture<?>[] sources = values.clone();
        for (var source : sources) Objects.requireNonNull(source);
        CompletableFuture<Object> result = new CompletableFuture<>();
        Runnable[] actions = new Runnable[sources.length];
        for (int i = 0; i < sources.length; i++) {
            final int index = i;
            actions[i] =
                    () -> {
                        var source = sources[index];
                        if (source.failure == null) result.complete(source.value);
                        else result.completeExceptionally(wrap(source.failure));
                    };
        }
        result.listen(
                () -> {
                    for (int i = 0; i < sources.length; i++) sources[i].unlisten(actions[i]);
                });
        for (int i = 0; i < sources.length && !result.isDone(); i++) sources[i].listen(actions[i]);
        return result;
    }

    /**
     * Combines successful outcomes only after both inputs finish.
     * @param stage independent input stage
     * @param fn nonnull completion mapping
     * @param executor nonnull cooperative execution target
     * @return independently owned completion stage
     */
    private <U, V> CompletableFuture<V> combine(CompletionStage<? extends U> stage, BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
        Objects.requireNonNull(fn);
        CompletableFuture<? extends U> other = Objects.requireNonNull(stage).toCompletableFuture();
        return allOf(this, other).apply(v -> fn.apply(value, other.value), executor);
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return combine(other, fn, null);
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return combine(other, fn, defaultExecutor());
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
        return combine(other, fn, Objects.requireNonNull(executor));
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        Objects.requireNonNull(action);
        return thenCombine(
                other,
                (a, b) -> {
                    action.accept(a, b);
                    return null;
                });
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return thenAcceptBothAsync(other, action, defaultExecutor());
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action, Executor executor) {
        Objects.requireNonNull(action);
        return thenCombineAsync(
                other,
                (a, b) -> {
                    action.accept(a, b);
                    return null;
                },
                executor);
    }

    @Override
    public CompletableFuture<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        Objects.requireNonNull(action);
        return thenCombine(
                other,
                (a, b) -> {
                    action.run();
                    return null;
                });
    }

    @Override
    public CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return runAfterBothAsync(other, action, defaultExecutor());
    }

    @Override
    public CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        Objects.requireNonNull(action);
        return thenCombineAsync(
                other,
                (a, b) -> {
                    action.run();
                    return null;
                },
                executor);
    }

    /**
     * Maps the first observed input outcome; exceptional completion propagates without mapping.
     *
     * @param other independent input stage
     * @param fn nonnull completion mapping
     * @param executor cooperative execution target, or null for inline completion
     * @param <U> mapped result type
     * @return independently owned completion stage
     */
    @SuppressWarnings("unchecked")
    private <U> CompletableFuture<U> either(CompletionStage<? extends T> other, Function<? super T, U> fn, Executor executor) {
        Objects.requireNonNull(fn);
        return anyOf(this, Objects.requireNonNull(other).toCompletableFuture())
                .apply(v -> fn.apply((T) v), executor);
    }

    @Override
    public <U> CompletableFuture<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return either(other, fn, null);
    }

    @Override
    public <U> CompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return either(other, fn, defaultExecutor());
    }

    @Override
    public <U> CompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn, Executor executor) {
        return either(other, fn, Objects.requireNonNull(executor));
    }

    @Override
    public CompletableFuture<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        Objects.requireNonNull(action);
        return applyToEither(
                other,
                v -> {
                    action.accept(v);
                    return null;
                });
    }

    @Override
    public CompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return acceptEitherAsync(other, action, defaultExecutor());
    }

    @Override
    public CompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action, Executor executor) {
        Objects.requireNonNull(action);
        return applyToEitherAsync(
                other,
                v -> {
                    action.accept(v);
                    return null;
                },
                executor);
    }

    @Override
    public CompletableFuture<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        Objects.requireNonNull(action);
        return anyOf(this, other.toCompletableFuture()).thenRun(action);
    }

    @Override
    public CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return runAfterEitherAsync(other, action, defaultExecutor());
    }

    @Override
    public CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        Objects.requireNonNull(action);
        return anyOf(this, other.toCompletableFuture()).thenRunAsync(action, executor);
    }

    /**
     * Schedules completion of this future without replacing an existing outcome.
     * @param fn nonnull completion mapping
     * @return this future
     */
    public CompletableFuture<T> completeAsync(Supplier<? extends T> fn) {
        return completeAsync(fn, defaultExecutor());
    }

    /**
     * Schedules completion of this future without replacing an existing outcome.
     * @param fn nonnull completion mapping
     * @param executor nonnull cooperative execution target
     * @return this future
     */
    public CompletableFuture<T> completeAsync(Supplier<? extends T> fn, Executor executor) {
        Objects.requireNonNull(fn);
        Objects.requireNonNull(executor)
                .execute(
                        () -> {
                            if (isDone()) return;
                            try {
                                complete(fn.get());
                            } catch (Throwable error) {
                                completeExceptionally(wrap(error));
                            }
                        });
        return this;
    }

    /**
     * Creates an executor that suspends a cooperative timer before submitting each action.
     * @param delay delay before executor submission
     * @param unit nonnull duration unit
     * @return completion operation result
     */
    public static Executor delayedExecutor(long delay, TimeUnit unit) {
        /*
         * A sleeping Java coroutine yields to the browser, so delayed work needs neither spinning nor a native thread.
         */
        return delayedExecutor(delay, unit, ASYNC);
    }

    /**
     * Creates an executor that suspends a cooperative timer before submitting each action.
     * @param delay delay before executor submission
     * @param unit nonnull duration unit
     * @param executor nonnull cooperative execution target
     * @return completion operation result
     */
    public static Executor delayedExecutor(long delay, TimeUnit unit, Executor executor) {
        /*
         * A sleeping Java coroutine yields to the browser, so delayed work needs neither spinning nor a native thread.
         */
        Objects.requireNonNull(unit);
        Objects.requireNonNull(executor);
        return action -> {
            Objects.requireNonNull(action);
            ASYNC.execute(
                    () -> {
                        try {
                            unit.sleep(delay);
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        executor.execute(action);
                    });
        };
    }

    /**
     * Arms a cooperative deadline and cancels its timer when another completion wins.
     * @param fallback value used while incomplete or after the deadline
     * @param timeout deadline duration in the supplied unit
     * @param unit nonnull duration unit
     * @param fail whether expiration completes exceptionally
     * @return independently owned completion stage
     */
    private CompletableFuture<T> timeout(T fallback, long timeout, TimeUnit unit, boolean fail) {
        Objects.requireNonNull(unit);
        if (isDone()) return this;
        Thread timer =
                new Thread(
                        () -> {
                            try {
                                unit.sleep(timeout);
                                if (fail) completeExceptionally(new TimeoutException());
                                else complete(fallback);
                            } catch (InterruptedException cancelled) {
                                // A competing completion cancelled this deadline's wait.
                                return;
                            }
                        });
        listen(() -> timer.interrupt());
        timer.start();
        return this;
    }

    /**
     * Completes this future exceptionally if it remains incomplete when the deadline expires.
     * @param timeout deadline duration in the supplied unit
     * @param unit nonnull duration unit
     * @return this future
     */
    public CompletableFuture<T> orTimeout(long timeout, TimeUnit unit) {
        return timeout(null, timeout, unit, true);
    }

    /**
     * Completes this future with the fallback if the deadline wins the completion race.
     * @param value normal completion value, including null
     * @param timeout deadline duration in the supplied unit
     * @param unit nonnull duration unit
     * @return this future
     */
    public CompletableFuture<T> completeOnTimeout(T value, long timeout, TimeUnit unit) {
        return timeout(value, timeout, unit, false);
    }

    @Override
    public String toString() {
        return super.toString()
                + (!done
                        ? "[Not completed]"
                        : failure != null
                                ? "[Completed exceptionally]"
                                : "[Completed normally]");
    }
}
