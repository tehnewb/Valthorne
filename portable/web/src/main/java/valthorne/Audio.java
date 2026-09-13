package valthorne;
import java.util.*;
import java.util.concurrent.Callable;
import valthorne.audio.sound.*;
/** Browser audio tasks execute on the application's coroutine, without a native context thread. */
public class Audio {
 private Audio(){}
 private static final Set<SoundPlayer> players=new LinkedHashSet<>();
 private static ListenerPosition listenerPosition=new ListenerPosition(0,0,0);
 public record ListenerPosition(float x,float y,float z){}
 public static void setListenerPosition(float x,float y,float z){if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))throw new IllegalArgumentException("Listener coordinates must be finite");if(listenerPosition.x()!=x||listenerPosition.y()!=y||listenerPosition.z()!=z)listenerPosition=new ListenerPosition(x,y,z);}
 public static ListenerPosition getListenerPosition(){return listenerPosition;}
 public static SoundPlayer load(String path){return create(SoundData.load(path));}
 public static SoundPlayer load(byte[] bytes){return create(SoundData.load(bytes));}
 public static SoundPlayer create(SoundData data){SoundPlayer player=new SoundPlayer(data);register(player);return player;}
 public static void destroy(SoundPlayer player){if(player!=null){unregister(player);player.dispose();}}
 public static void run(Runnable runnable){Objects.requireNonNull(runnable).run();}
 public static void sync(Runnable runnable){run(runnable);}
 public static <T>T call(Callable<T> callable){try{return Objects.requireNonNull(callable).call();}catch(RuntimeException|Error failure){throw failure;}catch(Exception failure){throw new RuntimeException(failure);}}
 public static boolean isAudioThread(){return true;}
 public static void register(SoundPlayer player){players.add(Objects.requireNonNull(player));}
 public static void unregister(SoundPlayer player){players.remove(player);}
 static void update(){for(SoundPlayer player:players)player.update();}
 static void dispose(){while(!players.isEmpty())players.iterator().next().dispose();}
}
