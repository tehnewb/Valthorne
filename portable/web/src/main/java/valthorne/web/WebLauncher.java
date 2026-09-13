package valthorne.web;
import org.teavm.jso.JSBody;
import valthorne.portable.FirstPersonDemo;

/** Example entry point. Consumers can select another Java main through -PwebMainClass. */
public final class WebLauncher {
    public static void main(String[] args) {
        if(arena()){
            var game=new FirstPersonDemo(new WebInteractiveScene(),new WebPlatform(),new WebAssets(),new WebMedia());
            WebRuntime.start(game,(commands,color)->{if((commands&2)!=0)game.reset();game.setLightColor(color);},()->{WebRuntime.report(9,game.seconds());reportArena(game.hits(),game.shots(),game.ammo());});
            return;
        }
        var game = new PhysicsPlayground(new WebSceneBackend(),new WebAssets());
        WebRuntime.start(game, (commands, color) -> {
            if ((commands & 1) != 0) game.drop();
            if ((commands & 2) != 0) game.reset();
            game.setColor(color);
        }, () -> WebRuntime.report(game.getSpawned(), game.getSeconds()));
    }
    @JSBody(script="return new URLSearchParams(location.search).get('scene')==='arena';") private static native boolean arena();
    @JSBody(params={"hits","shots","ammo"},script="valthorneHost.arenaHits=hits;valthorneHost.arenaShots=shots;valthorneHost.arenaAmmo=ammo;") private static native void reportArena(int hits,int shots,int ammo);
}
