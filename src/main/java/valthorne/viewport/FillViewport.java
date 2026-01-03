package valthorne.viewport;

public class FillViewport extends Viewport {
    public FillViewport(float worldWidth, float worldHeight) {
        super(worldWidth, worldHeight);
    }

    @Override
    public void update(int screenWidth, int screenHeight) {
        float worldAspect = worldWidth / worldHeight;
        float screenAspect = (float) screenWidth / (float) screenHeight;

        if (screenAspect > worldAspect) {
            this.width = screenWidth;
            this.height = (int) (screenWidth / worldAspect);
            this.x = 0;
            this.y = (screenHeight - this.height) / 2;
        } else {
            this.height = screenHeight;
            this.width = (int) (screenHeight * worldAspect);
            this.y = 0;
            this.x = (screenWidth - this.width) / 2;
        }

        projectionMatrix.ortho(0, worldWidth, 0, worldHeight, -1f, 1f);
    }
}
