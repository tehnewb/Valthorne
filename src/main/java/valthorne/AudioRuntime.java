package valthorne;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import valthorne.audio.AudioState;
import valthorne.audio.SoundPlayer;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;

import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.EXTThreadLocalContext.alcSetThreadContext;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Owns one OpenAL session from asynchronous startup through native teardown.
 * Device handles, player registry, and task queue never cross session boundaries.
 * Submission and stopping share a short monitor; driver calls, command execution,
 * future completion, and joins happen outside it. Every accepted command receives
 * an outcome even if startup fails or shutdown overtakes initialization.
 */
final class AudioRuntime {
    private final ConcurrentLinkedQueue<AudioCommand<?>> commands = new ConcurrentLinkedQueue<>(); // Commands admitted before shutdown.
    private final Set<SoundPlayer> players = new HashSet<>(); // Audio-thread-owned active players.
    private final CompletableFuture<Void> ready = new CompletableFuture<>(); // Completion after context and capabilities are usable.
    private final CompletableFuture<Void> terminated = new CompletableFuture<>(); // Completion after native teardown finishes.
    private final Thread thread; // Sole owner of native device, context, and player work.
    private volatile AudioState state = AudioState.STARTING; // Published admission and lifecycle state.
    private volatile Throwable failure; // Published startup, run-loop, or cleanup failure.
    private long device; // Device opened and closed only by the owning audio thread.
    private long context; // Context created and destroyed only by the owning audio thread.
    private boolean threadContext; // Whether the driver provides thread-local current contexts.
    private boolean contextCurrent; // Whether this session successfully bound its native context.
    private SoundPlayer[] playerSnapshot = new SoundPlayer[0]; // Stable iteration snapshot for player callbacks.
    private boolean playersChanged; // Whether registry edits require a new iteration snapshot.

    /**
     * Creates a daemon session without performing native initialization.
     */
    AudioRuntime() {
        thread = new Thread(this::runLoop, "Valthorne-Audio");
        thread.setDaemon(true);
    }

    /**
     * Starts the worker without waiting for its driver. Thread-start failures are
     * published through the same readiness and termination outcomes as native failures.
     */
    void start() {
        try {
            thread.start();
        } catch (RuntimeException | Error cause) {
            failure = cause;
            state = AudioState.FAILED;
            ready.completeExceptionally(cause);
            rejectPending(cause);
            terminated.completeExceptionally(cause);
            cause.printStackTrace();
        }
    }

    /**
     * Returns a safely published lifecycle snapshot.
     *
     * @return current session state
     */
    AudioState state() {
        return state;
    }

    /**
     * Returns the recorded terminal cause without waiting for startup or cleanup.
     *
     * @return failure cause, or null when none has been recorded
     */
    Throwable failure() {
        return failure;
    }

    /**
     * Returns the session's readiness outcome for read-only facade exposure.
     *
     * @return shared readiness future
     */
    CompletableFuture<Void> ready() {
        return ready;
    }

    /**
     * Returns the session's native-teardown outcome for read-only facade exposure.
     *
     * @return shared termination future
     */
    CompletableFuture<Void> terminated() {
        return terminated;
    }

    /**
     * Determines native ownership without requiring a context to exist yet.
     *
     * @return whether the caller is this session's audio thread
     */
    boolean isAudioThread() {
        return Thread.currentThread() == thread;
    }

    /**
     * Returns the native device for an operation already running on the worker.
     *
     * @return live playback device
     */
    long device() {
        if (!isAudioThread()) throw new IllegalStateException("Audio device belongs to the audio thread");
        return device;
    }

    /**
     * Admits a command during startup or normal operation. Shutdown admission is
     * atomic with queue insertion. Audio-thread calls execute inline only while
     * ready, so failure callbacks cannot accidentally use an uninitialized context.
     *
     * @param callable nonnull operation
     * @param <T> result type
     * @return completion for execution, failure, cancellation, or shutdown rejection
     */
    <T> CompletableFuture<T> submit(Callable<T> callable) {
        AudioCommand<T> command = new AudioCommand<>(callable);
        if (isAudioThread()) {
            if (state == AudioState.READY) command.run();
            else command.reject(failure == null ? new CancellationException("Audio session is not ready") : failure);
            return command.result();
        }
        Throwable rejection = null;
        synchronized (this) {
            if (state == AudioState.STARTING || state == AudioState.READY) commands.add(command);
            else rejection = failure == null ? new CancellationException("Audio session is stopping or stopped") : failure;
        }
        if (rejection != null) command.reject(rejection);
        else LockSupport.unpark(thread);
        return command.result();
    }

    /**
     * Closes admission immediately and asks the owner to stop. A driver call already
     * in progress cannot be interrupted safely; its returned handles are released
     * on the worker before termination completes. No joining or native work occurs here.
     */
    void stop() {
        synchronized (this) {
            if (state != AudioState.STARTING && state != AudioState.READY) return;
            state = AudioState.STOPPING;
        }
        ready.completeExceptionally(new CancellationException("Audio stopped before readiness"));
        rejectPending(new CancellationException("Audio session stopped"));
        LockSupport.unpark(thread);
    }

    /**
     * Waits for native teardown, preserving interruption and never joining the owner
     * from itself. Only explicit synchronous lifecycle shutdown calls this method.
     */
    void awaitTermination() {
        if (isAudioThread()) return;
        boolean interrupted = false;
        while (thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException cause) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    /**
     * Adds a player to the owner-thread update registry.
     *
     * @param player live player owned by this session
     */
    void register(SoundPlayer player) {
        if (!isAudioThread()) throw new IllegalStateException("Player registration belongs to the audio thread");
        if (state != AudioState.READY) throw new IllegalStateException("Audio session is not ready");
        if (players.add(player)) playersChanged = true;
    }

    /**
     * Removes a player from automatic updates without disposing it.
     *
     * @param player player to remove
     */
    void unregister(SoundPlayer player) {
        if (!isAudioThread()) throw new IllegalStateException("Player removal belongs to the audio thread");
        if (players.remove(player)) playersChanged = true;
    }

    /**
     * Performs startup, serial commands, player updates, and teardown on one worker.
     * Stop requests are checked between native startup steps. Failures close admission
     * and complete every pending result before resources are released.
     */
    private void runLoop() {
        Throwable terminalFailure = null;
        try {
            if (state != AudioState.STARTING) return;
            device = alcOpenDevice((ByteBuffer) null);
            if (device == NULL) throw new IllegalStateException("Failed to open OpenAL device");
            if (state != AudioState.STARTING) return;
            ALCCapabilities capabilities = ALC.createCapabilities(device);
            threadContext = capabilities.ALC_EXT_thread_local_context;
            if (state != AudioState.STARTING) return;
            context = alcCreateContext(device, (IntBuffer) null);
            if (context == NULL) throw new IllegalStateException("Failed to create OpenAL context");
            if (state != AudioState.STARTING) return;
            boolean current = threadContext ? alcSetThreadContext(context) : alcMakeContextCurrent(context);
            if (!current) throw new IllegalStateException("Failed to activate OpenAL context");
            contextCurrent = true;
            AL.createCapabilities(capabilities);
            synchronized (this) {
                if (state != AudioState.STARTING) return;
                state = AudioState.READY;
            }
            ready.complete(null);
            while (state == AudioState.READY) {
                drainCommands();
                if (state != AudioState.READY) break;
                updatePlayers();
                drainCommands();
                if (state != AudioState.READY) break;
                if (players.isEmpty() && commands.isEmpty()) LockSupport.park();
                else LockSupport.parkNanos(5_000_000L);
            }
        } catch (Throwable cause) {
            terminalFailure = cause;
        } finally {
            synchronized (this) {
                state = AudioState.STOPPING;
                failure = terminalFailure;
            }
            Throwable rejection = terminalFailure == null ? new CancellationException("Audio session stopped") : terminalFailure;
            ready.completeExceptionally(rejection);
            rejectPending(rejection);
            disposePlayers();
            try {
                shutdownOpenAL();
            } catch (Throwable cause) {
                if (terminalFailure == null) terminalFailure = cause;
                else terminalFailure.addSuppressed(cause);
            }
            failure = terminalFailure;
            state = terminalFailure == null ? AudioState.STOPPED : AudioState.FAILED;
            if (terminalFailure == null) terminated.complete(null);
            else {
                terminated.completeExceptionally(terminalFailure);
                terminalFailure.printStackTrace();
            }
        }
    }

    /**
     * Executes admitted commands until stopping or the queue becomes empty.
     */
    private void drainCommands() {
        AudioCommand<?> command;
        while (state == AudioState.READY && (command = commands.poll()) != null) command.run();
    }

    /**
     * Completes queued work after admission closes. This also runs from shutdown
     * callers so command waiters are released even while a native driver call is pending.
     *
     * @param cause lifecycle failure or cancellation delivered to every queued command
     */
    private void rejectPending(Throwable cause) {
        AudioCommand<?> command;
        while ((command = commands.poll()) != null) command.reject(cause);
    }

    /**
     * Updates a stable snapshot so player callbacks can edit the owning registry.
     * Individual player failures are reported without terminating other playback.
     */
    private void updatePlayers() {
        if (playersChanged) {
            playerSnapshot = players.toArray(SoundPlayer[]::new);
            playersChanged = false;
        }
        for (SoundPlayer player : playerSnapshot) {
            if (state != AudioState.READY) return;
            try {
                player.update();
            } catch (Throwable cause) {
                cause.printStackTrace();
            }
        }
    }

    /**
     * Disposes a stable player snapshot while the native context still belongs to
     * this worker. Player disposal may unregister itself without invalidating iteration.
     */
    private void disposePlayers() {
        for (SoundPlayer player : players.toArray(SoundPlayer[]::new)) {
            try {
                player.dispose();
            } catch (Throwable cause) {
                cause.printStackTrace();
            }
        }
        players.clear();
        playerSnapshot = new SoundPlayer[0];
    }

    /**
     * Releases context ownership, context, and device in nested cleanup scopes so
     * later resources are attempted even if an earlier cleanup operation throws.
     */
    private void shutdownOpenAL() {
        try {
            if (contextCurrent) {
                if (threadContext) alcSetThreadContext(NULL);
                else alcMakeContextCurrent(NULL);
            }
        } finally {
            try {
                if (contextCurrent) {
                    AL.setCurrentThread(null);
                    if (!threadContext) AL.setCurrentProcess(null);
                    contextCurrent = false;
                }
            } finally {
                try {
                    if (context != NULL) alcDestroyContext(context);
                } finally {
                    context = NULL;
                    if (device != NULL) alcCloseDevice(device);
                    device = NULL;
                }
            }
        }
    }
}
