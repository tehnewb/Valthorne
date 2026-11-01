package valthorne.android.internal;

import java.io.InputStream;

/**
 * Stateless factory for independent Android XML pull cursors used by Tiled loaders.
 * Each document gets its own parser, so concurrent Assets requests share no state.
 */
public final class AndroidXmlFactory {
    /**
     * Shared stateless factory, never a shared document parser.
     */
    private static final AndroidXmlFactory instance = new AndroidXmlFactory();

    /**
     * Prevents unnecessary factory allocation.
     */
    private AndroidXmlFactory() { }

    /**
     * Returns the stateless Android parser factory.
     * @return shared factory
     */
    public static AndroidXmlFactory newInstance() { return instance; }

    /**
     * Returns the same stateless factory for existing Tiled loader construction.
     * @return shared factory
     */
    public static AndroidXmlFactory newFactory() { return instance; }

    /**
     * Opens a fresh parser without taking ownership of the caller's input stream.
     * @param input borrowed encoded XML
     * @return independent document cursor
     * @throws AndroidXmlException if parser initialization fails
     */
    public AndroidXmlReader createAndroidXmlReader(InputStream input) throws AndroidXmlException { return new AndroidXmlReader(input); }
}
