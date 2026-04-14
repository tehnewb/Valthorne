package valthorne.graphics.radiance;

import valthorne.graphics.texture.Texture;

public final class RadianceSceneBuffer {

    private final RadianceRenderTarget target;

    public RadianceSceneBuffer(int width, int height) {
        this.target = new RadianceRenderTarget(width, height, true, false);
    }

    public void begin() {
        target.begin();
    }

    public void end() {
        target.end();
    }

    public void clear() {
        clear(0f, 0f, 0f, 0f);
    }

    public void clear(float r, float g, float b, float a) {
        target.clear(r, g, b, a);
    }

    public void resize(int width, int height) {
        target.resize(width, height);
    }

    public int getWidth() {
        return target.getWidth();
    }

    public int getHeight() {
        return target.getHeight();
    }

    public int getTextureID() {
        return target.getTextureID();
    }

    public Texture getTexture() {
        return target.getTexture();
    }

    public void dispose() {
        target.dispose();
    }
}
