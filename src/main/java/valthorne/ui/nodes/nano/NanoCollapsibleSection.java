package valthorne.ui.nodes.nano;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.ui.UINode;
import valthorne.ui.behavior.ChangeSignal;

import java.util.Objects;

/** NanoVG-backed disclosure section. */
public class NanoCollapsibleSection extends NanoPanel {
    private final String title;
    private final NanoButton header;
    private final NanoPanel body = new NanoPanel();
    private final ChangeSignal changes = new ChangeSignal();
    private boolean expanded = true;

    public NanoCollapsibleSection(String title, UINode content) {
        this.title = Objects.requireNonNull(title);
        Objects.requireNonNull(content);
        if (content.getParent() != null) throw new IllegalArgumentException("Content already has a parent");
        header = new NanoButton("[-] " + title) {
            @Override public void onKeyPress(KeyPressEvent event) {
                if (isDisabled()) return;
                if (event.getKey() == Keyboard.LEFT || event.getKey() == Keyboard.RIGHT) {
                    expanded(event.getKey() == Keyboard.RIGHT);
                    event.consume();
                } else super.onKeyPress(event);
            }
        }.action(button -> expanded(!expanded));
        getLayout().column().noShrink();
        header.getLayout().height(36).widthPercent(100).noShrink();
        body.getLayout().widthPercent(100).minHeight(0).noShrink();
        content.getLayout().widthPercent(100);
        body.add(content);
        super.add(header);
        super.add(body);
    }

    public boolean isExpanded() { return expanded; }
    public NanoButton getHeader() { return header; }
    public AutoCloseable onChange(Runnable listener) { return changes.subscribe(listener); }

    public NanoCollapsibleSection expanded(boolean value) {
        if (value == expanded) return this;
        if (!value && getRoot() != null) {
            boolean focus = contains(getRoot().getFocused());
            if (contains(getRoot().getCaptured())) { getRoot().cancelInput(); focus = true; }
            if (focus) getRoot().setFocusTo(header);
        }
        expanded = value;
        body.setVisible(value);
        if (value) body.getLayout().heightAuto(); else body.getLayout().height(0);
        header.text((value ? "[-] " : "[+] ") + title);
        changes.fire();
        return this;
    }

    private boolean contains(UINode node) {
        for (; node != null; node = node.getParent()) if (node == body) return true;
        return false;
    }

    @Override public void update(float delta) {
        header.update(delta);
        if (expanded) body.update(delta);
    }
}
