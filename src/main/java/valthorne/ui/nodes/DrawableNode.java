package valthorne.ui.nodes;

import valthorne.graphics.Drawable;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.UINode;

public class DrawableNode extends UINode {

    private Drawable drawable;

    public DrawableNode(Drawable drawable) {
        this.drawable = drawable;
    }

    @Override
    public void onCreate() {

    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void update(float delta) {

    }

    @Override
    public void draw(TextureBatch batch) {
        drawable.draw(batch, getRenderX(), getRenderY(), getWidth(), getHeight());
    }

    public DrawableNode drawable(Drawable drawable) {
        this.drawable = drawable;
        return this;
    }

    public Drawable getDrawable() {
        return drawable;
    }
}
