package valthorne.graphics.lighting2d;

import java.util.Arrays;
import java.util.Objects;

/** Instance of a shared alpha silhouette. Transform and frame changes reuse endpoint storage.
 * Like other 2D occluders this blocks light in XY, not a projected 3D ground shadow.
 */
public class AlphaOccluder2D extends Occluder2D {
    private AlphaShadowShape2D shape;
    private float ax=Float.NaN, ay, bx, by, cx, cy;
    public AlphaOccluder2D(AlphaShadowShape2D shape) { setShape(shape); setTransform(0,0,1,1,0,0,0); }
    public final AlphaShadowShape2D getShape() { return shape; }
    public final AlphaOccluder2D setShape(AlphaShadowShape2D value) {
        Objects.requireNonNull(value,"shape");
        if(shape!=value) {
            shape=value;
            float previousX=ax; ax=Float.NaN;
            if(Float.isFinite(previousX))setBasis(previousX,ay,bx,by,cx,cy);
        }
        return this;
    }
    /** Sets the same scaled-size, world-unit pivot and clockwise-degree convention as Sprite.
     * Negative width/height mirror the instance. Zero extent produces no obstruction.
     */
    public AlphaOccluder2D setTransform(float x,float y,float width,float height,float degrees,float originX,float originY) {
        PointLight2D.finite(x);PointLight2D.finite(y);PointLight2D.finite(width);PointLight2D.finite(height);
        PointLight2D.finite(degrees);PointLight2D.finite(originX);PointLight2D.finite(originY);
        float sin=(float)Math.sin(Math.toRadians(-degrees)), cos=(float)Math.cos(Math.toRadians(-degrees));
        setBasis(x+originX-originX*cos+originY*sin,y+originY-originX*sin-originY*cos,
                width*cos,width*sin,-height*sin,height*cos);
        return this;
    }
    final void setBasis(float a,float b,float c,float d,float e,float f) {
        if(ax==a && ay==b && bx==c && by==d && cx==e && cy==f)return;
        ax=a;ay=b;bx=c;by=d;cx=e;cy=f;
        coordinateCount=c*f-d*e==0?0:shape.edges.length;
        if(vertices.length<coordinateCount)vertices=Arrays.copyOf(vertices,coordinateCount);
        minX=minY=Float.POSITIVE_INFINITY;maxX=maxY=Float.NEGATIVE_INFINITY;
        for(int i=0;i<coordinateCount;i+=2) {
            float u=shape.edges[i], v=shape.edges[i+1];
            float px=a+u*c+v*e, py=b+u*d+v*f;
            vertices[i]=px;vertices[i+1]=py;
            minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py);
        }
        if(coordinateCount==0)minX=maxX=a; // Keep empty shapes safe for the spatial index.
        if(coordinateCount==0)minY=maxY=b;
        revision++;
    }
}
