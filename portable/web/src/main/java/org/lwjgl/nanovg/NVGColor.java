package org.lwjgl.nanovg;
/** Managed RGBA value for the engine's browser UI backend. No native address is exposed. */
public final class NVGColor implements AutoCloseable {
 private float r,g,b,a;
 public static NVGColor create(){return new NVGColor();}
 public static NVGColor calloc(){return new NVGColor();}
 public float r(){return r;} public float g(){return g;} public float b(){return b;} public float a(){return a;}
 public NVGColor r(float value){r=value;return this;} public NVGColor g(float value){g=value;return this;} public NVGColor b(float value){b=value;return this;} public NVGColor a(float value){a=value;return this;}
 public NVGColor set(float r,float g,float b,float a){return r(r).g(g).b(b).a(a);}
 public void free(){} public void close(){}
}
