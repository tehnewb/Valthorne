package valthorne.web;
import java.io.*;
/** Standard Reader.transferTo implementation for the pinned TeaVM class library. */
public final class ReaderTransfers {
 private ReaderTransfers(){}
 public static long transfer(Reader input,Writer output)throws IOException{
  java.util.Objects.requireNonNull(output);char[] buffer=new char[8192];long count=0;int read;
  while((read=input.read(buffer,0,buffer.length))>=0){if(read==0){int c=input.read();if(c<0)break;output.write(c);count++;}else{output.write(buffer,0,read);count+=read;}}
  return count;
 }
}
