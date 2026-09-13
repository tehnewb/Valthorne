package valthorne.audio.sound;
public class OggSoundStream extends BrowserPcmStream {
 public OggSoundStream(SoundData data){super(data);}
 public int channels(){return super.channels();}public int sampleRate(){return super.sampleRate();}public int bitsPerSample(){return super.bitsPerSample();}public float duration(){return super.duration();}
 public boolean seek(float seconds){return super.seek(seconds);}public int read(java.nio.ByteBuffer pcm){return super.read(pcm);}public void close(){super.close();}
}
