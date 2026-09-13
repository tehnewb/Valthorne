package valthorne.web.graphics;

import valthorne.graphics.shader.Shader;
import org.teavm.jso.*;
import static valthorne.web.graphics.BrowserGL.*;

/** Internal raster implementation of the four independent-pixel radiance passes. */
public final class BrowserRadianceShader extends Shader {
 private static final String VERTEX="""
  #version 300 es
  precision highp float;
  void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));gl_Position=vec4(p*2.0-1.0,0.0,1.0);}
  """;
 private final int framebuffer,vao;
 private boolean closed;
 public BrowserRadianceShader(String fragment){super(VERTEX,fragment);framebuffer=glGenFramebuffers();vao=glGenVertexArrays();}
 public void setUniform2i(String name,int x,int y){glUniform2i(glGetUniformLocation(getProgramID(),name),x,y);}
 public void renderTo(int target,int width,int height){
  if(closed)throw new IllegalStateException("Radiance shader is closed");
  glBindFramebuffer(GL_FRAMEBUFFER,framebuffer);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,target,0);
  if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete radiance framebuffer");
  glViewport(0,0,width,height);glBindVertexArray(vao);glDrawArrays(GL_TRIANGLES,0,3);
 }
 @Override public void dispose(){if(closed)return;closed=true;glDeleteFramebuffers(framebuffer);glDeleteVertexArrays(vao);super.dispose();}
 /** Save raster-only state once per solve, instead of querying it for every level. */
 public static final class PassState implements AutoCloseable {
  private final JSObject saved=begin();
  public void close(){end(saved);}
  @JSBody(script="var g=valthorneHost.graphics.context(),caps=[g.BLEND,g.DEPTH_TEST,g.CULL_FACE,g.SCISSOR_TEST,g.STENCIL_TEST,g.RASTERIZER_DISCARD];var s={draw:g.getParameter(g.DRAW_FRAMEBUFFER_BINDING),read:g.getParameter(g.READ_FRAMEBUFFER_BINDING),viewport:g.getParameter(g.VIEWPORT),vao:g.getParameter(g.VERTEX_ARRAY_BINDING),mask:g.getParameter(g.COLOR_WRITEMASK),caps:caps.map(function(c){return [c,g.isEnabled(c)];})};caps.forEach(function(c){g.disable(c);});g.colorMask(true,true,true,true);return s;") private static native JSObject begin();
  @JSBody(params="s",script="var g=valthorneHost.graphics.context();g.bindFramebuffer(g.DRAW_FRAMEBUFFER,s.draw);g.bindFramebuffer(g.READ_FRAMEBUFFER,s.read);g.viewport(s.viewport[0],s.viewport[1],s.viewport[2],s.viewport[3]);g.bindVertexArray(s.vao);g.colorMask(s.mask[0],s.mask[1],s.mask[2],s.mask[3]);s.caps.forEach(function(v){if(v[1])g.enable(v[0]);else g.disable(v[0]);});") private static native void end(JSObject s);
 }
}
