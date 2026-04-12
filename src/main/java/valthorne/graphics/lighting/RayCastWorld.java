package valthorne.graphics.lighting;

public interface RayCastWorld {

    RayCastHit rayCast(float startX, float startY, float endX, float endY);
}