package valthorne.math.physics;
import org.joml.Vector3f;
import valthorne.graphics.model.Model3D;
/** Browser collision definition. Full extents and Z-up axes match the desktop API. */
public final class CollisionShape3D {
    final int type;
    final float[] data;
    private CollisionShape3D(int type,float... data){this.type=type;this.data=data;}
    public static CollisionShape3D box(float width,float depth,float height){
        PhysicsMath3D.positive(width,"width");PhysicsMath3D.positive(depth,"depth");PhysicsMath3D.positive(height,"height");
        return new CollisionShape3D(0,width,depth,height);
    }
    public static CollisionShape3D sphere(float radius){PhysicsMath3D.positive(radius,"radius");return new CollisionShape3D(1,radius);}
    public static CollisionShape3D capsule(float radius,float height){
        PhysicsMath3D.positive(radius,"radius");PhysicsMath3D.positive(height,"height");
        if(height<2*radius)throw new IllegalArgumentException("Capsule height must be at least twice its radius");
        return height==2*radius?sphere(radius):new CollisionShape3D(2,radius,height);
    }
    public static CollisionShape3D cylinder(float radius,float height){
        PhysicsMath3D.positive(radius,"radius");PhysicsMath3D.positive(height,"height");return new CollisionShape3D(3,radius,height);
    }
    public static CollisionShape3D convexHull(Vector3f... vertices){
        if(vertices.length<4)throw new IllegalArgumentException("A convex hull needs at least four vertices");
        return new CollisionShape3D(4,pack(vertices));
    }
    public static CollisionShape3D convexHull(Model3D model){return convexHull(vertices(model));}
    public static CollisionShape3D mesh(Model3D model){return new CollisionShape3D(5,pack(vertices(model)));}
    public boolean isStaticOnly(){return type==5;}
    private static float[] pack(Vector3f[] vertices){
        float[] data=new float[vertices.length*3];
        for(int i=0;i<vertices.length;i++){Vector3f v=PhysicsMath3D.check(vertices[i]);data[i*3]=v.x;data[i*3+1]=v.y;data[i*3+2]=v.z;}return data;
    }
    private static Vector3f[] vertices(Model3D model) {
        java.util.Objects.requireNonNull(model, "model");
        if (model.getTriangleCount() == 0) throw new IllegalArgumentException("Model must contain triangles");
        Vector3f[] vertices = new Vector3f[model.getTriangleCount() * 3];
        int i = 0;
        for (Model3D.Triangle triangle : model.getTriangles()) {
            vertices[i++] = triangle.getA();
            vertices[i++] = triangle.getB();
            vertices[i++] = triangle.getC();
        }
        return vertices;
    }

}
