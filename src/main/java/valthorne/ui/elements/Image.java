package valthorne.ui.elements;

import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureData;
import valthorne.ui.Element;

/**
 * The Image class represents a graphical element that is rendered using a texture.
 * It extends the Element class and provides functionality to update, draw, and manage
 * its position and size. The Image class wraps an instance of a Texture for rendering.
 *
 * @author Albert Beaupre
 * @since January 31st, 2026
 */
public class Image extends Element {

    private Texture texture;

    public Image(Texture texture) {
        this.texture = texture;
    }

    @Override
    public void update(float delta) {

    }

    @Override
    public void draw() {
        texture.draw();
    }

    @Override
    public void setPosition(float x, float y) {
        super.setPosition(x, y);
        texture.setPosition(x, y);
    }

    @Override
    public void setSize(float width, float height) {
        super.setSize(width, height);
        texture.setSize(width, height);
    }

    public Image setTexture(TextureData data) {
        this.texture = new Texture(data);
        return this;
    }

    public Texture getTexture() {
        return texture;
    }
}
