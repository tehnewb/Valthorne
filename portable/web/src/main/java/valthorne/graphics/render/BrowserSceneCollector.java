package valthorne.graphics.render;

import valthorne.graphics.model.Material3D;
import valthorne.graphics.model.Model3D;
import valthorne.graphics.scene.ModelInstance3D;
import valthorne.graphics.model.ObjModel3D;
import valthorne.graphics.scene.Scene3D;
import valthorne.graphics.scene.SceneNode3D;

import java.util.ArrayList;
import org.joml.Matrix4f;

/** Reusable scene traversal with lazy browser texture upload for imported models. */
final class BrowserSceneCollector {
    static final class Item {
        Model3D model;Material3D material;final Matrix4f transform=new Matrix4f();
        Model3D model(){return model;}Material3D material(){return material;}Matrix4f transform(){return transform;}
    }
    final ArrayList<Item> instances=new ArrayList<>();
    private final ArrayList<Matrix4f> transforms=new ArrayList<>();
    private final ArrayList<Material3D> materials=new ArrayList<>();
    private int count,transformCount,materialCount;
    BrowserSceneCollector capture(Scene3D scene){
        count=transformCount=materialCount=0;
        for(int i=0;i<scene.renderableCount();i++){
            var source=scene.renderableAt(i);if(!source.isRenderableVisible())continue;
            if(!(source instanceof ModelInstance3D model))throw new IllegalArgumentException("Filament requires triangle model instances");
            add(model.getModel(),model.getMaterial(),model.getWorldTransform(nextTransform()));
        }
        for(int i=0;i<scene.nodeCount();i++)visit(scene.nodeAt(i),null);
        while(instances.size()>count)instances.remove(instances.size()-1);
        while(transforms.size()>transformCount)transforms.remove(transforms.size()-1);
        while(materials.size()>materialCount)materials.remove(materials.size()-1);
        return this;
    }
    private Matrix4f nextTransform(){if(transformCount==transforms.size())transforms.add(new Matrix4f());return transforms.get(transformCount++);}
    private void visit(SceneNode3D node,Matrix4f parent){
        if(!node.isVisible())return;Matrix4f world=nextTransform();
        if(parent==null)node.getWorldTransform(world);else node.appendLocalTransform(world.set(parent));
        add(node.getModel(),node.getMaterial(),world);
        for(int i=0;i<node.childCount();i++)visit(node.childAt(i),world);
    }
    private void add(Model3D model,Material3D material,Matrix4f transform){
        if(model==null)return;
        if(model instanceof ObjModel3D obj){
            obj.uploadTextures();
            for(var part:obj.getParts()){
                if(materialCount==materials.size())materials.add(new Material3D());
                Material3D combined=materials.get(materialCount++).set(material);
                var a=combined.getTint();var b=part.material().getTint();a.set(a.r()*b.r(),a.g()*b.g(),a.b()*b.b(),a.a()*b.a());
                if(combined.getRenderPass()==RenderPass3D.OPAQUE&&combined.getTransmission()<=.01f&&(part.material().getRenderPass()==RenderPass3D.TRANSLUCENT||b.a()<1))combined.setRenderPass(RenderPass3D.TRANSLUCENT);
                if(combined.getTexture()==null)combined.setTexture(part.material().getTexture());
                add(part.model(),combined,transform);
            }
            return;
        }
        if(count==instances.size())instances.add(new Item());Item item=instances.get(count++);
        item.model=model;item.material=material;item.transform.set(transform);
    }
    void clear(){instances.clear();transforms.clear();materials.clear();}
}
