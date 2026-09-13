package java.util.concurrent;
import java.util.*;
import java.util.function.*;

/**
 * Completable stages for TeaVM's cooperative Java threads. Waiting suspends the
 * calling coroutine, leaving the browser event loop free to finish network I/O.
 * No native threads, spin loops, or shared-memory atomics are required.
 */
public class CompletableFuture<T> implements Future<T>, CompletionStage<T> {
 private boolean done;
 private T value;
 private Throwable failure;
 private ArrayList<Runnable> dependents;
 private static final Executor ASYNC=action->new Thread(action).start();
 private static final class Dispatch {final ArrayDeque<Runnable> queue=new ArrayDeque<>();boolean draining;}
 private static final ThreadLocal<Dispatch> dispatchers=ThreadLocal.withInitial(Dispatch::new);
 public CompletableFuture(){}
 private static void dispatch(List<Runnable> actions){
  if(actions==null)return;
  Dispatch dispatcher=dispatchers.get();dispatcher.queue.addAll(actions);
  if(dispatcher.draining)return;
  dispatcher.draining=true;
  try{Runnable action;while((action=dispatcher.queue.poll())!=null)action.run();}finally{dispatcher.draining=false;}
 }
 private void listen(Runnable action){
  synchronized(this){if(!done){if(dependents==null)dependents=new ArrayList<>();dependents.add(action);return;}}
  dispatch(Collections.singletonList(action));
 }
 private void unlisten(Runnable action){synchronized(this){if(dependents!=null){dependents.remove(action);if(dependents.isEmpty())dependents=null;}}}
 private boolean finish(T result,Throwable error,boolean force){
  List<Runnable> actions;
  synchronized(this){if(done&&!force)return false;done=true;value=result;failure=error;actions=dependents;dependents=null;notifyAll();}
  dispatch(actions);return true;
 }
 public boolean complete(T value){return finish(value,null,false);}
 public boolean completeExceptionally(Throwable error){return finish(null,Objects.requireNonNull(error),false);}
 public boolean cancel(boolean interrupt){synchronized(this){if(done)return failure instanceof CancellationException;}return completeExceptionally(new CancellationException());}
 public synchronized boolean isCancelled(){return failure instanceof CancellationException;}
 public synchronized boolean isDone(){return done;}
 public synchronized boolean isCompletedExceptionally(){return failure!=null;}
 public void obtrudeValue(T value){finish(value,null,true);}
 public void obtrudeException(Throwable error){finish(null,Objects.requireNonNull(error),true);}
 public synchronized int getNumberOfDependents(){return dependents==null?0:dependents.size();}
 private static CompletionException wrap(Throwable error){return error instanceof CompletionException?(CompletionException)error:new CompletionException(error);}
 private T reportJoin(){if(failure instanceof CancellationException)throw (CancellationException)failure;if(failure!=null)throw wrap(failure);return value;}
 private T reportGet()throws ExecutionException{if(failure instanceof CancellationException)throw (CancellationException)failure;if(failure!=null)throw new ExecutionException(failure instanceof CompletionException&&failure.getCause()!=null?failure.getCause():failure);return value;}
 public synchronized T get()throws InterruptedException,ExecutionException{if(Thread.interrupted())throw new InterruptedException();while(!done)wait();return reportGet();}
 public synchronized T get(long timeout,TimeUnit unit)throws InterruptedException,ExecutionException,TimeoutException{
  long remaining=Objects.requireNonNull(unit).toNanos(timeout);if(Thread.interrupted())throw new InterruptedException();long start=System.nanoTime();
  while(!done){if(remaining<=0)throw new TimeoutException();wait(Math.max(1,remaining/1000000));remaining=unit.toNanos(timeout)-(System.nanoTime()-start);}return reportGet();
 }
 public T join(){boolean interrupted=false;try{synchronized(this){while(!done)try{wait();}catch(InterruptedException error){interrupted=true;}return reportJoin();}}finally{if(interrupted)Thread.currentThread().interrupt();}}
 public synchronized T getNow(T fallback){return done?reportJoin():fallback;}
 public <U> CompletableFuture<U> newIncompleteFuture(){return new CompletableFuture<>();}
 public Executor defaultExecutor(){return ASYNC;}
 public CompletableFuture<T> toCompletableFuture(){return this;}
 public CompletableFuture<T> copy(){return thenApply(Function.identity());}
 public CompletionStage<T> minimalCompletionStage(){return new MinimalStage<>(this);}
 private static final class MinimalStage<V> extends CompletableFuture<V>{
  MinimalStage(CompletableFuture<V> source){source.listen(()->{if(source.failure==null)super.complete(source.value);else super.completeExceptionally(wrap(source.failure));});}
  public boolean complete(V value){throw new UnsupportedOperationException();}public boolean completeExceptionally(Throwable error){throw new UnsupportedOperationException();}
  public boolean cancel(boolean flag){throw new UnsupportedOperationException();}public void obtrudeValue(V value){throw new UnsupportedOperationException();}public void obtrudeException(Throwable error){throw new UnsupportedOperationException();}
  public CompletableFuture<V> toCompletableFuture(){return copy();}
 }
 public static <U> CompletableFuture<U> completedFuture(U value){CompletableFuture<U> result=new CompletableFuture<>();result.complete(value);return result;}
 public static <U> CompletableFuture<U> failedFuture(Throwable error){CompletableFuture<U> result=new CompletableFuture<>();result.completeExceptionally(error);return result;}
 public static <U> CompletionStage<U> completedStage(U value){return completedFuture(value).minimalCompletionStage();}
 public static <U> CompletionStage<U> failedStage(Throwable error){return CompletableFuture.<U>failedFuture(error).minimalCompletionStage();}
 public static <U> CompletableFuture<U> supplyAsync(Supplier<U> fn){return supplyAsync(fn,ASYNC);}
 public static <U> CompletableFuture<U> supplyAsync(Supplier<U> fn,Executor executor){Objects.requireNonNull(fn);Objects.requireNonNull(executor);CompletableFuture<U> result=new CompletableFuture<>();executor.execute(()->{if(result.isDone())return;try{result.complete(fn.get());}catch(Throwable error){result.completeExceptionally(wrap(error));}});return result;}
 public static CompletableFuture<Void> runAsync(Runnable action){return runAsync(action,ASYNC);}
 public static CompletableFuture<Void> runAsync(Runnable action,Executor executor){Objects.requireNonNull(action);return supplyAsync(()->{action.run();return null;},executor);}
 private <U> CompletableFuture<U> transform(BiFunction<? super T,Throwable,? extends U> fn,Executor executor){
  CompletableFuture<U> result=newIncompleteFuture();
  listen(()->{if(result.isDone())return;Runnable task=()->{if(result.isDone())return;try{result.complete(fn.apply(value,failure));}catch(Throwable error){result.completeExceptionally(wrap(error));}};try{if(executor==null)task.run();else executor.execute(task);}catch(Throwable error){result.completeExceptionally(wrap(error));}});return result;
 }
 private <U> CompletableFuture<U> apply(Function<? super T,? extends U> fn,Executor executor){Objects.requireNonNull(fn);return transform((value,error)->{if(error!=null)throw wrap(error);return fn.apply(value);},executor);}
 public <U> CompletableFuture<U> thenApply(Function<? super T,? extends U> fn){return apply(fn,null);}
 public <U> CompletableFuture<U> thenApplyAsync(Function<? super T,? extends U> fn){return apply(fn,defaultExecutor());}
 public <U> CompletableFuture<U> thenApplyAsync(Function<? super T,? extends U> fn,Executor executor){return apply(fn,Objects.requireNonNull(executor));}
 public CompletableFuture<Void> thenAccept(Consumer<? super T> action){Objects.requireNonNull(action);return thenApply(v->{action.accept(v);return null;});}
 public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action){return thenAcceptAsync(action,defaultExecutor());}
 public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action,Executor executor){Objects.requireNonNull(action);return thenApplyAsync(v->{action.accept(v);return null;},executor);}
 public CompletableFuture<Void> thenRun(Runnable action){Objects.requireNonNull(action);return thenApply(v->{action.run();return null;});}
 public CompletableFuture<Void> thenRunAsync(Runnable action){return thenRunAsync(action,defaultExecutor());}
 public CompletableFuture<Void> thenRunAsync(Runnable action,Executor executor){Objects.requireNonNull(action);return thenApplyAsync(v->{action.run();return null;},executor);}
 public <U> CompletableFuture<U> handle(BiFunction<? super T,Throwable,? extends U> fn){return transform(Objects.requireNonNull(fn),null);}
 public <U> CompletableFuture<U> handleAsync(BiFunction<? super T,Throwable,? extends U> fn){return transform(Objects.requireNonNull(fn),defaultExecutor());}
 public <U> CompletableFuture<U> handleAsync(BiFunction<? super T,Throwable,? extends U> fn,Executor executor){return transform(Objects.requireNonNull(fn),Objects.requireNonNull(executor));}
 private CompletableFuture<T> observe(BiConsumer<? super T,? super Throwable> action,Executor executor){Objects.requireNonNull(action);return transform((v,e)->{try{action.accept(v,e);}catch(Throwable next){if(e==null)throw wrap(next);if(e!=next)e.addSuppressed(next);}if(e!=null)throw wrap(e);return v;},executor);}
 public CompletableFuture<T> whenComplete(BiConsumer<? super T,? super Throwable> action){return observe(action,null);}
 public CompletableFuture<T> whenCompleteAsync(BiConsumer<? super T,? super Throwable> action){return observe(action,defaultExecutor());}
 public CompletableFuture<T> whenCompleteAsync(BiConsumer<? super T,? super Throwable> action,Executor executor){return observe(action,Objects.requireNonNull(executor));}
 public CompletableFuture<T> exceptionally(Function<Throwable,? extends T> fn){Objects.requireNonNull(fn);return handle((v,e)->e==null?v:fn.apply(e));}
 public CompletableFuture<T> exceptionallyAsync(Function<Throwable,? extends T> fn){return exceptionallyAsync(fn,defaultExecutor());}
 public CompletableFuture<T> exceptionallyAsync(Function<Throwable,? extends T> fn,Executor executor){Objects.requireNonNull(fn);return handleAsync((v,e)->e==null?v:fn.apply(e),executor);}
 private <U> CompletableFuture<U> compose(BiFunction<? super T,Throwable,? extends CompletionStage<U>> fn,Executor executor){
  CompletableFuture<U> result=newIncompleteFuture();
  transform((v,e)->{CompletionStage<U> next=Objects.requireNonNull(fn.apply(v,e));next.whenComplete((value,error)->{if(error==null)result.complete(value);else result.completeExceptionally(wrap(error));});return null;},executor).whenComplete((v,e)->{if(e!=null)result.completeExceptionally(wrap(e));});return result;
 }
 private <U> CompletableFuture<U> composeSuccess(Function<? super T,? extends CompletionStage<U>> fn,Executor executor){Objects.requireNonNull(fn);return compose((v,e)->{if(e!=null)throw wrap(e);return fn.apply(v);},executor);}
 public <U> CompletableFuture<U> thenCompose(Function<? super T,? extends CompletionStage<U>> fn){return composeSuccess(fn,null);}
 public <U> CompletableFuture<U> thenComposeAsync(Function<? super T,? extends CompletionStage<U>> fn){return composeSuccess(fn,defaultExecutor());}
 public <U> CompletableFuture<U> thenComposeAsync(Function<? super T,? extends CompletionStage<U>> fn,Executor executor){return composeSuccess(fn,Objects.requireNonNull(executor));}
 public CompletableFuture<T> exceptionallyCompose(Function<Throwable,? extends CompletionStage<T>> fn){Objects.requireNonNull(fn);return compose((v,e)->e==null?completedFuture(v):fn.apply(e),null);}
 public CompletableFuture<T> exceptionallyComposeAsync(Function<Throwable,? extends CompletionStage<T>> fn){return exceptionallyComposeAsync(fn,defaultExecutor());}
 public CompletableFuture<T> exceptionallyComposeAsync(Function<Throwable,? extends CompletionStage<T>> fn,Executor executor){Objects.requireNonNull(fn);return compose((v,e)->e==null?completedFuture(v):fn.apply(e),Objects.requireNonNull(executor));}
 public static CompletableFuture<Void> allOf(CompletableFuture<?>... values){
  CompletableFuture<?>[] sources=values.clone();for(var source:sources)Objects.requireNonNull(source);
  CompletableFuture<Void> result=new CompletableFuture<>();if(sources.length==0){result.complete(null);return result;}
  int[] remaining={sources.length};for(var source:sources)source.listen(()->{if(--remaining[0]!=0)return;for(var item:sources)if(item.failure!=null){result.completeExceptionally(wrap(item.failure));return;}result.complete(null);});return result;
 }
 public static CompletableFuture<Object> anyOf(CompletableFuture<?>... values){
  CompletableFuture<?>[] sources=values.clone();for(var source:sources)Objects.requireNonNull(source);
  CompletableFuture<Object> result=new CompletableFuture<>();Runnable[] actions=new Runnable[sources.length];
  for(int i=0;i<sources.length;i++){final int index=i;actions[i]=()->{var source=sources[index];if(source.failure==null)result.complete(source.value);else result.completeExceptionally(wrap(source.failure));};}
  result.listen(()->{for(int i=0;i<sources.length;i++)sources[i].unlisten(actions[i]);});
  for(int i=0;i<sources.length&&!result.isDone();i++)sources[i].listen(actions[i]);return result;
 }
 private <U,V> CompletableFuture<V> combine(CompletionStage<? extends U> stage,BiFunction<? super T,? super U,? extends V> fn,Executor executor){Objects.requireNonNull(fn);CompletableFuture<? extends U> other=Objects.requireNonNull(stage).toCompletableFuture();return allOf(this,other).apply(v->fn.apply(value,other.value),executor);}
 public <U,V> CompletableFuture<V> thenCombine(CompletionStage<? extends U> other,BiFunction<? super T,? super U,? extends V> fn){return combine(other,fn,null);}
 public <U,V> CompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other,BiFunction<? super T,? super U,? extends V> fn){return combine(other,fn,defaultExecutor());}
 public <U,V> CompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other,BiFunction<? super T,? super U,? extends V> fn,Executor executor){return combine(other,fn,Objects.requireNonNull(executor));}
 public <U> CompletableFuture<Void> thenAcceptBoth(CompletionStage<? extends U> other,BiConsumer<? super T,? super U> action){Objects.requireNonNull(action);return thenCombine(other,(a,b)->{action.accept(a,b);return null;});}
 public <U> CompletableFuture<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,BiConsumer<? super T,? super U> action){return thenAcceptBothAsync(other,action,defaultExecutor());}
 public <U> CompletableFuture<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,BiConsumer<? super T,? super U> action,Executor executor){Objects.requireNonNull(action);return thenCombineAsync(other,(a,b)->{action.accept(a,b);return null;},executor);}
 public CompletableFuture<Void> runAfterBoth(CompletionStage<?> other,Runnable action){Objects.requireNonNull(action);return thenCombine(other,(a,b)->{action.run();return null;});}
 public CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other,Runnable action){return runAfterBothAsync(other,action,defaultExecutor());}
 public CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other,Runnable action,Executor executor){Objects.requireNonNull(action);return thenCombineAsync(other,(a,b)->{action.run();return null;},executor);}
 @SuppressWarnings("unchecked") private <U> CompletableFuture<U> either(CompletionStage<? extends T> other,Function<? super T,U> fn,Executor executor){Objects.requireNonNull(fn);return anyOf(this,Objects.requireNonNull(other).toCompletableFuture()).apply(v->fn.apply((T)v),executor);}
 public <U> CompletableFuture<U> applyToEither(CompletionStage<? extends T> other,Function<? super T,U> fn){return either(other,fn,null);}
 public <U> CompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other,Function<? super T,U> fn){return either(other,fn,defaultExecutor());}
 public <U> CompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other,Function<? super T,U> fn,Executor executor){return either(other,fn,Objects.requireNonNull(executor));}
 public CompletableFuture<Void> acceptEither(CompletionStage<? extends T> other,Consumer<? super T> action){Objects.requireNonNull(action);return applyToEither(other,v->{action.accept(v);return null;});}
 public CompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends T> other,Consumer<? super T> action){return acceptEitherAsync(other,action,defaultExecutor());}
 public CompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends T> other,Consumer<? super T> action,Executor executor){Objects.requireNonNull(action);return applyToEitherAsync(other,v->{action.accept(v);return null;},executor);}
 public CompletableFuture<Void> runAfterEither(CompletionStage<?> other,Runnable action){Objects.requireNonNull(action);return anyOf(this,other.toCompletableFuture()).thenRun(action);}
 public CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other,Runnable action){return runAfterEitherAsync(other,action,defaultExecutor());}
 public CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other,Runnable action,Executor executor){Objects.requireNonNull(action);return anyOf(this,other.toCompletableFuture()).thenRunAsync(action,executor);}
 public CompletableFuture<T> completeAsync(Supplier<? extends T> fn){return completeAsync(fn,defaultExecutor());}
 public CompletableFuture<T> completeAsync(Supplier<? extends T> fn,Executor executor){Objects.requireNonNull(fn);Objects.requireNonNull(executor).execute(()->{if(isDone())return;try{complete(fn.get());}catch(Throwable error){completeExceptionally(wrap(error));}});return this;}
 public static Executor delayedExecutor(long delay,TimeUnit unit){return delayedExecutor(delay,unit,ASYNC);}
 public static Executor delayedExecutor(long delay,TimeUnit unit,Executor executor){Objects.requireNonNull(unit);Objects.requireNonNull(executor);return action->{Objects.requireNonNull(action);ASYNC.execute(()->{try{unit.sleep(delay);}catch(InterruptedException error){Thread.currentThread().interrupt();return;}executor.execute(action);});};}
 private CompletableFuture<T> timeout(T fallback,long timeout,TimeUnit unit,boolean fail){
  Objects.requireNonNull(unit);if(isDone())return this;
  Thread timer=new Thread(()->{try{unit.sleep(timeout);if(fail)completeExceptionally(new TimeoutException());else complete(fallback);}catch(InterruptedException ignored){}});
  listen(()->timer.interrupt());timer.start();return this;
 }
 public CompletableFuture<T> orTimeout(long timeout,TimeUnit unit){return timeout(null,timeout,unit,true);}
 public CompletableFuture<T> completeOnTimeout(T value,long timeout,TimeUnit unit){return timeout(value,timeout,unit,false);}
 public String toString(){return super.toString()+(!isDone()?"[Not completed]":isCompletedExceptionally()?"[Completed exceptionally]":"[Completed normally]");}
}
