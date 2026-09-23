package valthorne.audio;
import java.nio.ByteBuffer;
import valthorne.web.audio.AudioStreaming;
/** Seekable PCM view. Each stream has its own cursor and borrows immutable decoded samples. */
class BrowserPcmStream implements SoundStream {
 private final SoundData sound;private ByteBuffer pcm;private int cursor;private AudioStreaming.Handle handle;private boolean closed;
 BrowserPcmStream(SoundData source){
  if(source.data()!=null){sound=source;pcm=source.data().slice();return;}
  sound=source;handle=AudioStreaming.open(source.source());

 }
 public int channels(){return sound.channels();}public int sampleRate(){return sound.sampleRate();}public int bitsPerSample(){return sound.bitsPerSample();}public float duration(){return sound.duration();}
 public boolean seek(float seconds){check();if(!Float.isFinite(seconds))throw new IllegalArgumentException("Seek time must be finite");if(handle!=null)return AudioStreaming.seek(handle,seconds);int frame=channels()*(bitsPerSample()/8);cursor=(int)Math.min(pcm.limit(),Math.max(0,seconds)*sampleRate()*frame);cursor-=cursor%frame;return true;}
 public int read(ByteBuffer destination){check();if(handle!=null)return AudioStreaming.read(handle,destination);destination.clear();int count=Math.min(destination.remaining(),pcm.limit()-cursor);int frame=channels()*(bitsPerSample()/8);count-=count%frame;for(int i=0;i<count;i++)destination.put(pcm.get(cursor++));destination.flip();return count;}
 public void close(){if(closed)return;closed=true;if(handle!=null)AudioStreaming.close(handle);handle=null;pcm=null;}private void check(){if(closed)throw new IllegalStateException("Sound stream is closed");}
}
