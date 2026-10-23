package valthorne.ui.nodes.nano;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.ui.UINode;
import valthorne.ui.behavior.ChangeSignal;

import java.util.Objects;

/**
 * NanoVG-backed disclosure section.
 */
public class NanoCollapsibleSection extends NanoPanel {
    private final String title; // Caption retained for the collapsible section header.
    private final NanoButton header; // Owned header controlling expansion and section focus.
    private final NanoPanel body = new NanoPanel(); // Owned content container shown only while expanded.
    private final ChangeSignal changes = new ChangeSignal(); // Synchronous listeners notified after user-visible state changes.
    private boolean expanded = true; // Whether the section body is currently visible.

    /**
     * Builds the control and its owned children from the supplied configuration.
     *
     * @param title displayed header caption
     * @param content fresh unattached content node owned by the section after attachment
     */
    public NanoCollapsibleSection(String title, UINode content) {
        this.title = Objects.requireNonNull(title);
        Objects.requireNonNull(content);
        if (content.getParent() != null) throw new IllegalArgumentException("Content already has a parent");
        header = new NanoButton(title) {
            @Override
            public void onKeyPress(KeyPressEvent event) {
                if (isDisabled()) return;
                if (event.getKey() == Keyboard.LEFT || event.getKey() == Keyboard.RIGHT) {
                    expanded(event.getKey() == Keyboard.RIGHT);
                    event.consume();
                } else super.onKeyPress(event);
            }
        }.action(button -> expanded(!expanded));
        header.leftAligned(true);
        header.setStyle(NanoButton.PADDING_X_KEY, 24f);
        header.add(new NanoDisclosureIndicator(this::isExpanded));
        getLayout()
                .column()
                .noShrink();
        header.setStyleName("section-header");
        header.getLayout()
                .widthPercent(100)
                .noShrink();
        body.getLayout()
                .widthPercent(100)
                .minHeight(0)
                .noShrink();
        content.getLayout()
                .widthPercent(100);
        body.add(content);
        super.add(header);
        super.add(body);
    }

    /**
     * Reports whether the section body is currently expanded.
     *
     * @return reports whether the section body is currently expanded
     */
    public boolean isExpanded() {
        return expanded;
    }
    /**
     * Borrows the owned header control for styling or interaction configuration.
     *
     * @return the owned header control for styling or interaction configuration
     */
    public NanoButton getHeader() {
        return header;
    }
    /**
     * Replaces the synchronous user-change callback without emitting an initial notification.
     *
     * @param listener nonnull synchronous application callback
     * @return removal action detaching this listener from subsequent changes
     */
    public Runnable onChange(Runnable listener) {
        return changes.subscribe(listener);
    }

    /**
     * Changes section expansion and synchronizes body visibility and focus.
     *
     * @param value replacement value applied to this control
     * @return this control for further configuration
     */
    public NanoCollapsibleSection expanded(boolean value) {
        if (value == expanded) return this;
        if (!value && getRoot() != null) {
            boolean focus = contains(getRoot().getFocused());
            if (contains(getRoot().getCaptured())) {
                getRoot()
                        .cancelInput();
                focus = true;
            }
            if (focus) getRoot()
                    .setFocusTo(header);
        }
        expanded = value;
        body.setVisible(value);
        if (value) body.getLayout()
                .heightAuto();
        else body.getLayout()
                .height(0);
        changes.fire();
        return this;
    }

    /**
     * Checks ancestry by identity without allocating a temporary path.
     *
     * @param node tree or UI node whose state is queried or changed
     * @return checks ancestry by identity without allocating a temporary path
     */
    private boolean contains(UINode node) {
        for (; node != null; node = node.getParent()) if (node == body) return true;
        return false;
    }

    @Override
    public void update(float delta) {
        header.update(delta);
        if (expanded) body.update(delta);
    }
}
