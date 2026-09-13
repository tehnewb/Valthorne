package valthorne.web;
import java.util.*;
import java.util.concurrent.Executor;
/** Cooperative I/O jobs. Each keeps its own Java stack while fetch/decode awaits. */
public final class BrowserAssetExecutor implements Executor {
 private final Set<Thread> active=new HashSet<>();
 private boolean stopped;
 public void execute(Runnable action){
  Objects.requireNonNull(action);if(stopped)throw new IllegalStateException("Asset executor is shut down");
  Thread thread=new Thread(()->{try{if(!stopped)action.run();}finally{active.remove(Thread.currentThread());}});active.add(thread);thread.start();
 }
 public void shutdownNow(){stopped=true;for(Thread thread:new ArrayList<>(active))thread.interrupt();}
 public boolean isShutdown(){return stopped;}
 public boolean isTerminated(){return stopped&&active.isEmpty();}
}
