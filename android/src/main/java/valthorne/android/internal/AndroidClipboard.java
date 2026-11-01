package valthorne.android.internal;

import android.content.ClipData;
import android.content.ClipboardManager;

/**
 * Provides Android clipboard access for the shared text editing model. Android
 * retains control of clipboard permissions and foreground application access.
 */
public final class AndroidClipboard {
    /**
     * Prevents constructing clipboard utilities.
     */
    private AndroidClipboard() {
    }

    /**
     * Copies nonempty selected text, reporting denied platform access.
     * @param text selected text
     * @return whether Android accepted the clipboard assignment
     */
    public static boolean copy(String text) {
        if (text == null || text.isEmpty()) return false;
        try {
            AndroidRuntime.activity().getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("", text));
            return true;
        } catch (SecurityException denied) {
            return false;
        }
    }

    /**
     * Reads the first available plain text item without coercing content URIs.
     * @return clipboard text, or null when unavailable or denied
     */
    public static String paste() {
        try {
            ClipData data = AndroidRuntime.activity().getSystemService(ClipboardManager.class).getPrimaryClip();
            if (data == null || data.getItemCount() == 0 || data.getItemAt(0).getText() == null) return null;
            return data.getItemAt(0).getText().toString();
        } catch (SecurityException denied) {
            return null;
        }
    }
}
