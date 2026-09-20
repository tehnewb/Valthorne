package org.lwjgl.nanovg;
/** Managed image paint consumed by the browser vector backend. */
public final class NVGPaint implements AutoCloseable {
 public float x,y,width,height,angle,alpha,r0,g0,b0,a0,r1,g1,b1,a1;public int image,kind;
 public static NVGPaint calloc(){return new NVGPaint();}
 public void free(){} public void close(){}
}
