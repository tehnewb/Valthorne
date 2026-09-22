package valthorne.audio.sound;
import java.nio.*;
import valthorne.audio.AudioFormat;
import valthorne.web.BrowserIO;
import valthorne.web.audio.AudioDecode;
import valthorne.Audio;
import valthorne.web.audio.AudioStreaming;

/** Decoded browser audio with the existing data and stream API. */
public record SoundData(SoundSource source,ByteBuffer data,long streamOffset,long streamLength,float duration,int channels,int sampleRate,int bitsPerSample,boolean streaming,boolean compressed,AudioFormat format){
 public static SoundData load(byte[] bytes){return decode(new SoundSource.BytesSource(bytes),bytes);}
 public static SoundData load(String path){return AudioStreaming.probe(new SoundSource.PathSource(path));}
 private static SoundData decode(SoundSource source,byte[] bytes){
  AudioFormat format=AudioFormat.detect(bytes);
  if(format==AudioFormat.WAV){
   SoundMetadata m=WaveSoundDecoder.probe(bytes);
   if(m.duration()<=0||m.duration()>15||m.dataLength()>8*1024*1024)return new SoundData(source,null,m.dataOffset(),m.dataLength(),m.duration(),m.channels(),m.sampleRate(),m.bitsPerSample(),true,false,format);
   return new WaveSoundDecoder().decode(bytes);
  }
  return AudioStreaming.probe(source);
 }
 static SoundData create(SoundSource source,ByteBuffer pcm,float duration,int channels,int rate,int bits,AudioFormat format){
  return new SoundData(source,pcm,0,pcm.remaining(),duration,channels,rate,bits,duration>15||pcm.remaining()>8*1024*1024,format!=AudioFormat.WAV,format);
 }
 public SoundStream openStream(){if(!streaming)throw new IllegalStateException("SoundData is not stream-backed");return new BrowserPcmStream(this);}
 public long estimatedPcmBytes(){if(duration<=0||channels<=0||sampleRate<=0||bitsPerSample<=0)return -1;double bytes=duration*sampleRate*channels*(bitsPerSample/8.0);return bytes>=Long.MAX_VALUE?Long.MAX_VALUE:Math.round(bytes);}
 public SoundPlayer asSoundPlayer(){return Audio.create(this);}
}
