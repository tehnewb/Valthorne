package valthorne.android.internal;

import java.lang.ref.Cleaner;

/**
 * Retains one native CPU font parser for bitmap metrics and kerning. Explicit
 * close releases immediately; Cleaner supplies fallback for immutable FontData.
 */
public final class AndroidFontOwner implements AutoCloseable {
    /**
     * Shared fallback cleanup service for native CPU font allocations.
     */
    private static final Cleaner cleaner = Cleaner.create();
    private final int token; // Owned parser handle retained by the metrics object.
    private final Cleaner.Cleanable cleanup; // Idempotent parser cleanup independent of GPU lifetime.

    /**
     * Takes ownership of a successfully parsed font.
     * @param token owned nonzero native font token
     */
    public AndroidFontOwner(int token) { this.token = token; cleanup = cleaner.register(this, new AndroidFontRelease(token)); }

    /**
     * Returns the parser while the calling metrics object retains this owner.
     * @return native parser token
     */
    public int getPointer() { return token; }

    @Override
    public void close() { cleanup.clean(); }
}
