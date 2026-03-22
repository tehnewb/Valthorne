package valthorne.ui.nodes.nano;

import valthorne.ui.UIContainer;

public class NanoContainer extends UIContainer implements NanoNode {
    @Override
    public void draw(long vg) {
        for (int i = 0; i < size(); i++) {
            var child = get(i);
            if (child == null || !child.isVisible())
                continue;
            if (child instanceof NanoNode nanoChild)
                nanoChild.draw(vg);
        }
    }
}
