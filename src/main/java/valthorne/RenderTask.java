package valthorne;

import java.util.concurrent.CompletableFuture;

/**
 * Tracks work scheduled for the rendering thread so shutdown can cancel work
 * that never ran. The completion carries any failure back to its requester.
 *
 * @param action work to execute on the rendering thread
 * @param completion result completed by execution or cancelled by shutdown
 */
record RenderTask(Runnable action, CompletableFuture<Void> completion) implements Runnable {
    @Override
    public void run() {
        if (completion.isDone()) return;
        try {
            action.run();
            completion.complete(null);
        } catch (Throwable failure) {
            completion.completeExceptionally(failure);
        }
    }
}
