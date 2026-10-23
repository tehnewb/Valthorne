package valthorne.ui.nodes.nano;

import valthorne.ui.UIInputEvent;
import valthorne.ui.UINode;

/**
 * NanoModal shell that routes Escape through the chooser even when the backdrop or
 * an empty dialog area owns focus. Nested dropdown scopes receive their own keys.
 * @author Albert Beaupre
 */
final class NanoChooserDialog extends NanoModal {
    private final NanoFileChooser owner; // Borrowed control coordinating this helper's behavior.

    /**
     * Binds the shell to the original owner so dismissal restores the saved focus.
     * @param owner attached node requesting this dialog
     */
    NanoChooserDialog(NanoFileChooser owner, UINode anchor) {
        super(anchor);
        this.owner = owner;
    }

    /**
     * Converts Escape anywhere in this modal scope into the chooser's cancel result.
     * @param context routed preview event
     */
    @Override
    public void onInputPreview(UIInputEvent context) {
        owner.onInputPreview(context);
    }
}
