package valthorne.viewport;

public class StretchViewport extends Viewport {
    public StretchViewport(float worldWidth, float worldHeight) {
        super(worldWidth, worldHeight);
    }

    @Override
    public void update(int screenWidth, int screenHeight) {
        this.x = 0;
        this.y = 0;
        this.width = screenWidth;
        this.height = screenHeight;

        projectionMatrix.ortho(0, worldWidth, 0, worldHeight, -1f, 1f);
    }
}
