import valthorne.Application;
import valthorne.JGL;
import valthorne.Window;
import valthorne.camera.OrthographicCamera2D;
import valthorne.graphics.font.FontData;
import valthorne.graphics.texture.*;
import valthorne.ui.Layout;
import valthorne.ui.UI;
import valthorne.ui.Value;
import valthorne.ui.elements.Button;
import valthorne.ui.elements.Checkbox;
import valthorne.ui.elements.Label;
import valthorne.ui.elements.TextField;
import valthorne.ui.flex.AlignItems;
import valthorne.ui.flex.FlexBox;
import valthorne.ui.flex.FlexDirection;
import valthorne.ui.flex.JustifyContent;
import valthorne.ui.styles.ButtonStyle;
import valthorne.ui.styles.CheckboxStyle;
import valthorne.ui.styles.LabelStyle;
import valthorne.ui.styles.TextFieldStyle;
import valthorne.viewport.ScreenViewport;
import valthorne.viewport.Viewport;

public class UITest implements Application {

    private UI ui;
    private Viewport viewport;
    private OrthographicCamera2D camera2D;
    private TextureDrawable background;

    public static void main(String[] args) {
        JGL.init(new UITest(), "UI Flex Layout Test (Justify + Align)", 1280, 720);
    }

    @Override
    public void init() {
        viewport = new ScreenViewport(Window.getWidth(), Window.getHeight());
        viewport.setSize(Window.getWidth(), Window.getHeight());
        viewport.setCamera(camera2D = new OrthographicCamera2D());

        ui = new UI();
        ui.setViewport(viewport);
        ui.setLayout(new Layout()
                .setHeight(Value.percentage(1f))
                .setWidth(Value.percentage(1f))
                .setX(Value.pixels(0))
                .setY(Value.pixels(0)));

        background = new TextureDrawable(new Texture(TextureData.load("./assets/background.png")));

        TextureData buttonNormal = TextureData.load("./assets/ui/button.png");
        TextureData buttonDisabled = TextureData.load("./assets/ui/button-disabled.png");
        TextureData buttonHovered = TextureData.load("./assets/ui/button-hovered.png");
        TextureData buttonPressed = TextureData.load("./assets/ui/button-pressed.png");

        TextureData tfNormal = TextureData.load("./assets/ui/textfield.png");
        TextureData tfFocused = TextureData.load("./assets/ui/textfield-focused.png");

        TextureData checkboxBackground = TextureData.load("./assets/ui/checkbox.png");
        TextureData checkboxCheckmark = TextureData.load("./assets/ui/checkbox-checkmark.png");
        TextureData checkboxHovered = TextureData.load("./assets/ui/checkbox-hovered.png");

        FontData font = FontData.load("./assets/ui/font.otf", 24, 0, 254);
        FontData font2 = FontData.load("./assets/ui/title.otf", 64, 0, 254);


        ButtonStyle buttonStyle = ButtonStyle.of()
                .background(new NinePatchDrawable(new NinePatchTexture(buttonNormal, 6, 6, 6, 6)))
                .disabled(new NinePatchDrawable(new NinePatchTexture(buttonDisabled, 6, 6, 6, 6)))
                .hovered(new NinePatchDrawable(new NinePatchTexture(buttonHovered, 6, 6, 6, 6)))
                .pressed(new NinePatchDrawable(new NinePatchTexture(buttonPressed, 6, 6, 6, 6)))
                .fontData(font);

        TextFieldStyle textFieldStyle = TextFieldStyle.of()
                .fontData(font)
                .background(new NinePatchDrawable(new NinePatchTexture(tfNormal, 3, 3, 3, 3)))
                .focused(new NinePatchDrawable(new NinePatchTexture(tfFocused, 3, 3, 3, 3)));

        CheckboxStyle checkboxStyle = CheckboxStyle.of()
                .background(new NinePatchDrawable(new NinePatchTexture(checkboxBackground, 4, 4, 4, 4)))
                .hovered(new NinePatchDrawable(new NinePatchTexture(checkboxHovered, 4, 4, 4, 4)))
                .checkmark(new NinePatchDrawable(new NinePatchTexture(checkboxCheckmark, 2, 2, 2, 2)));

        LabelStyle labelStyle = LabelStyle.of().fontData(font);
        LabelStyle labelStyle2 = LabelStyle.of().fontData(font2);

        TextField username = new TextField("Username", textFieldStyle, e -> {});
        TextField password = new TextField("Password", textFieldStyle, e -> {});
        password.setMasking(true);

        Checkbox checkbox = new Checkbox(checkboxStyle);

        Label label = new Label("View Password", labelStyle);
        Label title = new Label("Valthorne", labelStyle2);
        title.setLayout(new Layout().setHeight(Value.pixels(200f)));

        // Toggle password visibility
        checkbox.setAction(c -> password.setMasking(!c.isChecked()));

        Button button = new Button("Login", buttonStyle);
        button.setAction(b -> {
            System.out.println("Username/Password: " + username.getText() + "/" + password.getText());
            System.out.println("View Password? " + checkbox.isChecked());
        });

        FlexBox form = new FlexBox()
                .setFlexDirection(FlexDirection.COLUMN)
                .setGap(14f)
                .setWrap(false)
                .setJustifyContent(JustifyContent.CENTER) // main axis (vertical) distribution
                .setAlignItems(AlignItems.CENTER);        // cross axis (horizontal) alignment

        form.setLayout(new Layout()
                .setWidth(Value.percentage(0.55f))
                .setHeight(Value.percentage(0.65f))
                .setX(Value.percentage(0.50f - 0.55f / 2f))
                .setY(Value.percentage(0.50f - 0.65f / 2f))
                .setPadding(Value.pixels(16f))
                .setMargins(Value.pixels(24f)));

        // Username: narrower to clearly see align-items center
        username.setLayout(new Layout()
                .setWidth(Value.percentage(0.40f))
                .setHeight(Value.pixels(40f)));

        // Password: full width to show different cross-size behavior
        password.setLayout(new Layout()
                .setWidth(Value.percentage(0.4f))
                .setHeight(Value.pixels(40f)));

        FlexBox viewPasswordRow = new FlexBox()
                .setFlexDirection(FlexDirection.ROW)
                .setGap(10f)
                .setWrap(false)
                .setJustifyContent(JustifyContent.CENTER)
                .setAlignItems(AlignItems.CENTER);

        viewPasswordRow.setLayout(new Layout()
                .setWidth(Value.percentage(0.4f))
                .setHeight(Value.pixels(24f)));

        checkbox.setLayout(new Layout()
                .setWidth(Value.pixels(17f))
                .setHeight(Value.pixels(17f)));

        label.setLayout(new Layout()
                .setHeight(Value.pixels(16f)));

        viewPasswordRow.addElement(checkbox);
        viewPasswordRow.addElement(label);

        button.setLayout(new Layout()
                .setWidth(Value.percentage(0.25f))
                .setHeight(Value.pixels(34f)));

        // Build hierarchy
        form.addElement(title);
        form.addElement(username);
        form.addElement(password);
        form.addElement(viewPasswordRow);
        form.addElement(button);

        ui.addElement(form);

        // Initial layout after tree built
        ui.layout();

        Window.addWindowResizeListener(event -> {
            viewport.update(event.getNewWidth(), event.getNewHeight());
            ui.setSize(event.getNewWidth(), event.getNewHeight());
            ui.layout();
        });
    }

    @Override
    public void render() {
        ui.draw();
    }

    @Override
    public void update(float delta) {
        Window.setTitle("FPS: " + JGL.getFramesPerSecond());
        camera2D.setCenter(viewport.getWorldWidth() * 0.5f, viewport.getWorldHeight() * 0.5f);
        ui.update(delta);
    }

    @Override
    public void dispose() {
    }
}
