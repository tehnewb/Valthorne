package valthorne.ui.nodes.nano;

import org.lwjgl.nanovg.NVGColor;
import valthorne.Keyboard;
import valthorne.event.events.*;
import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.NanoUtility;
import valthorne.ui.UINode;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.lwjgl.nanovg.NanoVG.*;

public class NanoComboBox<T> extends NanoContainer {

    private static final StyleKey<Color> CELL_BACKGROUND_COLOR = StyleKey.of("nano.combobox.cellBackgroundColor", Color.class, new Color(0xFF242424));
    private static final StyleKey<Color> CELL_HOVER_COLOR = StyleKey.of("nano.combobox.cellColor", Color.class, Color.WHITE);
    private static final StyleKey<Color> CELL_DISABLED_COLOR = StyleKey.of("nano.combobox.cellColor", Color.class, Color.WHITE);
    private static final StyleKey<Color> CELL_SELECTED_COLOR = StyleKey.of("nano.combobox.cellColor", Color.class, Color.WHITE);
    private static final StyleKey<Color> CELL_TEXT_COLOR = StyleKey.of("nano.combobox.cellTextColor", Color.class, Color.BLACK);
    private static final StyleKey<Color> CELL_HOVER_TEXT_COLOR = StyleKey.of("nano.combobox.cellTextColor", Color.class, Color.BLACK);
    private static final StyleKey<Color> CELL_SELECTED_TEXT_COLOR = StyleKey.of("nano.combobox.cellTextColor", Color.class, Color.BLACK);
    private static final StyleKey<Color> CELL_DISABLED_TEXT_COLOR = StyleKey.of("nano.combobox.cellTextColor", Color.class, Color.BLACK);
    private static final StyleKey<Color> CELL_BORDER_COLOR = StyleKey.of("nano.combobox.cellBorderColor", Color.class, Color.WHITE);
    private static final StyleKey<Integer> BORDER_RADIUS = StyleKey.of("nano.combobox.borderRadius", Integer.class, 6);
    private static final StyleKey<Float> FONT_SIZE = StyleKey.of("nano.combobox.fontSize", Float.class, 16f);
    private static final StyleKey<String> FONT_NAME = StyleKey.of("nano.combobox.fontName", String.class, "default");

    private static final StyleKey<Color> BACKGROUND_COLOR = StyleKey.of("nano.combobox.backgroundColor", Color.class, new Color(0xFF242424));
    private static final StyleKey<Color> HOVER_BACKGROUND_COLOR = StyleKey.of("nano.combobox.hoverBackgroundColor", Color.class, new Color(0xFF323232));
    private static final StyleKey<Color> FOCUSED_BACKGROUND_COLOR = StyleKey.of("nano.combobox.focusedBackgroundColor", Color.class, new Color(0xFF3A3A3A));
    private static final StyleKey<Color> BORDER_COLOR = StyleKey.of("nano.combobox.borderColor", Color.class, new Color(0xFF555555));
    private static final StyleKey<Color> HOVER_BORDER_COLOR = StyleKey.of("nano.combobox.hoverBorderColor", Color.class, new Color(0xFF777777));
    private static final StyleKey<Color> FOCUSED_BORDER_COLOR = StyleKey.of("nano.combobox.focusedBorderColor", Color.class, new Color(0xFF7AA2FF));
    private static final StyleKey<Color> TEXT_COLOR = StyleKey.of("nano.combobox.textColor", Color.class, new Color(0xFFFFFFFF));
    private static final StyleKey<Color> HOVER_TEXT_COLOR = StyleKey.of("nano.combobox.hoverTextColor", Color.class, new Color(0xFFFFFFFF));


    @Override
    public void draw(long vg) {

        nvgBeginPath(vg);
        nvgRoundedRect(vg, getAbsoluteX(), getAbsoluteY(), getWidth(), getHeight(), getStyle().get(BORDER_RADIUS));
        super.draw(vg);

    }


    private static final class ComboCell extends UINode implements NanoNode {

        private Color backgroundColor = new Color(0xFF242424);
        private int borderRadius;
        private String fontName = "default";
        private float fontSize = 18f;

        @Override
        protected void applyLayout() {
            ResolvedStyle style = getStyle();

            if (style != null) {
                Color resolvedBackgroundColor = style.get(CELL_BACKGROUND_COLOR);
                Color resolvedHoverColor = style.get(CELL_HOVER_COLOR);
                Color resolvedDisabledColor = style.get(CELL_DISABLED_COLOR);
                Color resolvedSelectedColor = style.get(CELL_SELECTED_COLOR);
                Color resolvedTextColor = style.get(CELL_TEXT_COLOR);
                Color resolvedHoverTextColor = style.get(CELL_HOVER_TEXT_COLOR);
                Color resolvedSelectedTextColor = style.get(CELL_SELECTED_TEXT_COLOR);
                Color resolvedDisabledTextColor = style.get(CELL_DISABLED_TEXT_COLOR);
                Color resolvedBorderColor = style.get(CELL_BORDER_COLOR);


            }
        }

        @Override
        public void draw(long nanoHandle) {
            float x = getAbsoluteX();
            float y = getAbsoluteY();
            float width = getWidth();
            float height = getHeight();

            NVGColor bg = NanoUtility.color1(backgroundColor); // TODO add the colors later

            nvgSave(nanoHandle);
            nvgBeginPath(nanoHandle);
            nvgRoundedRect(nanoHandle, x, y, width, height, borderRadius);
            nvgFillColor(nanoHandle, bg);
            nvgFill(nanoHandle);
            nvgIntersectScissor(nanoHandle, x, y, width, height);
            nvgFontSize(nanoHandle, fontSize);
            nvgFontFace(nanoHandle, fontName);
            nvgTextAlign(nanoHandle, NVG_ALIGN_CENTER);
            nvgRestore(nanoHandle);
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

        }
    }
}