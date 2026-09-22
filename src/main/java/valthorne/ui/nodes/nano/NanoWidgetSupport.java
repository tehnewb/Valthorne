package valthorne.ui.nodes.nano;

/** Internal layout surfaces shared by the composite NanoVG widgets. */
final class NanoWidgetSupport {
    private NanoWidgetSupport() {}

    /** Creates a transparent layout group, styled independently of ordinary panels. */
    static NanoPanel panel() {
        NanoPanel panel = new NanoPanel();
        var clear = new valthorne.graphics.Color(0x00000000);
        panel.backgroundColor(clear).hoverBackgroundColor(clear).focusedBackgroundColor(clear)
                .pressedBackgroundColor(clear).disabledBackgroundColor(clear).borderWidth(0);
        panel.setStyleName("widget-layout");
        return panel;
    }

    /** Creates a transparent clipping viewport for labels and window content. */
    static NanoScrollPanel viewport() {
        NanoScrollPanel panel = new NanoScrollPanel();
        panel.setStyleName("widget-layout");
        return panel;
    }
}
