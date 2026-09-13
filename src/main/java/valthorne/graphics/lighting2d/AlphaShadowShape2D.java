package valthorne.graphics.lighting2d;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Immutable, shareable pixel-alpha boundary. No GPU readback or native resources.
 * Holes and disconnected islands are preserved. Collinear pixel edges are merged.
 * Coordinates cover [0,1] in each axis, with row zero at local Y zero.
 */
public final class AlphaShadowShape2D {
    final float[] edges;
    private AlphaShadowShape2D(float[] edges) { this.edges = edges; }
    public int getSegmentCount() { return edges.length / 4; }

    /** Reads tightly packed RGBA8 from the buffer's current position, without changing it.
     * Alpha strictly greater than cutoff (0..255) blocks light. Pixels are copied into
     * boundary geometry; the source buffer may be released after this call.
     */
    public static AlphaShadowShape2D fromRgba(ByteBuffer rgba, int width, int height, int cutoff) {
        if (width <= 0 || height <= 0 || cutoff < 0 || cutoff > 255)
            throw new IllegalArgumentException("Invalid dimensions or alpha cutoff");
        int count = Math.multiplyExact(width, height);
        if ((long) count * 4 > rgba.remaining()) throw new IllegalArgumentException("Incomplete RGBA pixels");
        boolean[] mask = new boolean[count];
        int base = rgba.position();
        for (int i=0;i<count;i++) mask[i]=(rgba.get(base+i*4+3)&255)>cutoff;
        return fromMask(mask,width,height);
    }

    static AlphaShadowShape2D fromMask(boolean[] mask, int w, int h) {
        Edges out = new Edges();
        // Scan boundary lines, merging runs with the same occupied side. Keeping the
        // side distinct avoids joining diagonally touching islands into one edge.
        for (int y=0;y<=h;y++) {
            int start=0, previous=0;
            for (int x=0;x<=w;x++) {
                int side=x==w?0:(filled(mask,w,h,x,y-1)?1:0)-(filled(mask,w,h,x,y)?1:0);
                if(side!=previous) {
                    if(previous!=0) out.add((float)start/w,(float)y/h,(float)x/w,(float)y/h);
                    start=x;previous=side;
                }
            }
        }
        for (int x=0;x<=w;x++) {
            int start=0, previous=0;
            for (int y=0;y<=h;y++) {
                int side=y==h?0:(filled(mask,w,h,x-1,y)?1:0)-(filled(mask,w,h,x,y)?1:0);
                if(side!=previous) {
                    if(previous!=0) out.add((float)x/w,(float)start/h,(float)x/w,(float)y/h);
                    start=y;previous=side;
                }
            }
        }
        return new AlphaShadowShape2D(Arrays.copyOf(out.data,out.count));
    }
    private static boolean filled(boolean[] m,int w,int h,int x,int y) {
        return x>=0 && y>=0 && x<w && y<h && m[y*w+x];
    }
    private static final class Edges {
        float[] data=new float[64];int count;
        void add(float a,float b,float c,float d) {
            if(count+4>data.length)data=Arrays.copyOf(data,data.length*2);
            data[count++]=a;data[count++]=b;data[count++]=c;data[count++]=d;
        }
    }
}
