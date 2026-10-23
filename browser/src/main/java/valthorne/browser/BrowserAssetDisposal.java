package valthorne.browser;

import valthorne.graphics.font.FontData;
import valthorne.graphics.map.ldtk.LdtkMap;
import valthorne.graphics.map.ldtk.LdtkProject;
import valthorne.graphics.map.tiled.TiledMapData;
import valthorne.graphics.texture.TextureData;

/**
 * Releases cached browser asset values through known lifecycle contracts. Custom
 * loader values implement AutoCloseable; arbitrary reflection is not available in
 * the browser export. Disposal failures are returned so cache cleanup can proceed
 * and report all failures through the shared Assets error aggregation.
 */
public final class BrowserAssetDisposal {
    /**
     * Prevents constructing stateless ownership dispatchers.
     */
    private BrowserAssetDisposal() {
    }

    /**
     * Disposes one built-in or AutoCloseable value without reflective discovery.
     * @param value cached asset, including null
     * @return disposal failure, or null on success or a value without a lifecycle
     */
    public static Throwable dispose(Object value) {
        /*
         * Explicit type branches retain every built-in loader's ownership behavior
         * without forcing applications to enable broad reflection metadata.
         */
        try {
            if (value instanceof AutoCloseable closeable) closeable.close();
            else if (value instanceof TextureData texture) texture.dispose();
            else if (value instanceof FontData font) font.dispose();
            else if (value instanceof TiledMapData map) map.dispose();
            else if (value instanceof LdtkProject project) project.dispose();
            else if (value instanceof LdtkMap map) map.dispose();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }
}
