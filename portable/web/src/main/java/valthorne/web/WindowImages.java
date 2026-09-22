package valthorne.web;
import valthorne.graphics.texture.TextureData;
import org.teavm.jso.typedarrays.Uint8Array;
import java.util.Objects;
public final class WindowImages {
 private WindowImages(){}
 public static Uint8Array pixels(TextureData texture){
  Objects.requireNonNull(texture);int count=Math.multiplyExact(Math.multiplyExact(texture.width(),texture.height()),4);
  var input=texture.buffer().duplicate();if(count<4||input.remaining()<count)throw new IllegalArgumentException("Invalid RGBA window image");
  var pixels=Uint8Array.create(count);for(int i=0;i<count;i++)pixels.set(i,(short)(input.get()&255));return pixels;
 }
}
