package valthorne.audio;
import java.nio.*;
import valthorne.audio.AudioFormat;
import valthorne.web.BrowserIO;
/** PCM16 RIFF reader preserving the desktop payload metadata. */
public class WaveSoundDecoder implements SoundDecoder {
 public static SoundMetadata probe(byte[] bytes){
  if(bytes==null||bytes.length<12)throw new IllegalArgumentException("Invalid WAV");ByteBuffer input=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
  if(input.getInt(0)!=0x46464952||input.getInt(8)!=0x45564157)throw new IllegalArgumentException("Invalid WAV");int channels=0,rate=0,bits=0;
  for(int at=12;at+8<=bytes.length;){int tag=input.getInt(at),size=input.getInt(at+4),start=at+8;if(size<0||size>bytes.length-start)throw new IllegalArgumentException("Truncated WAV chunk");
   if(tag==0x20746d66){if(size<16)throw new IllegalArgumentException("Invalid WAV format");int encoding=input.getShort(start)&65535;channels=input.getShort(start+2)&65535;rate=input.getInt(start+4);bits=input.getShort(start+14)&65535;if(encoding!=1||bits!=16)throw new IllegalArgumentException("Only PCM16 WAV supported");}
   if(tag==0x61746164){if(channels<=0||rate<=0||bits!=16||size%(channels*2)!=0)throw new IllegalArgumentException("Invalid WAV metadata");return new SoundMetadata(size/(float)(channels*rate*2L),channels,rate,bits,start,size);}
   at=start+size+(size&1);
  }throw new IllegalArgumentException("No WAV audio data chunk found");
 }
 public static SoundMetadata probe(String path){return probe(BrowserIO.read(path));}
 public SoundData decode(byte[] bytes){SoundMetadata m=probe(bytes);ByteBuffer pcm=ByteBuffer.allocate((int)m.dataLength());pcm.put(bytes,(int)m.dataOffset(),(int)m.dataLength()).flip();return new SoundData(null,pcm,0,m.dataLength(),m.duration(),m.channels(),m.sampleRate(),m.bitsPerSample(),false,false,AudioFormat.WAV);}
}
