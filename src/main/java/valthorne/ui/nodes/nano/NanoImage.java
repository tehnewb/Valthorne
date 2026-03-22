package valthorne.ui.nodes.nano;

import org.lwjgl.nanovg.NVGPaint;
import valthorne.graphics.texture.TextureBatch;
import valthorne.graphics.texture.TextureData;
import valthorne.ui.UINode;

import static org.lwjgl.nanovg.NanoVG.*;

public class NanoImage extends UINode implements NanoNode {

    private TextureData texture;
    private int imageHandle = -1;
    private long imageContext;

    public NanoImage(TextureData texture) {
        this.texture = texture;
    }

    @Override
    public void onCreate() {
        if (texture != null)
            getLayout().width(texture.width()).height(texture.height()).fill();
    }

    @Override
    public void onDestroy() {
        deleteImage();
    }

    @Override
    public void update(float delta) {
    }

    @Override
    public void draw(TextureBatch batch) {
    }

    @Override
    public void draw(long vg) {
        if (texture == null || vg == 0L)
            return;

        ensureImage(vg);

        if (imageHandle < 0)
            return;

        float x = getAbsoluteX();
        float y = getAbsoluteY();
        float width = getWidth();
        float height = getHeight();

        if (width <= 0f || height <= 0f)
            return;

        try (NVGPaint paint = NVGPaint.calloc()) {
            nvgImagePattern(vg, x, y, width, height, 0f, imageHandle, 1f, paint);
            nvgBeginPath(vg);
            nvgRect(vg, x, y, width, height);
            nvgFillPaint(vg, paint);
            nvgFill(vg);
        }
    }

    public TextureData getTexture() {
        return texture;
    }

    public NanoImage texture(TextureData texture) {
        if (this.texture == texture)
            return this;

        deleteImage();
        this.texture = texture;

        if (texture != null)
            getLayout().width(texture.width()).height(texture.height()).fill();

        markLayoutDirty();
        return this;
    }

    private void ensureImage(long vg) {
        if (imageHandle >= 0 && imageContext == vg)
            return;

        deleteImage();

        imageHandle = nvgCreateImageRGBA(vg, texture.width(), texture.height(), 0, texture.buffer());
        imageContext = vg;
    }

    private void deleteImage() {
        if (imageHandle >= 0 && imageContext != 0L)
            nvgDeleteImage(imageContext, imageHandle);

        imageHandle = -1;
        imageContext = 0L;
    }
}