package compatibility;
import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import valthorne.*;
import valthorne.graphics.Color;
/** Identical Java IO code on desktop and web, including persistence across runs. */
public final class CommonFileApplication implements Application {
 private int frames,checks,generation;
 public static void main(String[] args){try{JGL.init(new CommonFileApplication(),JGLConfiguration.defaults().title("File compatibility").size(640,480).visible(false));System.out.println("COMMON_FILES_RETURNED");}catch(Throwable error){error.printStackTrace();throw error;}}
 private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
 public void init(){try{
  Path root=Path.of(".codex-temp/portable-file-fixture");Files.createDirectories(root);
  Path counter=root.resolve("counter.bin");generation=Files.exists(counter)?Integer.parseInt(Files.readString(counter))+1:1;Files.writeString(counter,Integer.toString(generation));
  Path data=root.resolve("data.bin");Files.write(data,new byte[]{1,2,3});Files.write(data,new byte[]{4,5},StandardOpenOption.APPEND);
  check(Arrays.equals(Files.readAllBytes(data),new byte[]{1,2,3,4,5}),"Append/read");
  try(RandomAccessFile file=new RandomAccessFile(data.toFile(),"rw")){file.seek(8);file.write(7);check(file.length()==9,"Sparse write size");file.seek(5);check(file.read()==0&&file.read()==0&&file.read()==0&&file.read()==7,"Sparse zero fill");file.setLength(3);check(file.length()==3,"Truncate");file.seek(0);check(file.read()==1,"Random read");}
  Path copy=root.resolve("copy.bin"),renamed=root.resolve("renamed.bin");Files.deleteIfExists(copy);Files.deleteIfExists(renamed);Files.copy(data,copy);Files.move(copy,renamed);
  check(!Files.exists(copy)&&Files.size(renamed)==3,"Copy/move");
  check(Arrays.asList(root.toFile().list()).contains("renamed.bin"),"Directory listing");
  check(renamed.toFile().setLastModified(1234567890000L),"Set timestamp");check(renamed.toFile().lastModified()==1234567890000L,"Read timestamp");
  Files.delete(renamed);check(!Files.exists(renamed),"Delete");
  Path nested=root.resolve("nested");Files.createDirectories(nested);Files.writeString(nested.resolve("child"),"nested");Path moved=root.resolve("moved");Files.move(nested,moved);check(Files.readString(moved.resolve("child")).equals("nested"),"Directory move");Files.delete(moved.resolve("child"));Files.delete(moved);Files.delete(data);
  Path temporary=Files.createTempFile("valthorne-files-",".bin");Files.write(temporary,new byte[]{6});check(Files.readAllBytes(temporary)[0]==6,"Temporary files");Files.delete(temporary);
  System.out.println("COMMON_FILES_VALIDATED checks="+checks+" generation="+generation);
 }catch(IOException e){throw new UncheckedIOException(e);}}
 public void update(float dt){if(++frames>=10)Window.requestClose();}public void render(){Window.clear(Color.NAVY);}public void dispose(){}
}
