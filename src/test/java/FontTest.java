import valthorne.Application;
import valthorne.JGL;
import valthorne.graphics.Color;
import valthorne.graphics.font.Font;
import valthorne.graphics.font.FontData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FontTest implements Application {

    private Font font;

    @Override
    public void init() {
        try {
            font = new Font(FontData.load(Files.readAllBytes(Paths.get("./src/test/resources/test-font.ttf")), 12, 0, 254));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        font.setColor(Color.BLUE);
        font.setText("""
                
                Lorem ipsum dolor sit amet, consectetur adipiscing elit. Morbi tristique semper vulputate. Nam in pretium sem, sed feugiat sem. Quisque pellentesque sodales ante, nec posuere nisi lacinia ac. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos. Phasellus viverra dui tortor, vel posuere metus consectetur cursus. Nam risus quam, placerat a diam ac, faucibus fermentum arcu. Mauris scelerisque lobortis blandit.
                
                Sed sit amet ante porta lacus feugiat tincidunt. Nunc ac mattis turpis. Orci varius natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Nulla facilisi. Suspendisse tristique dignissim elit vitae volutpat. Etiam semper in nunc in mollis. Nulla eu convallis enim, in maximus diam.
                
                Pellentesque molestie, ipsum ac mollis cursus, ex metus volutpat diam, sed porta metus urna in dui. Pellentesque lobortis, sapien eget bibendum consectetur, libero tellus elementum enim, in lacinia erat elit in metus. Nullam egestas blandit velit. Sed rhoncus volutpat orci, a tempor dolor dignissim vel. Nunc euismod libero metus, commodo malesuada lorem dictum a. Nulla vestibulum mattis mattis. Maecenas facilisis sollicitudin neque, ut porta turpis mollis nec. Vivamus sodales congue est, nec iaculis augue viverra sed. Mauris quis euismod quam. Proin felis dui, feugiat sit amet felis fermentum, dictum sodales eros. Pellentesque non fermentum enim, in placerat ligula. Mauris pellentesque tellus sed erat sollicitudin pulvinar. Aenean gravida vitae odio mattis consectetur. Donec et turpis nec sapien viverra scelerisque non vitae mauris. Fusce scelerisque massa feugiat sem viverra hendrerit.
                
                Nullam interdum orci sed felis ultrices dapibus. Donec porttitor porta nunc, et sodales ipsum elementum et. Sed at fringilla quam, vitae consectetur libero. Nullam vel nisl neque. Sed vitae placerat eros. Sed id porttitor erat. Donec id metus id augue porta ultricies. Duis gravida mi eget tortor egestas, mollis lacinia libero hendrerit. Curabitur rutrum commodo sapien, ac posuere ex sollicitudin non. Ut vitae quam eu lorem vehicula blandit. Vestibulum porta dapibus urna sit amet porttitor. Donec sed purus nec est bibendum vestibulum. Proin vitae luctus arcu, quis pharetra arcu. Integer finibus velit nibh, at ornare neque viverra ut. Nullam in nunc eget leo laoreet scelerisque vel vel tortor. Aliquam erat volutpat.
                
                Ut euismod, sem at mollis suscipit, nibh nisi vehicula ipsum, eget varius sapien ex id nunc. Aliquam erat volutpat. Nunc sed neque velit. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos. Cras placerat laoreet diam sed finibus. Quisque condimentum maximus magna. Nunc ullamcorper, ipsum id lacinia euismod, ante ex euismod nisl, ultricies euismod sem purus sed velit. Fusce sit amet sapien risus.
                """);
    }

    @Override
    public void render() {
        font.draw();
    }

    @Override
    public void update(float delta) {
    }

    @Override
    public void dispose() {
        font.dispose();
    }

    public static void main(String[] args) {
        JGL.init(new FontTest(), "Font Test", 1280, 720);
    }
}
