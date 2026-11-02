package java.util.concurrent;

import java.util.function.*;

/**
 * Standard stage API implemented by the cooperative browser future.
 */
public interface CompletionStage<T> {
    /**
     * Maps successful completion; an upstream failure bypasses the mapping.
     * @param fn nonnull completion mapping
     * @return independently owned completion stage
     */
    <U> CompletionStage<U> thenApply(Function<? super T, ? extends U> fn);

    /**
     * Maps successful completion; an upstream failure bypasses the mapping. Execution uses the supplied executor or the default cooperative executor.
     * @param fn nonnull completion mapping
     * @return independently owned completion stage
     */
    <U> CompletionStage<U> thenApplyAsync(Function<? super T, ? extends U> fn);

    /**
     * Maps successful completion; an upstream failure bypasses the mapping. Execution uses the supplied executor or the default cooperative executor.
     * @param fn nonnull completion mapping
     * @param executor nonnull cooperative execution target
     * @return independently owned completion stage
     */
    <U> CompletionStage<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor);

    /**
     * Consumes successful completion and propagates upstream or consumer failure.
     * @param action nonnull completion action
     * @return independently owned completion stage
     */
    CompletionStage<Void> thenAccept(Consumer<? super T> action);

    /**
     * Consumes successful completion and propagates upstream or consumer failure. Execution uses the supplied executor or the default cooperative executor.
     * @param action nonnull completion action
     * @return independently owned completion stage
     */
    CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action);

    /**
     * Consumes successful completion and propagates upstream or consumer failure. Execution uses the supplied executor or the default cooperative executor.
     * @param action nonnull completion action
     * @param executor nonnull cooperative execution target
     * @return independently owned completion stage
     */
    CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor);

    /**
     * Runs an action after successful completion and propagates failures.
     * @param action nonnull completion action
     * @return independently owned completion stage
     */
    CompletionStage<Void> thenRun(Runnable action);

    /**
     * Runs an action after successful completion and propagates failures. Execution uses the supplied executor or the default cooperative executor.
     * @param action nonnull completion action
     * @return independently owned completion stage
     */
    CompletionStage<Void> thenRunAsync(Runnable action);

    /**
     * Runs an action after successful completion and propagates failures. Execution uses the supplied executor or the default cooperative executor.
     * @param action nonnull completion action
     * @param executor nonnull cooperative execution target
     * @return independently owned completion stage
     */
    CompletionStage<Void> thenRunAsync(Runnable action, Executor executor);

    /**
     * Combines two successful results after both inputs finish.
     * @param other independent input stage
     * @param fn nonnull completion mapping
     * @return independently owned completion stage
     */
    <U, V> CompletionStage<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn);

    /**
     * Combines two successful results after both inputs finish. Execution uses the supplied executor or the default cooperative executor.
     * @param other independent input stage
     * @param fn nonnull completion mapping
     * @return independently owned completion stage
     */
    <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn);

    /**
     * Combines two successful results after both inputs finish. Execution uses the supplied executor or the default cooperative executor.
     * @param other independent input stage
     * @param fn nonnull completion mapping
     * @param executor nonnull cooperative execution target
     * @return independently owned completion stage
     */
    <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn, Executor executor);

    /**
     * Consumes both successful results after both inputs finish.
     * @param other independent input stage
     * @param action nonnull completion action
     * @return independently owned completion stage
     */
    <U> CompletionStage<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action);

    /**
     * Consumes both successful results after both inputs finish. Execution uses the supplied executor or the default cooperative executor.
     * @param other independent input stage
     * @param action nonnull completion action
     * @return independently owned completion stage
     */
    <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action);

    /**
     * Consumes both successful results after both inputs finish. Execution uses the supplied executor or the default cooperative executor.
     * @param other independent input stage
     * @param action nonnull completion action
     * @param executor nonnull cooperative execution target
     * @return independently owned completion stage
     */
    <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action, Executor executor);

    /**
     * Runs an action after both inputs complete successfully.
     * @param other independent input stage
     * @param action nonnull completion action
     * @return independently owned completion stage
     */
    CompletionStage<Void> runAfterBoth(CompletionStage<?> other, Runnable action);

    /**
     * Runs an action after both inputs complete successfully. Execution uses the supplied executor or the default cooperative executor.
     * @param other independent input stage
     * @param action nonnull completion action
     * @return independently owned completion stage
     */
    CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action);

    /**
     * Runs an action after both inputs complete successfully. Execution uses the supplied executor or the default cooperative executor.
     * @param other independent input stage
     * @param action nonnull completion action
     * @param executor nonnull cooperative execution target
     * @return independently owned completion stage
     */
    CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor);

    /**
     * Maps the first observed input outcome, propagating failure if that outcome is exceptional.
     * @param other independent input stage
     * @param fn nonnull completion mapping
     * @return independently owned completion stage
     */
    <U> CompletionStage<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn);

    /**
     * Maps the first observed input outcome, propagating failure if that outcome is exceptional. Execution uses the supplied executor or the default cooperative executor.
     * @param other independent input stage
     * @param fn nonnull completion mapping
     * @return independently owned completion stage
     */
    <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn);

    /**
     * Maps the first observed input outcome, propagating failure if that outcome is exceptional. Execution uses the supplied executor or the default cooperative executor.
     * @param other independent input stage
     * @param fn nonnull completion mapping
     * @param executor nonnull cooperative execution target
     * @return independently owned completion stage
     */
    <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn, Executor executor);

    /**
     * Consumes the first observed successful value, propagating an exceptional winning outcome.
     * @param other independent input stage
     * @param action nonnull completion action
     * @return independently owned completion stage
     */
    CompletionStage<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action);

    /**
     * Consumes the first observed successful value, propagating an exceptional winning outcome. Execution uses the supplied executor or the default cooperative executor.
     * @param other independent input stage
     * @param action nonnull completion action
     * @return independently owned completion stage
     */
    CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action);

    /**
     * Consumes the first observed successful value, propagating an exceptional winning outcome. Execution uses the supplied executor or the default cooperative executor.
     * @param other independent input stage
     * @param action nonnull completion action
     * @param executor nonnull cooperative execution target
     * @return independently owned completion stage
     */
    CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action, Executor executor);

    /**
     * Runs an action after the first observed successful outcome.
     * @param other independent input stage
     * @param action nonnull completion action
     * @return independently owned completion stage
     */
    CompletionStage<Void> runAfterEither(CompletionStage<?> other, Runnable action);

    /**
     * Runs an action after the first observed successful outcome. Execution uses the supplied executor or the default cooperative executor.
     * @param other independent input stage
     * @param action nonnull completion action
     * @return independently owned completion stage
     */
    CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action);

    /**
     * Runs an action after the first observed successful outcome. Execution uses the supplied executor or the default cooperative executor.
     * @param other independent input stage
     * @param action nonnull completion action
     * @param executor nonnull cooperative execution target
     * @return independently owned completion stage
     */
    CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor);

    /**
     * Flattens a successful mapping into the stage returned by the mapping.
     * @param fn nonnull completion mapping
     * @return independently owned completion stage
     */
    <U> CompletionStage<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn);

    /**
     * Flattens a successful mapping into the stage returned by the mapping. Execution uses the supplied executor or the default cooperative executor.
     * @param fn nonnull completion mapping
     * @return independently owned completion stage
     */
    <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn);

    /**
     * Flattens a successful mapping into the stage returned by the mapping. Execution uses the supplied executor or the default cooperative executor.
     * @param fn nonnull completion mapping
     * @param executor nonnull cooperative execution target
     * @return independently owned completion stage
     */
    <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor);

    /**
     * Recovers an exceptional completion without changing a successful value.
     * @param fn nonnull completion mapping
     * @return independently owned completion stage
     */
    CompletionStage<T> exceptionally(Function<Throwable, ? extends T> fn);

    /**
     * Recovers an exceptional completion without changing a successful value. Execution uses the supplied executor or the default cooperative executor.
     * @param fn nonnull completion mapping
     * @return independently owned completion stage
     */
    CompletionStage<T> exceptionallyAsync(Function<Throwable, ? extends T> fn);

    /**
     * Recovers an exceptional completion without changing a successful value. Execution uses the supplied executor or the default cooperative executor.
     * @param fn nonnull completion mapping
     * @param executor nonnull cooperative execution target
     * @return independently owned completion stage
     */
    CompletionStage<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor);

    /**
     * Recovers failure through another stage and forwards its eventual outcome.
     * @param fn nonnull completion mapping
     * @return independently owned completion stage
     */
    CompletionStage<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn);

    /**
     * Recovers failure through another stage and forwards its eventual outcome. Execution uses the supplied executor or the default cooperative executor.
     * @param fn nonnull completion mapping
     * @return independently owned completion stage
     */
    CompletionStage<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn);

    /**
     * Recovers failure through another stage and forwards its eventual outcome. Execution uses the supplied executor or the default cooperative executor.
     * @param fn nonnull completion mapping
     * @param executor nonnull cooperative execution target
     * @return independently owned completion stage
     */
    CompletionStage<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor);

    /**
     * Observes completion while retaining the original value or upstream failure.
     * @param action nonnull completion action
     * @return independently owned completion stage
     */
    CompletionStage<T> whenComplete(BiConsumer<? super T, ? super Throwable> action);

    /**
     * Observes completion while retaining the original value or upstream failure. Execution uses the supplied executor or the default cooperative executor.
     * @param action nonnull completion action
     * @return independently owned completion stage
     */
    CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action);

    /**
     * Observes completion while retaining the original value or upstream failure. Execution uses the supplied executor or the default cooperative executor.
     * @param action nonnull completion action
     * @param executor nonnull cooperative execution target
     * @return independently owned completion stage
     */
    CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor);

    /**
     * Maps either normal or exceptional completion into a new outcome.
     * @param fn nonnull completion mapping
     * @return independently owned completion stage
     */
    <U> CompletionStage<U> handle(BiFunction<? super T, Throwable, ? extends U> fn);

    /**
     * Maps either normal or exceptional completion into a new outcome. Execution uses the supplied executor or the default cooperative executor.
     * @param fn nonnull completion mapping
     * @return independently owned completion stage
     */
    <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn);

    /**
     * Maps either normal or exceptional completion into a new outcome. Execution uses the supplied executor or the default cooperative executor.
     * @param fn nonnull completion mapping
     * @param executor nonnull cooperative execution target
     * @return independently owned completion stage
     */
    <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor);

    /**
     * Returns a mutable future view of this stage.
     * @return independently owned completion stage
     */
    CompletableFuture<T> toCompletableFuture();
}
