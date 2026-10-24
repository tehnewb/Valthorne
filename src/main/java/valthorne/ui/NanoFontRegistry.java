package valthorne.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.lwjgl.nanovg.NanoVG.nvgCreateFont;

/**
 * Retains font-file registrations made through NanoUtility for a root-owned
 * NanoVG context. Registration and restoration run only on the rendering thread;
 * ordinary text drawing performs no registry lookups. Font names and source paths
 * survive device replacement, while borrowed native font indices do not.
 */
final class NanoFontRegistry {
    /**
     * Root registries currently attached to live native contexts.
     */
    private static final ArrayList<NanoFontRegistry> active = new ArrayList<>();

    private long context; // Borrowed root context, or zero while the device is unavailable.
    private LinkedHashMap<String, String> fonts; // Lazily retained custom font paths in registration order.

    /**
     * Creates an empty font-registration owner without native resources.
     */
    NanoFontRegistry() {
    }

    /**
     * Loads a font and remembers successful registrations for managed contexts.
     *
     * @param context destination NanoVG context
     * @param name application font name
     * @param path readable font file retained until root disposal
     * @return native font index, or minus one when loading fails
     */
    static int load(long context, String name, String path) {
        /*
         * Only cold registration scans roots; externally owned contexts retain
         * their existing direct native-loading behavior.
         */
        int font = nvgCreateFont(context, name, path);
        if (font < 0) return font;
        for (int i = 0; i < active.size(); i++) {
            NanoFontRegistry registry = active.get(i);
            if (registry.context != context) continue;
            if (registry.fonts == null) registry.fonts = new LinkedHashMap<>();
            registry.fonts.put(name, path);
            break;
        }
        return font;
    }

    /**
     * Restores custom fonts after the root has registered its default faces.
     *
     * @param context newly created native context
     */
    void attach(long context) {
        if (context == 0) return;
        if (fonts != null) {
            for (Map.Entry<String, String> font : fonts.entrySet())
                if (nvgCreateFont(context, font.getKey(), font.getValue()) < 0)
                    throw new IllegalStateException("Could not restore UI font: " + font.getKey());
        }
        this.context = context;
        active.add(this);
    }

    /**
     * Stops matching a native context before its root destroys it.
     */
    void detach() {
        active.remove(this);
        context = 0;
    }

    /**
     * Releases retained registration paths when the root is disposed.
     */
    void clear() {
        detach();
        fonts = null;
    }
}
