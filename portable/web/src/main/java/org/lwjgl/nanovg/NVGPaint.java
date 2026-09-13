package org.lwjgl.nanovg;
/** Managed image paint consumed by the browser vector backend. */
public final class NVGPaint implements AutoCloseable {
 public float x,y,width,height,angle,alpha;public int image;
 public static NVGPaint calloc(){return new NVGPaint();}
 public void free(){} public void close(){}
}
