package valthorne.graphics.lighting2d;

import valthorne.graphics.Sprite;
import valthorne.graphics.texture.TextureData;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Objects;

/** Shadow caster that follows a borrowed Sprite automatically during Lighting2D rendering.
 * Uses retained CPU RGBA pixels, never GPU readback. Frame silhouettes are held in a
 * bounded per-instance cache (default 32). Prewarm animations with update() after setting
 * each frame. Live pixel edits require clearFrameCache(); normal sprite frame/transform
 * setters are detected automatically. Do not dispose source pixel data before extraction.
 * Sprite drawing with explicit per-call transform overrides is not observed.
 */
public final class SpriteOccluder2D extends AlphaOccluder2D {
    private static final AlphaShadowShape2D EMPTY=AlphaShadowShape2D.fromMask(new boolean[1],1,1);
    private final Sprite sprite;
    private final int cutoff, capacity;
    private final Frame[] frames;
    private final AlphaShadowShape2D[] shapes;
    private int frameCount,nextSlot;
    private Frame current;
    private long extractions;
    private record Frame(TextureData data,float u,float v,float u2,float v2,int cutoff) { }

    public SpriteOccluder2D(Sprite sprite) { this(sprite,127,32); }
    public SpriteOccluder2D(Sprite sprite,int alphaCutoff,int cachedFrames) {
        super(EMPTY);
        this.sprite=Objects.requireNonNull(sprite,"sprite");
        if(alphaCutoff<0 || alphaCutoff>255 || cachedFrames<1)throw new IllegalArgumentException("Invalid alpha cutoff/cache capacity");
        cutoff=alphaCutoff;capacity=cachedFrames;
        frames=new Frame[capacity];shapes=new AlphaShadowShape2D[capacity];update();
    }
    public Sprite getSprite() { return sprite; }
    public int getCachedFrameCount() { return frameCount; }
    public long getExtractionCount() { return extractions; }
    public void clearFrameCache() { Arrays.fill(frames,null);Arrays.fill(shapes,null);frameCount=nextSlot=0;current=null; }
    @Override void synchronize() { update(); }
    /** Synchronizes without allocating when frame, tint and transform are unchanged. */
    public void update() {
        TextureData data=sprite.getTexture().getData();
        FloatBuffer uv=sprite.getUVBuffer();
        float u=uv.get(0),v=uv.get(1),u2=uv.get(4),v2=uv.get(5);
        float alpha=sprite.getColor().a();
        int threshold=alpha>0?Math.min(255,(int)Math.floor(cutoff/alpha)):255;
        if(current==null || current.data!=data || current.u!=u || current.v!=v || current.u2!=u2 || current.v2!=v2 || current.cutoff!=threshold) {
            int slot=-1;
            for(int i=0;i<frameCount;i++) {
                Frame key=frames[i];
                if(key.data==data && key.u==u && key.v==v && key.u2==u2 && key.v2==v2 && key.cutoff==threshold) {slot=i;break;}
            }
            if(slot<0) {
                Frame key=new Frame(data,u,v,u2,v2,threshold);
                AlphaShadowShape2D shape=extract(key);extractions++;
                slot=nextSlot;nextSlot=(nextSlot+1)%capacity;
                frames[slot]=key;shapes[slot]=shape;frameCount=Math.min(capacity,frameCount+1);
            }
            setShape(shapes[slot]);current=frames[slot];
        }
        FloatBuffer quad=sprite.getVertexBuffer();
        float x=quad.get(0),y=quad.get(1);
        setBasis(x,y,quad.get(2)-x,quad.get(3)-y,quad.get(6)-x,quad.get(7)-y);
    }
    private static AlphaShadowShape2D extract(Frame frame) {
        if(frame.cutoff==255)return EMPTY;
        TextureData data=frame.data;
        if(!Float.isFinite(frame.u+frame.v+frame.u2+frame.v2))throw new IllegalArgumentException("Invalid sprite UVs");
        int w=Math.max(1,Math.round(Math.abs(frame.u2-frame.u)*data.width()));
        int h=Math.max(1,Math.round(Math.abs(frame.v2-frame.v)*data.height()));
        if(w>data.width() || h>data.height())throw new IllegalArgumentException("Repeated UVs are not supported by sprite shadows");
        boolean[] mask=new boolean[Math.multiplyExact(w,h)];
        var pixels=data.buffer();int base=pixels.position();
        if((long)data.width()*data.height()*4>pixels.remaining())throw new IllegalArgumentException("Missing retained RGBA pixels");
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            int sx=Math.clamp((int)Math.floor((frame.u+(frame.u2-frame.u)*(x+.5f)/w)*data.width()),0,data.width()-1);
            int sy=Math.clamp((int)Math.floor((frame.v+(frame.v2-frame.v)*(y+.5f)/h)*data.height()),0,data.height()-1);
            mask[y*w+x]=(pixels.get(base+(sy*data.width()+sx)*4+3)&255)>frame.cutoff;
        }
        return AlphaShadowShape2D.fromMask(mask,w,h);
    }
}
