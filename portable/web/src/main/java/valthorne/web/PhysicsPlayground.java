package valthorne.web;

import valthorne.Application;
import valthorne.portable.SceneBackend;
import valthorne.portable.AssetService;
import valthorne.portable.ModelAsset;
import valthorne.tick.Tick;
import valthorne.math.MathUtils;

/** Java gameplay compiled by TeaVM; platform rendering and Jolt live behind SceneBackend. */
public final class PhysicsPlayground implements Application {
    private final SceneBackend backend;
    private final AssetService assets;
    private int spawned, seconds;
    private final Tick pulse = new Tick().delay(1).callback(t -> seconds++);
    private float orbit;
    private int color = 0x65d9ff;

    public PhysicsPlayground(SceneBackend backend, AssetService assets) {this.backend = backend; this.assets=assets;}
    @Override public void init() {reset(); pulse.start();}
    public void reset() {
        backend.clear();
        backend.box(0,-.5f,0,12,.5f,12,0x526477,false);
        loadModel("models/tree.glb",-5,0,-3,1.8f);
        loadModel("models/tree.glb",5,0,-3,1.5f);
        loadModel("models/tent.glb",-6,0,2,2);
        spawned = 0;
        orbit = 0;
        for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) {
            backend.box((x - 2) * 1.15f, .55f + y * 1.15f, 0, .5f,
                    (x + y) % 2 == 0 ? 0xeab47b : 0x98aabb, true);
            spawned++;
        }
    }
    private void loadModel(String uri,float x,float y,float z,float scale) {
        assets.loadGlb(uri,new AssetService.Listener() {
            public void loaded(ModelAsset model) {model.transform(x,y,z,0,scale);}
            public void failed(String reason) {
                if(!reason.equals("cancelled")) System.err.println("Model load failed: "+reason);
            }
        });
    }
    public void drop() {
        if (spawned >= 80) return;
        backend.box((float)Math.sin(spawned * 2.4) * 3, 9, (float)Math.cos(spawned) * 2,
                .4f, color, true);
        spawned++;
    }
    public void setColor(int rgb) {color = rgb;}
    public int getSpawned() {return spawned;}
    public int getSeconds() {return seconds;}
    @Override public void update(float delta) {
        pulse.update(delta);
        orbit = MathUtils.clamp(orbit + delta * .45f, 0, 100000);
        backend.step(delta);
    }
    @Override public void render() {
        backend.light((float)Math.sin(orbit) * 5, 5, (float)Math.cos(orbit) * 5, color, 90000);
        backend.render();
    }
    @Override public void dispose() {backend.close();}
}
