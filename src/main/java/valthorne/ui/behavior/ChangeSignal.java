package valthorne.ui.behavior;

import java.util.Arrays;
import java.util.Objects;

/**
 * Synchronous change notification shared by UI models such as text editing and
 * selection. Each registration receives an independent subscription handle and
 * callbacks run in registration order on the thread that calls {@link #fire()}.
 * The signal carries no event payload; listeners read the owning model's state.
 *
 * <p>Dispatch captures the current registration array without copying it.
 * Subscribing or removing a subscription replaces that array, leaving an ongoing
 * dispatch unchanged. A listener removed by an earlier callback therefore still
 * runs in the current dispatch. A newly added listener first participates in a
 * subsequent call to fire, including a recursive call made by a callback.</p>
 *
 * <p>Dispatch itself allocates no objects, excluding work performed by callbacks.
 * Registration and removal copy arrays and are linear in the listener count.
 * Callbacks are held strongly while registered; retain and run their removal actions
 * when the listening UI object no longer needs notifications. Registering the
 * same callback twice creates two independently removable registrations.</p>
 *
 * <p>All operations are intended for the UI thread and are not synchronized.
 * Callback failures propagate immediately and skip remaining listeners in that
 * dispatch. Notifications are neither queued nor coalesced, and recursive
 * dispatch is not prevented.</p>
 *
 * @author Albert Beaupre
 */
public final class ChangeSignal {
    /*
     * Shared empty registration snapshot, reused initially and when the final
     * listener is removed. Published registration arrays are never modified.
     */
    private static final ChangeSubscription[] EMPTY = new ChangeSubscription[0];
    private ChangeSubscription[] entries = EMPTY; // Current ordered registrations, replaced whenever subscriptions change.

    /**
     * Appends a listener without invoking it and returns a handle for removing
     * only this registration. Duplicate callback instances are allowed. Running
     * the handle repeatedly is harmless and does not affect other registrations.
     * An ongoing dispatch retains its original snapshot and does not see this
     * addition; any later dispatch captures the updated array.
     *
     * @param callback the nonnull action to invoke synchronously on each change
     * @return an independently removable subscription action
     * @throws NullPointerException if callback is null
     */
    public Runnable subscribe(Runnable callback) {
        ChangeSubscription entry = new ChangeSubscription(this, Objects.requireNonNull(callback));
        ChangeSubscription[] next = Arrays.copyOf(entries, entries.length + 1);
        next[entries.length] = entry;
        entries = next;
        return entry;
    }

    /**
     * Invokes the listeners present at entry, in registration order. Subscription
     * changes made during callbacks do not alter this dispatch's membership.
     * Recursive calls run immediately with their own current snapshot; callers
     * must avoid unbounded notification recursion in model-update callbacks.
     *
     * <p>No work is performed when there are no listeners. A callback's unchecked
     * exception or error propagates to the caller without running the remaining
     * callbacks, and subscription changes already made are not rolled back.</p>
     */
    public void fire() {
        ChangeSubscription[] snapshot = entries;
        for (ChangeSubscription entry : snapshot) entry.callback.run();
    }

    /**
     * Removes the matching registration without modifying an active dispatch snapshot.
     *
     * @param subscription registration to remove by identity
     */
    void remove(ChangeSubscription subscription) {
        for (int i = 0; i < entries.length; i++) {
            if (entries[i] != subscription) continue;
            ChangeSubscription[] next = entries.length == 1 ? EMPTY : new ChangeSubscription[entries.length - 1];
            System.arraycopy(entries, 0, next, 0, i);
            System.arraycopy(entries, i + 1, next, i, next.length - i);
            entries = next;
            return;
        }
    }
}
