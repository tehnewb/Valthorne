package valthorne.audio.sound;
import valthorne.audio.AudioFormat;
import valthorne.web.BrowserIO;
import valthorne.web.audio.AudioDecode;
public class Mp3SoundDecoder implements SoundDecoder {
 public static SoundMetadata probe(byte[] bytes){SoundData data=SoundData.load(bytes);return new SoundMetadata(data.duration(),data.channels(),data.sampleRate(),data.bitsPerSample(),0,bytes.length);}
 public static SoundMetadata probe(String path){return probe(BrowserIO.read(path));}
 public SoundData decode(byte[] bytes){SoundData data=AudioFormat.detect(bytes)==AudioFormat.WAV?new WaveSoundDecoder().decode(bytes):AudioDecode.decode(null,bytes,AudioFormat.MP3);return new SoundData(null,data.data(),0,data.data().remaining(),data.duration(),data.channels(),data.sampleRate(),data.bitsPerSample(),false,true,AudioFormat.MP3);}
}
