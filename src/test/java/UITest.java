import valthorne.Application;
import valthorne.JGL;
import valthorne.Window;
import valthorne.camera.OrthographicCamera2D;
import valthorne.graphics.font.FontData;
import valthorne.graphics.texture.*;
import valthorne.ui.Alignment;
import valthorne.ui.Justify;
import valthorne.ui.UI;
import valthorne.ui.elements.*;
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
    private VerticalBox column;
    private HorizontalBox row;

    @Override
    public void init() {
        viewport = new ScreenViewport(Window.getWidth(), Window.getHeight());
        viewport.setCamera(camera2D = new OrthographicCamera2D());

        ui = new UI();
        ui.setViewport(viewport);

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

        FontData font = FontData.load("./assets/ui/font.ttf", 12, 0, 254);

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
        checkbox.setSize(32, 32);

        username.setSize(200, 40);
        username.getLayout().setPadding(15);
        password.setSize(200, 40);
        password.getLayout().setPadding(15);
        button.setSize(120, 40);
        button.getLayout().setPadding(15);

        column = new VerticalBox();
        column.setSize(Window.getWidth(), 300);
        column.setPosition(0, 100);
        column.setAlignment(Alignment.CENTER_CENTER);
        column.setJustify(Justify.CENTER);

        row = new HorizontalBox();
        row.setSize(Window.getWidth(), 40);
        row.setJustify(Justify.CENTER);
        row.setAlignment(Alignment.CENTER_CENTER);
        row.addElement(button);
        row.addElement(checkbox);

        column.addElement(username);
        column.addElement(password);
        column.addElement(row);

        ui.addElement(column);
    }

    @Override
    public void render() {
        viewport.render(() -> {
            background.draw(0, 0, Window.getWidth(), Window.getHeight());
        });
        column.setSize(Window.getWidth(), 400);
        row.setSize(Window.getWidth(), 40);
        ui.draw();
    }

    @Override
    public void update(float delta) {
        Window.setTitle("FPS: " + JGL.getFramesPerSecond());
        viewport.update(Window.getWidth(), Window.getHeight());
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
