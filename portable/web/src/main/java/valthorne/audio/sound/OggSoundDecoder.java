package valthorne.audio.sound;
import valthorne.audio.AudioFormat;
import valthorne.web.BrowserIO;
import valthorne.web.audio.AudioDecode;
public class OggSoundDecoder implements SoundDecoder {
 private static void validate(byte[] bytes){if(AudioFormat.detect(bytes)!=AudioFormat.OGG)throw new IllegalArgumentException("Invalid OGG source");}
 public static SoundMetadata probe(byte[] bytes){validate(bytes);SoundData data=SoundData.load(bytes);return new SoundMetadata(data.duration(),data.channels(),data.sampleRate(),data.bitsPerSample(),0,bytes.length);}
 public static SoundMetadata probe(String path){return probe(BrowserIO.read(path));}
 public SoundData decode(byte[] bytes){validate(bytes);SoundData data=AudioDecode.decode(null,bytes,AudioFormat.OGG);return new SoundData(null,data.data(),0,data.data().remaining(),data.duration(),data.channels(),data.sampleRate(),data.bitsPerSample(),false,true,AudioFormat.OGG);}
}
