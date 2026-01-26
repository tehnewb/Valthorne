import valthorne.Application;
import valthorne.JGL;
import valthorne.Window;
import valthorne.camera.OrthographicCamera2D;
import valthorne.graphics.font.FontData;
import valthorne.graphics.texture.*;
import valthorne.ui.Layout;
import valthorne.ui.UI;
import valthorne.ui.Value;
import valthorne.ui.ValueType;
import valthorne.ui.elements.Button;
import valthorne.ui.elements.Checkbox;
import valthorne.ui.elements.TextField;
import valthorne.ui.styles.ButtonStyle;
import valthorne.ui.styles.CheckboxStyle;
import valthorne.ui.styles.TextFieldStyle;
import valthorne.viewport.ScreenViewport;
import valthorne.viewport.Viewport;

public class UITest implements Application {

    private UI ui;
    private Viewport viewport;
    private OrthographicCamera2D camera2D;
    private TextureDrawable background;

    @Override
    public void init() {
        viewport = new ScreenViewport(Window.getWidth(), Window.getHeight());
        viewport.setSize(Window.getWidth(), Window.getHeight());
        viewport.setCamera(camera2D = new OrthographicCamera2D());

        ui = new UI();
        ui.setViewport(viewport);
        ui.setLayout(new Layout()
                .setHeight(ValueType.PERCENTAGE.of(1f))
                .setWidth(ValueType.PERCENTAGE.of(1f))
                .setX(ValueType.PIXELS.of(0))
                .setY(ValueType.PIXELS.of(0)));
        ui.layout();

        this.background = new TextureDrawable(new Texture(TextureData.load("./assets/background.png")));

        TextureData buttonNormal = TextureData.load("./assets/ui/button.png");
        TextureData buttonDisabled = TextureData.load("./assets/ui/button-disabled.png");
        TextureData buttonHovered = TextureData.load("./assets/ui/button-hovered.png");
        TextureData buttonPressed = TextureData.load("./assets/ui/button-pressed.png");

        TextureData tfNormal = TextureData.load("./assets/ui/textfield.png");
        TextureData tfFocused = TextureData.load("./assets/ui/textfield-focused.png");

        TextureData checkboxBackground = TextureData.load("./assets/ui/checkbox.png");
        TextureData checkboxCheckmark = TextureData.load("./assets/ui/checkbox-checkmark.png");
        TextureData checkboxHovered = TextureData.load("./assets/ui/checkbox-hovered.png");

        FontData font = FontData.load("./assets/ui/font.otf", 26, 0, 254);

        ButtonStyle buttonStyle = ButtonStyle.of()
                .background(new NinePatchDrawable(new NinePatchTexture(buttonNormal, 6, 6, 6, 6)))
                .disabled(new NinePatchDrawable(new NinePatchTexture(buttonDisabled, 6, 6, 6, 6)))
                .hovered(new NinePatchDrawable(new NinePatchTexture(buttonHovered, 6, 6, 6, 6)))
                .pressed(new NinePatchDrawable(new NinePatchTexture(buttonPressed, 6, 6, 6, 6))).fontData(font);

        TextFieldStyle textFieldStyle = TextFieldStyle.of()
                .fontData(font)
                .background(new NinePatchDrawable(new NinePatchTexture(tfNormal, 3, 3, 3, 3)))
                .focused(new NinePatchDrawable(new NinePatchTexture(tfFocused, 3, 3, 3, 3)));

        CheckboxStyle checkboxStyle = CheckboxStyle.of()
                .background(new NinePatchDrawable(new NinePatchTexture(checkboxBackground, 4, 4, 4, 4)))
                .hovered(new NinePatchDrawable(new NinePatchTexture(checkboxHovered, 4, 4, 4, 4)))
                .checkmark(new NinePatchDrawable(new NinePatchTexture(checkboxCheckmark, 2, 2, 2, 2)));

        TextField username = new TextField("Username", textFieldStyle, e -> {});
        TextField password = new TextField("Password", textFieldStyle, e -> {});
        password.setMasking(true);

        Button button = new Button("Login", buttonStyle);
        Checkbox checkbox = new Checkbox(checkboxStyle);
        checkbox.setSize(16, 16);

        username.setLayout(new Layout()
                .setHeight(Value.pixels(40f))
                .setWidth(Value.percentage(0.25f))
                .setX(Value.percentage(0.25f + 0.25f / 2f))
                .setY(Value.percentage(0.5f)));

        password.setLayout(new Layout()
                .setHeight(Value.pixels(40f))
                .setWidth(Value.percentage(0.25f))
                .setX(Value.percentage(0.25f + 0.25f / 2f))
                .setY(Value.percentage(0.4f)));

        button.setAction(b -> {
            System.out.println("Username/Password: " + username.getText() + "/" + password.getText());
            System.out.println("Remember me? " + checkbox.isChecked());
        });

        ui.addElement(username);
        ui.addElement(password);

        Window.addWindowResizeListener(event -> {
            viewport.update(event.getNewWidth(), event.getNewHeight());
            ui.setSize(event.getNewWidth(), event.getNewHeight());
        });
    }

    @Override
    public void render() {
        viewport.render(() -> background.draw(0, 0, Window.getWidth(), Window.getHeight()));
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

    public static void main(String[] args) {
        JGL.init(new UITest(), "UI Layout Test", 1280, 720);
    }
}
