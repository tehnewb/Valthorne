package valthorne.ui.nodes;

import valthorne.graphics.Drawable;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.UIContainer;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

/**
 * <p>
 * {@code Panel} is the basic drawable container node in the Valthorne UI system.
 * It extends {@link UIContainer}, which means it can hold and manage child
 * {@link valthorne.ui.UINode} instances while also optionally rendering a styled
 * background behind them.
 * </p>
 *
 * <p>
 * This class is intentionally simple and acts as a foundational building block
 * for many other UI components. A panel does not define any custom input behavior
 * on its own. Instead, it provides a themed rectangular surface that can contain
 * child nodes and participate in the layout, update, and draw lifecycle shared by
 * all UI nodes.
 * </p>
 *
 * <p>
 * The panel's main visual feature is its optional background drawable, resolved
 * from the panel's style using {@link #BACKGROUND_KEY}. If a theme and resolved
 * style are available, the background is drawn to the panel's current render
 * bounds before any children are rendered.
 * </p>
 *
 * <p>
 * Because {@code Panel} extends {@link UIContainer}, it is commonly used as:
 * </p>
 *
 * <ul>
 *     <li>a generic grouping container</li>
 *     <li>a background surface for other controls</li>
 *     <li>a base class for more specialized UI widgets</li>
 *     <li>a layout wrapper for content blocks</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Panel panel = new Panel();
 *
 * panel.getLayout()
 *      .width(300)
 *      .height(200)
 *      .padding(12);
 *
 * panel.add(new Label("Settings"));
 * panel.add(new Button("Apply"));
 *
 * panel.update(delta);
 * panel.draw(batch);
 * }</pre>
 *
 * <p>
 * This example demonstrates the complete intended use of the class: creating a
 * panel, assigning layout values, adding child nodes, updating it, and drawing it.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 13th, 2026
 */
public class Panel extends UIContainer {

    /**
     * Style key used to resolve the panel background drawable.
     */
    public static final StyleKey<Drawable> BACKGROUND_KEY = StyleKey.of("background", Drawable.class);

    /**
     * <p>
     * Updates this panel and all of its children.
     * </p>
     *
     * <p>
     * This implementation delegates directly to the superclass update logic so
     * child nodes continue to receive their normal update calls.
     * </p>
     *
     * @param delta the elapsed frame time in seconds
     */
    @Override
    public void update(float delta) {
        super.update(delta);
    }

    /**
     * <p>
     * Draws this panel and all of its children.
     * </p>
     *
     * <p>
     * If the panel has an assigned theme and a resolved style, this method first
     * attempts to resolve a background drawable using {@link #BACKGROUND_KEY}.
     * If one exists, it is drawn across the panel's full render bounds. After that,
     * the container's children are drawn through the superclass implementation.
     * </p>
     *
     * @param batch the texture batch used for rendering
     */
    @Override
    public void draw(TextureBatch batch) {
        if (this.getTheme() != null) {

            ResolvedStyle style = this.getStyle();
            if (style != null) {

                Drawable background = style.get(BACKGROUND_KEY);
                if (background != null)
                    background.draw(batch, getRenderX(), getRenderY(), getWidth(), getHeight());
            }
        }
        super.draw(batch);
    }
}