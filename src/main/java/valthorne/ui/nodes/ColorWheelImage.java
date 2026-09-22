package valthorne.ui.nodes;

import java.nio.ByteBuffer;

import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.nanovg.NanoVG.*;

/** Cached, continuously sampled hue/saturation disc shared by both color pickers. */
public final class ColorWheelImage {
    private int image;
    private long context;
    private int width, height;
    private float brightness = Float.NaN;

    /** Draws a disc whose hue and saturation match the picker's pointer mapping. */
    public void draw(long vg, float x, float y, float width, float height, float radius, float value) {
        int pixelsWide = Math.max(1, Math.round(width));
        int pixelsHigh = Math.max(1, Math.round(height));
        if (image <= 0 || context != vg || this.width != pixelsWide || this.height != pixelsHigh
                || brightness != value) {
            ByteBuffer pixels = org.lwjgl.BufferUtils.createByteBuffer(pixelsWide * pixelsHigh * 4);
            float cx = width * .5f, cy = height * .5f;
            for (int py = 0; py < pixelsHigh; py++) {
                for (int px = 0; px < pixelsWide; px++) {
                    float dx = (px + .5f) * width / pixelsWide - cx;
                    float dy = (py + .5f) * height / pixelsHigh - cy;
                    float distance = (float) Math.hypot(dx, dy);
                    float alpha = Math.max(0, Math.min(1, radius - distance + .5f));
                    float saturation = Math.min(1, distance / radius);
                    float hue = (float) (Math.atan2(dy, dx) / (Math.PI * 2));
                    if (hue < 0) hue += 1;
                    float chroma = value * saturation;
                    float sector = hue * 6;
                    float secondary = chroma * (1 - Math.abs(sector % 2 - 1));
                    float red, green, blue;
                    if (sector < 1) { red = chroma; green = secondary; blue = 0; }
                    else if (sector < 2) { red = secondary; green = chroma; blue = 0; }
                    else if (sector < 3) { red = 0; green = chroma; blue = secondary; }
                    else if (sector < 4) { red = 0; green = secondary; blue = chroma; }
                    else if (sector < 5) { red = secondary; green = 0; blue = chroma; }
                    else { red = chroma; green = 0; blue = secondary; }
                    float base = value - chroma;
                    pixels.put((byte) Math.round((red + base) * 255));
                    pixels.put((byte) Math.round((green + base) * 255));
                    pixels.put((byte) Math.round((blue + base) * 255));
                    pixels.put((byte) Math.round(alpha * 255));
                }
            }
            pixels.flip();
            if (image > 0 && context == vg && this.width == pixelsWide && this.height == pixelsHigh) {
                nvgUpdateImage(vg, image, pixels);
            } else {
                dispose();
                image = nvgCreateImageRGBA(vg, pixelsWide, pixelsHigh, 0, pixels);
                context = vg;
            }
            this.width = pixelsWide;
            this.height = pixelsHigh;
            brightness = value;
        }
        if (image <= 0) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            NVGPaint paint = NVGPaint.calloc(stack);
            nvgImagePattern(vg, x, y, width, height, 0, image, 1, paint);
            nvgBeginPath(vg);
            nvgRect(vg, x, y, width, height);
            nvgFillPaint(vg, paint);
            nvgFill(vg);
        }
    }

    /** Releases the image while its NanoVG context is still alive. */
    public void dispose() {
        if (image > 0 && context != 0) nvgDeleteImage(context, image);
        image = 0;
        context = 0;
    }
}
