package valthorne;

import java.nio.file.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.glfw.GLFW.*;

/** Platform rendering and diagnostic operations shared by application launchers. */
public final class PlatformTools {
    private PlatformTools() {}
    public static final int RELEASE=0,PRESS=1,REPEAT=2;
    public static void viewport(int x,int y,int width,int height){glViewport(x,y,width,height);}
    public static void finish(){glFinish();}
    public static int graphicsError(){return glGetError();}
    public static String rendererName(){return glGetString(GL_RENDERER);}
    public static void textureUnit(int unit){glActiveTexture(GL_TEXTURE0+unit);}
    public static void capture(String path,int width,int height){
        try{
            var bytes=java.nio.ByteBuffer.allocateDirect(Math.multiplyExact(Math.multiplyExact(width,height),4));
            glReadPixels(0,0,width,height,GL_RGBA,GL_UNSIGNED_BYTE,bytes);
            var image=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_RGB);
            for(int y=0;y<height;y++)for(int x=0;x<width;x++){int at=(y*width+x)*4;image.setRGB(x,height-y-1,(bytes.get(at)&255)<<16|(bytes.get(at+1)&255)<<8|bytes.get(at+2)&255);}
            Path file=Path.of(path);if(file.getParent()!=null)Files.createDirectories(file.getParent());
            javax.imageio.ImageIO.write(image,"png",file.toFile());
        }catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
    }
    /** Replays input through the installed engine callbacks for deterministic diagnostics. */
    public static void injectKey(int code,int action){long window=Window.getAddress();var callback=glfwSetKeyCallback(window,null);glfwSetKeyCallback(window,callback);if(callback!=null)callback.invoke(window,code,0,action,0);}
    /** Positions use the engine's bottom-left coordinate convention. */
    public static void injectPointer(float x,float y,int button,int action){long window=Window.getAddress();var cursor=glfwSetCursorPosCallback(window,null);glfwSetCursorPosCallback(window,cursor);if(cursor!=null)cursor.invoke(window,x,Window.getHeight()-y);if(button>=0){var callback=glfwSetMouseButtonCallback(window,null);glfwSetMouseButtonCallback(window,callback);if(callback!=null)callback.invoke(window,button,action,0);}}
}
