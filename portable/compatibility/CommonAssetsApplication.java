package compatibility;

import valthorne.Application;
import valthorne.JGL;
import valthorne.JGLConfiguration;
import valthorne.Window;
import valthorne.asset.AssetParameters;
import valthorne.asset.Assets;
import valthorne.graphics.Color;
import valthorne.graphics.font.FontData;
import valthorne.graphics.font.FontParameters;
import valthorne.graphics.map.tiled.TiledMapData;
import valthorne.graphics.map.tiled.TiledMapParameters;
import valthorne.graphics.model.ModelParameters;
import valthorne.graphics.model.ObjModel3D;
import valthorne.graphics.texture.TextureData;
import valthorne.graphics.texture.TextureParameters;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class CommonAssetsApplication implements Application {
    private int frames, checks;
    private long start;

    static void main(String[] args) {
        try {
            JGL.init(new CommonAssetsApplication(), JGLConfiguration.defaults().title("Shared asset loading").size(640, 480).visible(false));
            System.out.println("COMMON_ASSETS_RETURNED");
        } catch (Throwable error) {
            error.printStackTrace();
            throw error;
        }
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    public void init() {
        start = System.nanoTime();
        check(CompletableFuture.completedFuture(3).thenApply(v -> v + 1).thenCompose(v -> CompletableFuture.completedFuture(v * 2)).join() == 8, "Completion chain");
        check(CompletableFuture.supplyAsync(() -> 7).thenApplyAsync(v -> v * 2).join() == 14, "Asynchronous chain");
        var source = new CompletableFuture<Integer>();
        var tail = source;
        for (int i = 0; i < 10000; i++) tail = tail.thenApply(v -> v + 1);
        source.complete(0);
        check(tail.join() == 10000, "Deep chain without stack overflow");
        var failure = new IllegalArgumentException("expected");
        var failed = CompletableFuture.<Integer>failedFuture(failure);
        check(failed.exceptionally(e -> 9).join() == 9, "Failure recovery");
        check(failed.handle((v, e) -> e == failure).join(), "Original cause");
        check(failed.exceptionallyCompose(e -> CompletableFuture.completedFuture(6)).join() == 6, "Composed recovery");
        var pending = new CompletableFuture<Integer>();
        var child = pending.thenApply(v -> v + 1);
        check(pending.cancel(true) && pending.isCancelled(), "Cancellation");
        boolean rejected = false;
        try {
            child.join();
        } catch (CompletionException e) {
            rejected = e.getCause() instanceof CancellationException;
        }
        check(rejected && !child.isCancelled(), "Dependent cancellation wrapped");
        var a = new CompletableFuture<Integer>();
        var b = new CompletableFuture<Integer>();
        var combined = a.thenCombine(b, Integer::sum);
        a.complete(2);
        check(!combined.isDone(), "Combine waits for both");
        b.complete(4);
        check(combined.join() == 6, "Combined value");
        a = new CompletableFuture<>();
        b = new CompletableFuture<>();
        var any = CompletableFuture.anyOf(a, b);
        b.complete(5);
        check(any.join().equals(5), "First completion");
        check(a.getNumberOfDependents() == 0, "Losing anyOf dependency released");
        var all = CompletableFuture.allOf(a, failed);
        check(!all.isDone(), "allOf waits despite failure");
        a.complete(1);
        rejected = false;
        try {
            all.join();
        } catch (CompletionException e) {
            rejected = e.getCause() == failure;
        }
        check(rejected, "allOf failure cause");
        check(new CompletableFuture<Integer>().completeOnTimeout(12, 5, TimeUnit.MILLISECONDS).join() == 12, "Fallback timeout");
        rejected = false;
        try {
            new CompletableFuture<Integer>().orTimeout(5, TimeUnit.MILLISECONDS).join();
        } catch (CompletionException e) {
            rejected = e.getCause() instanceof TimeoutException;
        }
        check(rejected, "Exceptional timeout");
        try {
            new CompletableFuture<Integer>().get(1, TimeUnit.MILLISECONDS);
            throw new AssertionError("Missing timed get failure");
        } catch (TimeoutException expected) {
            checks++;
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
        check(CompletableFuture.completedStage(4).toCompletableFuture().join() == 4, "Minimal stage copy");
        var counter = new AtomicInteger();
        Assets.addLoader(Parameters.class, p -> {
            counter.incrementAndGet();
            return new Value();
        });
        var parameters = new Parameters("custom");
        CompletableFuture<Value> first = Assets.loadAsync(parameters, Value.class);
        check(first == Assets.loadAsync(parameters, Value.class), "Load deduplication");
        Value value = first.join();
        check(counter.get() == 1 && Assets.get("custom", Value.class) == value, "Cache retrieval");
        check(Assets.unload("custom") && value.disposed == 1, "Reflective user asset disposal");
        var image = TextureParameters.fromPath("portable/compatibility/assets/colors.png", "image");
        TextureData texture = Assets.loadAsync(image, TextureData.class).join();
        check(texture.width() == 2, "Asynchronous browser image decode");
        check(Assets.unload(image.key()), "Texture unload");
        Assets.prepare(new Parameters("first"));
        Assets.prepare(new Parameters("second"));
        Assets.prepare(new Parameters("first"));
        Assets.load().join();
        check(Assets.isFinished() && Assets.getProgress() == 1, "Prepared load progress");
        check(Assets.clear() == 2, "Batch cleanup");
        Assets.prepare(image);
        Assets.prepare(FontParameters.fromClasspath("ui/AtkinsonHyperlegible-Regular.ttf", "font", 18, 32, 96));
        Assets.prepare(ModelParameters.fromPath("portable/compatibility/assets/cube.obj", "model"));
        Assets.prepare(TiledMapParameters.fromPath("portable/compatibility/assets/maps/arena.tmx", "map"));
        Assets.load().join();
        check(Assets.get("image", TextureData.class).width() == 2, "Prepared image");
        check(Assets.get("font", FontData.class) != null, "Prepared font");
        check(Assets.get("model", ObjModel3D.class).getTriangleCount() == 12, "Prepared model");
        check(Assets.get("map", TiledMapData.class).getWidth() == 4, "Prepared Tiled map");
        check(Assets.clear() == 4, "Mixed asset cleanup");
        Assets.shutdown();
        value = Assets.loadAsync(new Parameters("restart"), Value.class).join();
        check(value != null, "Executor restart");
        Assets.clear();
        Assets.shutdown();
        System.out.println("COMMON_ASSETS_READY");
    }

    public void update(float dt) {
        if (++frames >= 90) Window.requestClose();
    }

    public void render() {
        Window.clear(Color.NAVY);
    }

    public void dispose() {
        Assets.clear();
        Assets.shutdown();
        System.out.println("COMMON_ASSETS_VALIDATED checks=" + checks + " elapsedMs=" + ((System.nanoTime() - start) / 1e6));
    }

    public record Parameters(String key) implements AssetParameters {
    }

    public static final class Value {
        public int disposed;

        public void dispose() {
            disposed++;
        }
    }
}
