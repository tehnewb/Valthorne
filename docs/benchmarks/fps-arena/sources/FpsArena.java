package valthorne.examples;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.nanovg.NVGColor;
import valthorne.*;
import valthorne.camera.PerspectiveCamera;
import valthorne.event.events.*;
import valthorne.event.EventTypes;
import valthorne.event.listeners.KeyAdapter;
import valthorne.event.listeners.MouseAdapter;
import valthorne.graphics.Color;
import valthorne.graphics.model.*;
import valthorne.ui.*;
import valthorne.ui.nodes.nano.*;
import valthorne.ui.theme.ProfessionalTheme;
import valthorne.viewport.ScreenViewport;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.opengl.GL43.*;

/** Playable first-person feature test: native movement, drone waves and light-carrying particles. */
public final class FpsArena implements Application {
    private static final Color BACKGROUND = new Color(.025f, .035f, .05f, 1);
    private final Scene3D scene = new Scene3D();
    private final PerspectiveCamera camera = new PerspectiveCamera();
    private final Vector3f eye = new Vector3f(), forward = new Vector3f(0, 1, 0), muzzle = new Vector3f();
    private final Matrix4f weaponTransform = new Matrix4f();
    private final ArrayList<ModelInstance3D> weapon = new ArrayList<>();
    private final ArrayList<PointLight3D> roomLights = new ArrayList<>();
    private double[] renderSamples, simulationSamples;
    private PhysicsStudioModels models;
    private FpsArenaWorld game;
    private FpsArenaEffects effects;
    private FilamentRenderer3D renderer;
    private UIRoot ui;
    private ProfessionalTheme theme;
    private NanoPanel menu;
    private NanoLabel menuTitle, menuHint;
    private NanoButton resume;
    private Hud hud;
    private int width, height, frames, reportFrames, lastHealth = 100, lastKills;
    private int shownHealth = -1, shownAmmo = -1, shownReserve = -1;
    private boolean playing, started, smoke, benchmark, lookReady, jumpQueued, physicalParticles = true, particleLights = true;
    private float yaw, pitch, lastMouseX, lastMouseY, sensitivity = .0022f, fieldOfView = 72;
    private float exposure = 2.8f, environment = 80, particlePower = 1, keyPower = 180;
    private float reportTime, fps, renderMs, simulationMs, hitFlash, damageFlash, recoil, toastTime;
    private String toast = "", performance = "Preparing arena...", healthText = "100", ammoText = "24", reserveText = "/ 120", waveText = "WAVE 01", scoreText = "000000", targetText = "Clear the drones", reloadText = "PULSE RIFLE", details = "", supplies = "";
    private int palette;
    private final BitSet heldKeys = new BitSet();
    private int freshKey = -1;
    private final KeyAdapter keyEdges = new KeyAdapter() {
        @Override public void keyPressed(KeyPressEvent e) {
            int key = e.getKey();
            freshKey = key >= 0 && !heldKeys.get(key) ? key : -1;
            if (key >= 0) heldKeys.set(key);
        }
        @Override public void keyReleased(KeyReleaseEvent e) {if (e.getKey() >= 0) heldKeys.clear(e.getKey());}
    };

    private final KeyAdapter keys = new KeyAdapter() {
        @Override public void keyPressed(KeyPressEvent e) {
            int key = e.getKey();
            if (key < 0 || key != freshKey) return;
            if (key == GLFW_KEY_ESCAPE) {setPlaying(!playing && !game.isDead()); e.consume();}
            else if (playing) {
                if (key == GLFW_KEY_SPACE) jumpQueued = true;
                if (key == GLFW_KEY_R) game.reload();
                if (key == GLFW_KEY_G && game.throwGrenade(eye, forward)) tell("GRENADE OUT");
                if (key == GLFW_KEY_F && effects.flare(eye, forward, game.getPlayerBody())) tell("LIGHT FLARE DEPLOYED");
            }
        }
    };
    private final MouseAdapter mouse = new MouseAdapter() {
        @Override public void mouseMoved(MouseMoveEvent e) {look(e.getToX(), e.getToY());}
        @Override public void mouseDragged(MouseDragEvent e) {look(e.getToX(), e.getToY());}
    };
    private final valthorne.event.EventHandler<WindowFocusEvent> focus = e -> {
        if (!e.isFocused()) {heldKeys.clear(); if (playing) setPlaying(false);}
    };

    public static void main(String[] args) {
        var app = new FpsArena();
        for (String arg : args) {
            if (arg.equals("--smoke")) app.smoke = true;
            if (arg.equals("--benchmark")) app.benchmark = true;
            if (arg.equals("--visual-particles")) app.physicalParticles = false;
            if (arg.equals("--no-particle-lights")) app.particleLights = false;
        }
        if (app.benchmark) {
            app.renderSamples = new double[360];
            app.simulationSamples = new double[360];
        }
        JGL.init(app, JGLConfiguration.defaults().contextVersion(4, 3).size(1600, 960).depthBits(24)
                .visible(!app.smoke && !app.benchmark).title("Valthorne | LIVE FIRE - Physics + Light Arena"));
    }

    @Override public void init() {
        renderer = new FilamentRenderer3D();
        renderer.setQuality(FilamentRenderer3D.Quality.HIGH);
        renderer.setExposure(exposure);
        renderer.setEnvironmentIntensity(environment);
        camera.setClipPlanes(.04f, 100);
        models = new PhysicsStudioModels();
        resetGame();
        Keyboard.addKeyListener(keyEdges);
        ui = new UIRoot();
        ui.setClickable(false);
        ui.setViewport(new ScreenViewport(Window.getWidth(), Window.getHeight()));
        theme = new ProfessionalTheme(false, 1);
        ui.setTheme(theme.create());
        hud = new Hud();
        hud.setClickable(false);
        ui.add(hud);
        buildMenu();
        resize();
        Keyboard.addKeyListener(keys);
        Mouse.addMouseListener(mouse);
        JGL.subscribe(EventTypes.WINDOW_FOCUS, focus);
        glfwSetWindowSizeLimits(Window.getAddress(), 1280, 800, GLFW_DONT_CARE, GLFW_DONT_CARE);
        glfwSwapInterval(0);
        if (benchmark) setPlaying(true);
    }

    private void resetGame() {
        if (effects != null) effects.close();
        if (game != null) game.close();
        scene.clear();
        weapon.clear();
        roomLights.clear();
        game = new FpsArenaWorld(scene, models, impact -> {
            if (effects != null) effects.impact(impact);
            hitFlash = .1f;
        });
        effects = new FpsArenaEffects(scene, game.getPhysics(), physicalParticles, particleLights);
        effects.lightGain = particlePower;
        effects.palette = palette;
        addLight(0, -6, 7, 1, .88f, .68f, keyPower, 32, true);
        addLight(-9, 5, 5, .2f, .55f, 1, 65, 22, false);
        addLight(9, 9, 5, 1, .35f, .1f, 80, 24, false);
        addLight(0, 15, 8, .7f, .85f, 1, 110, 25, true);
        buildWeapon();
        yaw = pitch = recoil = 0;
        hitFlash = damageFlash = toastTime = 0;
        lastHealth = 100;
        lastKills = 0;
        updateCamera();
        refreshStatus(roomLights.size());
    }

    private void addLight(float x, float y, float z, float r, float g, float b, float intensity, float range, boolean shadows) {
        var light = new PointLight3D().setPosition(x, y, z).setColor(new Color(r, g, b, 1))
                .setIntensity(intensity).setRange(range).setCastsShadows(shadows);
        roomLights.add(light);
        scene.addLight(light);
    }

    private void buildWeapon() {
        var box = ModelBuilder3D.box(1, 1, 1);
        var dark = new Material3D().setTint(new Color(.075f, .09f, .12f, 1)).setMetallic(.7f).setRoughness(.3f).setCastsShadow(false);
        var steel = new Material3D().setTint(new Color(.35f, .42f, .48f, 1)).setMetallic(.85f).setRoughness(.22f).setCastsShadow(false);
        var cyan = new Material3D().setTint(new Color(.05f, .7f, .85f, 1)).setMetallic(.3f).setRoughness(.2f).setCastsShadow(false);
        weaponPiece(box, dark, .24f, .43f, -.24f, .16f, .5f, .15f);
        weaponPiece(box, steel, .24f, .77f, -.22f, .085f, .42f, .07f);
        weaponPiece(box, dark, .24f, .42f, -.39f, .11f, .15f, .24f);
        weaponPiece(box, cyan, .24f, .4f, -.155f, .09f, .21f, .015f);
        weaponPiece(box, dark, .24f, .64f, -.14f, .055f, .06f, .1f);
        weaponPiece(box, dark, .24f, .23f, -.22f, .14f, .16f, .12f);
    }

    private void weaponPiece(Model3D mesh, Material3D material, float x, float y, float z, float w, float d, float h) {
        var piece = new ModelInstance3D().setModel(mesh).setMaterial(material).setPosition(x, y, z).setScale(w, d, h);
        scene.add(piece);
        weapon.add(piece);
    }

    private NanoLabel label(String text, int size) {
        var node = new NanoLabel(text);
        node.setStyle(NanoLabel.FONT_SIZE_KEY, (float) size);
        node.getLayout().noShrink();
        return node;
    }

    private NanoButton button(String text, Runnable action) {
        var node = new NanoButton(text).action(n -> action.run());
        node.getLayout().height(36).noShrink();
        return node;
    }

    private void slider(String text, float min, float max, float value, java.util.function.DoubleConsumer change) {
        var caption = label(text + String.format(Locale.ROOT, "  %.2f", value), 12);
        menu.add(caption);
        var slider = new NanoSlider(min, max, value).action(n -> {
            change.accept(n.getValue());
            caption.text(text + String.format(Locale.ROOT, "  %.2f", n.getValue()));
        });
        slider.getLayout().height(20).noShrink();
        menu.add(slider);
    }

    private void buildMenu() {
        menu = new NanoPanel();
        menu.getLayout().absolute().column().width(370).padding(20).gap(6);
        menuTitle = label("LIVE FIRE / TEST CONSOLE", 21);
        menu.add(menuTitle);
        menuHint = label("Clear drone waves. Test every impact.", 13);
        menu.add(menuHint);
        resume = button("Enter arena", () -> setPlaying(true));
        menu.add(resume);
        menu.add(button("Restart run", () -> {resetGame(); setPlaying(true);}));
        var physicsButton = button(physicalParticles ? "Particle physics: Jolt" : "Particle physics: visual only", () -> {});
        physicsButton.action(n -> {
            physicalParticles = !physicalParticles;
            effects.physical = physicalParticles;
            effects.rebuild();
            physicsButton.text(physicalParticles ? "Particle physics: Jolt" : "Particle physics: visual only");
        });
        menu.add(physicsButton);
        var lightButton = button(particleLights ? "Particle lights: on" : "Particle lights: off", () -> {});
        lightButton.action(n -> {
            particleLights = !particleLights;
            effects.lights = particleLights;
            effects.update(0);
            lightButton.text(particleLights ? "Particle lights: on" : "Particle lights: off");
        });
        menu.add(lightButton);
        var colors = new NanoComboBox<String>().items(List.of("Amber flares", "Cyan flares", "Violet flares")).selectedIndex(palette).onChange(value -> {
            palette = value.startsWith("Amber") ? 0 : value.startsWith("Cyan") ? 1 : 2;
            effects.palette = palette;
        });
        colors.getLayout().height(34).noShrink();
        menu.add(colors);
        slider("Particle light power", 0, 3, particlePower, v -> {particlePower = (float) v; effects.lightGain = particlePower; effects.update(0);});
        slider("Exposure", .5f, 5, exposure, v -> {exposure = (float) v; renderer.setExposure(exposure);});
        slider("Environment", 0, 250, environment, v -> {environment = (float) v; renderer.setEnvironmentIntensity(environment);});
        slider("Key light", 0, 400, keyPower, v -> {keyPower = (float) v; roomLights.getFirst().setIntensity(keyPower);});
        slider("Mouse sensitivity", .5f, 3, sensitivity * 1000, v -> sensitivity = (float) v / 1000);
        menu.add(label("WASD move   Shift sprint   Space jump", 12));
        menu.add(label("Left fire   Right aim   R reload", 12));
        menu.add(label("G grenade   F light flare   Esc console", 12));
        menu.add(button("Exit arena", () -> glfwSetWindowShouldClose(Window.getAddress(), true)));
        ui.add(menu);
    }

    private void setPlaying(boolean value) {
        if (value && game.isDead()) return;
        playing = value;
        lookReady = false;
        jumpQueued = false;
        if (playing) started = true;
        if (menu != null) {
            menu.setVisible(!playing);
            if (playing) ui.setFocusTo(null);
            resume.text(started ? "Resume run" : "Enter arena");
            resume.setEnabled(!game.isDead());
            menuTitle.text(game.isDead() ? "RUN ENDED" : "LIVE FIRE / TEST CONSOLE");
            menuHint.text(game.isDead() ? "Restart to challenge the next run." : "Clear drone waves. Test every impact.");
        }
        if (!smoke && !benchmark) {
            glfwSetInputMode(Window.getAddress(), GLFW_CURSOR, playing ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
            if (glfwRawMouseMotionSupported()) glfwSetInputMode(Window.getAddress(), GLFW_RAW_MOUSE_MOTION, playing ? GLFW_TRUE : GLFW_FALSE);
        }
    }

    private void look(float x, float y) {
        if (!playing) return;
        if (lookReady) {
            // Engine mouse payloads use signed 16-bit positions. Modular deltas
            // preserve relative movement when an unbounded captured cursor wraps.
            yaw += (short) ((int) x - (int) lastMouseX) * sensitivity;
            pitch = Math.clamp(pitch + (short) ((int) y - (int) lastMouseY) * sensitivity, -1.4f, 1.4f);
        }
        lastMouseX = x;
        lastMouseY = y;
        lookReady = true;
    }

    private void updateCamera() {
        game.eyePosition(eye);
        float cp = (float) Math.cos(pitch);
        forward.set((float) Math.sin(yaw) * cp, (float) Math.cos(yaw) * cp, (float) Math.sin(pitch));
        camera.getPosition().set(eye);
        camera.getDirection().set(forward);
        camera.getUp().set(0, 0, 1);
        boolean aim = playing && Mouse.isButtonDown(GLFW_MOUSE_BUTTON_RIGHT);
        camera.setFieldOfViewDegrees(aim ? 48 : fieldOfView);
        weaponTransform.translation(eye).rotateZ(-yaw).rotateX(pitch + recoil * .4f)
                .translate(aim ? -.16f : 0, -recoil, recoil * .25f);
        for (var part : weapon) part.setParentTransform(weaponTransform);
        weaponTransform.transformPosition(muzzle.set(.24f, 1, -.2f));
    }

    private void resize() {
        width = Window.getWidth(); height = Window.getHeight();
        hud.getLayout().absolute().left(0).top(0).width(width).height(height);
        menu.getLayout().left(40).top(Math.max(85, (height - 710) / 2f));
        ui.layout();
    }

    private void tell(String message) {toast = message; toastTime = 1.8f;}

    @Override public void update(float delta) {
        if (width != Window.getWidth() || height != Window.getHeight()) resize();
        ui.update(delta);
        float dt = smoke || benchmark ? 1f / 60 : Math.min(delta, .05f);
        long start = System.nanoTime();
        if (playing) {
            updateCamera();
            if (Mouse.isButtonDown(GLFW_MOUSE_BUTTON_LEFT) && game.fire(eye, forward)) {
                effects.muzzle(eye, muzzle, game.getPlayerBody()); recoil = .07f;
            }
            float move = (Keyboard.isKeyDown(GLFW_KEY_W) ? 1 : 0) - (Keyboard.isKeyDown(GLFW_KEY_S) ? 1 : 0);
            float strafe = (Keyboard.isKeyDown(GLFW_KEY_D) ? 1 : 0) - (Keyboard.isKeyDown(GLFW_KEY_A) ? 1 : 0);
            game.update(dt, move, strafe, Keyboard.isKeyDown(GLFW_KEY_LEFT_SHIFT), jumpQueued, yaw);
            jumpQueued = false;
            effects.update(dt);
            if (game.getHealth() < lastHealth) damageFlash = .3f;
            if (game.getKills() > lastKills) tell("DRONE DOWN  +100");
            lastHealth = game.getHealth(); lastKills = game.getKills();
            if (game.isDead()) setPlaying(false);
        }
        simulationMs = (System.nanoTime() - start) * 1e-6f;
        hitFlash = Math.max(0, hitFlash - dt); damageFlash = Math.max(0, damageFlash - dt);
        recoil = Math.max(0, recoil - dt * .6f); toastTime = Math.max(0, toastTime - dt);
        updateCamera();
        reportTime += delta; reportFrames++;
        if (shownHealth != game.getHealth()) healthText = Integer.toString(shownHealth = game.getHealth());
        if (shownAmmo != game.getAmmo()) ammoText = Integer.toString(shownAmmo = game.getAmmo());
        if (shownReserve != game.getReserve()) reserveText = "/ " + (shownReserve = game.getReserve());
        reloadText = game.isReloading() ? "RELOADING" : "PULSE RIFLE";
        if (reportTime >= .25f) {
            fps = reportFrames / reportTime;
            performance = String.format(Locale.ROOT, "%.0f FPS   %.2f ms render   %.2f ms simulation", fps, renderMs, simulationMs);
            refreshStatus(renderer.getPointLightCount());
            reportTime = 0; reportFrames = 0;
        }
    }

    private void refreshStatus(int lightCount) {
        waveText = String.format(Locale.ROOT, "WAVE %02d", game.getWave());
        scoreText = String.format(Locale.ROOT, "%06d", game.getScore());
        details = effects.particleCount() + " particles  /  " + lightCount + " lights  /  " + game.getBodyCount() + " bodies";
        supplies = "GRENADES " + game.getGrenadesRemaining() + "    SCORE " + scoreText;
        targetText = game.getEnemies() > 0 ? game.getEnemies() + " DRONES REMAINING" : "NEXT WAVE IN " + (int) Math.ceil(game.getWaveCountdown());
    }

    @Override public void render() {
        long start = System.nanoTime();
        Window.clear(BACKGROUND);
        glViewport(0, 0, width, height);
        renderer.render(scene, camera);
        ui.draw();
        if (smoke || benchmark) glFinish();
        renderMs = (System.nanoTime() - start) * 1e-6f;
        frames++;
        if (smoke) smokeFrame();
        if (benchmark) benchmarkFrame();
    }

    private final class Hud extends NanoContainer {
        private final NVGColor ink = NVGColor.create();
        private void fill(long vg, int rgb, float alpha) {
            nvgRGBA((byte) (rgb >>> 16), (byte) (rgb >>> 8), (byte) rgb, (byte) Math.round(alpha * 255), ink);
            nvgFillColor(vg, ink); nvgStrokeColor(vg, ink);
        }
        private void box(long vg, float x, float y, float w, float h, int rgb, float alpha) {
            fill(vg, rgb, alpha); nvgBeginPath(vg); nvgRoundedRect(vg, x, y, w, h, 8); nvgFill(vg);
        }
        private void text(long vg, float x, float y, String text, float size, int rgb, int align) {
            fill(vg, rgb, 1); nvgFontFace(vg, "default"); nvgFontSize(vg, size); nvgTextAlign(vg, align | NVG_ALIGN_TOP); nvgText(vg, x, y, text);
        }
        @Override public void draw(long vg) {
            box(vg, 24, 22, 320, 70, 0x07131F, .88f);
            text(vg, 42, 34, "VALTHORNE  /  LIVE FIRE", 21, 0xEDF5FC, NVG_ALIGN_LEFT);
            text(vg, 42, 65, "PHYSICS + LIGHT COMBAT ARENA", 11, 0x69D9E8, NVG_ALIGN_LEFT);
            box(vg, width / 2f - 130, 22, 260, 70, 0x07131F, .88f);
            text(vg, width / 2f, 31, waveText, 24, 0xFFFFFF, NVG_ALIGN_CENTER);
            text(vg, width / 2f, 63, targetText, 12, 0xFFD181, NVG_ALIGN_CENTER);
            box(vg, width - 444, 22, 420, 70, 0x07131F, .88f);
            text(vg, width - 426, 34, performance, 13, 0xC2D4E6, NVG_ALIGN_LEFT);
            text(vg, width - 426, 60, details, 12, 0x69D9E8, NVG_ALIGN_LEFT);
            box(vg, 24, height - 128, 228, 94, 0x07131F, .9f);
            text(vg, 42, height - 114, "VITALS", 11, 0x91A9BF, NVG_ALIGN_LEFT);
            text(vg, 42, height - 94, healthText, 32, game.getHealth() < 30 ? 0xFF6666 : 0xFFFFFF, NVG_ALIGN_LEFT);
            text(vg, 120, height - 87, "ARMOR INTEGRITY", 10, 0x91A9BF, NVG_ALIGN_LEFT);
            box(vg, 42, height - 48, 192, 4, 0x263E51, 1);
            box(vg, 42, height - 48, 192 * Math.max(0, game.getHealth()) / 100f, 4, 0x55D6BF, 1);
            box(vg, width - 300, height - 138, 276, 104, 0x07131F, .9f);
            text(vg, width - 280, height - 125, reloadText, 12, 0x69D9E8, NVG_ALIGN_LEFT);
            text(vg, width - 280, height - 103, ammoText, 42, 0xFFFFFF, NVG_ALIGN_LEFT);
            text(vg, width - 205, height - 86, reserveText, 21, 0x91A9BF, NVG_ALIGN_LEFT);
            text(vg, width - 280, height - 51, supplies, 11, 0xFFD181, NVG_ALIGN_LEFT);
            if (playing) {
                float cx = width * .5f, cy = height * .5f, gap = 6 + recoil * 110;
                fill(vg, 0xDBFAFF, .9f); nvgStrokeWidth(vg, 1.5f); nvgBeginPath(vg);
                nvgMoveTo(vg, cx - gap - 7, cy); nvgLineTo(vg, cx - gap, cy);
                nvgMoveTo(vg, cx + gap, cy); nvgLineTo(vg, cx + gap + 7, cy);
                nvgMoveTo(vg, cx, cy - gap - 7); nvgLineTo(vg, cx, cy - gap);
                nvgMoveTo(vg, cx, cy + gap); nvgLineTo(vg, cx, cy + gap + 7); nvgStroke(vg);
                if (hitFlash > 0) {
                    fill(vg, 0xFFD181, 1); nvgBeginPath(vg);
                    for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) {
                        nvgMoveTo(vg, cx + sx * 13, cy + sy * 13); nvgLineTo(vg, cx + sx * 20, cy + sy * 20);
                    }
                    nvgStroke(vg);
                }
                text(vg, width / 2f, height - 28, "WASD move   SHIFT sprint   SPACE jump   R reload   G grenade   F light flare   ESC console", 12, 0xC2D4E6, NVG_ALIGN_CENTER);
            }
            if (toastTime > 0) text(vg, width / 2f, height * .64f, toast, 17, 0xFFD181, NVG_ALIGN_CENTER);
            if (game.isReloading()) box(vg, width / 2f - 55, height / 2f + 44, 110 * game.getReloadProgress(), 3, 0x69D9E8, 1);
            if (damageFlash > 0) {
                fill(vg, 0xFF3A3A, damageFlash * 1.5f); nvgStrokeWidth(vg, 18); nvgBeginPath(vg); nvgRect(vg, 4, 4, width - 8, height - 8); nvgStroke(vg);
            }
            if (!playing) {
                box(vg, 0, 0, width, height, 0x020810, .32f);
                text(vg, width * .68f, height * .42f, started ? "SIMULATION PAUSED" : "ENTER THE ARENA", 36, 0xFFFFFF, NVG_ALIGN_CENTER);
                text(vg, width * .68f, height * .42f + 54, "Reactive props. Bouncing debris. Lights in motion.", 17, 0xC2D4E6, NVG_ALIGN_CENTER);
            }
        }
    }

    private void capture(String name) {
        try {
            var bytes = BufferUtils.createByteBuffer(width * height * 4);
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
            var result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int at = (y * width + x) * 4;
                result.setRGB(x, height - y - 1, (bytes.get(at) & 255) << 16 | (bytes.get(at + 1) & 255) << 8 | (bytes.get(at + 2) & 255));
            }
            var path = Path.of("build/fps-arena", name);
            Files.createDirectories(path.getParent()); ImageIO.write(result, "png", path.toFile());
        } catch (Exception e) {throw new IllegalStateException("FPS capture failed", e);}
    }

    private Vector3f smokeStart;
    private int smokeAmmo, smokeGrenades, maximumParticles, maximumLights;
    private long smokeStep;

    private void key(int code, int action) {
        long window = Window.getAddress();
        var callback = glfwSetKeyCallback(window, null);
        glfwSetKeyCallback(window, callback);
        callback.invoke(window, code, 0, action, 0);
    }

    private void tap(int code) {key(code, GLFW_PRESS); key(code, GLFW_RELEASE);}

    private void pointer(float x, float y, int button, int action) {
        long window = Window.getAddress();
        var cursor = glfwSetCursorPosCallback(window, null);
        glfwSetCursorPosCallback(window, cursor);
        cursor.invoke(window, x, height - y);
        if (button >= 0) {
            var callback = glfwSetMouseButtonCallback(window, null);
            glfwSetMouseButtonCallback(window, callback);
            callback.invoke(window, button, action, 0);
        }
    }

    private static NanoButton findButton(UINode node, String text) {
        if (node instanceof NanoButton b && b.getText().equals(text)) return b;
        if (node instanceof UIContainer c) for (var child : c.getChildren()) {
            var found = findButton(child, text); if (found != null) return found;
        }
        return null;
    }

    private void click(String text) {
        var button = findButton(ui, text);
        require(button != null, "Missing button: " + text);
        float x = button.getAbsoluteX() + button.getWidth() / 2;
        float y = height - button.getAbsoluteY() - button.getHeight() / 2;
        pointer(x, y, GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
        pointer(x, y, GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private void smokeFrame() {
        maximumParticles = Math.max(maximumParticles, effects.particleCount());
        maximumLights = Math.max(maximumLights, renderer.getPointLightCount());
        if (frames == 1) capture("console.png");
        if (frames == 2) {
            click("Enter arena");
            require(playing, "Enter arena did not start");
            game.setWavesEnabled(false); game.clearDrones();
            smokeStart = new Vector3f(eye);
            key(GLFW_KEY_W, GLFW_PRESS);
        }
        if (frames == 32) {
            key(GLFW_KEY_W, GLFW_RELEASE);
            require(eye.y - smokeStart.y > 1.5f, "WASD did not move the player");
            smokeStart.set(eye);
            tap(GLFW_KEY_SPACE);
            require(jumpQueued, "Space was consumed before gameplay");
        }
        if (frames == 43) require(eye.z > smokeStart.z + .3f, "Jump input failed: eye=" + eye + ", start=" + smokeStart + ", grounded=" + game.isGrounded());
        if (frames == 65) pointer(800, 480, -1, 0);
        if (frames == 66) pointer(870, 500, -1, 0);
        if (frames == 67) {
            require(Math.abs(yaw) > .1f && pitch > 0, "Mouse look failed: yaw=" + yaw + ", pitch=" + pitch + ", ready=" + lookReady + ", playing=" + playing);
            pointer(870, 500, GLFW_MOUSE_BUTTON_RIGHT, GLFW_PRESS);
        }
        if (frames == 68) {
            require(camera.getFieldOfViewDegrees() == 48, "Aim zoom failed");
            pointer(870, 500, GLFW_MOUSE_BUTTON_RIGHT, GLFW_RELEASE);
            yaw = pitch = 0;
        }
        if (frames == 69) {
            lookReady = false;
            pointer(32760, 500, -1, 0); pointer(32776, 500, -1, 0);
            require(yaw > 0 && yaw < .1f, "Captured mouse coordinate wrap caused a camera jump");
            yaw = pitch = 0; lookReady = false;
        }
        if (frames == 71) game.spawnDrone(eye.x, eye.y + 4, eye.z);
        if (frames == 72) {
            smokeAmmo = game.getAmmo();
            pointer(width / 2f, height / 2f, GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
        }
        if (frames == 98) {
            pointer(width / 2f, height / 2f, GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
            require(game.getAmmo() < smokeAmmo && game.getKills() >= 1, "Shooting did not damage a drone");
            tap(GLFW_KEY_R);
            require(game.isReloading(), "Reload key failed");
        }
        if (frames == 110) {tap(GLFW_KEY_F); require(effects.flareCount() > 0, "Flare key failed");}
        if (frames == 112) {
            smokeGrenades = game.getGrenadesRemaining(); tap(GLFW_KEY_G);
            require(game.getGrenadesRemaining() == smokeGrenades - 1, "Grenade key failed");
        }
        if (frames == 140) {
            key(GLFW_KEY_ESCAPE, GLFW_PRESS);
            require(!playing, "Escape did not pause");
            smokeStep = game.getPhysics().getStepCount();
            key(GLFW_KEY_ESCAPE, GLFW_REPEAT);
            require(!playing, "Repeated Escape resumed the game");
            key(GLFW_KEY_ESCAPE, GLFW_RELEASE);
        }
        if (frames == 142) {
            require(game.getPhysics().getStepCount() == smokeStep, "Paused game continued stepping");
            click("Particle lights: on");
            require(!effects.lights && effects.attachedLightCount() == 0, "Particle light toggle failed");
            click("Particle physics: Jolt");
            require(!effects.physical && effects.particleCount() == 0, "Physics toggle did not clear effects");
        }
        if (frames == 143) {
            click("Particle physics: visual only"); click("Particle lights: off");
            click("Amber flares"); click("Cyan flares");
            require(effects.physical && effects.lights && effects.palette == 1, "Console mode/color edits failed");
        }
        if (frames == 145) {capture("console-edited.png"); click("Resume run"); require(playing, "Resume failed");}
        if (frames == 148) tap(GLFW_KEY_F);
        if (frames == 210) {
            require(!game.isReloading() && game.getAmmo() == smokeAmmo, "Reload did not complete");
            require(renderer.getPointLightCount() > roomLights.size(), "Attached lights are missing from Filament");
            capture("combat.png");
        }
        if (frames == 280) {
            require(game.getGrenadeCount() == 0, "Grenade did not expire/explode");
            require(eye.isFinite() && game.getHealth() > 0 && maximumParticles > 12 && maximumLights > 4, "Combat validation incomplete");
            var previousWorld = game.getPhysics();
            tap(GLFW_KEY_ESCAPE); click("Restart run");
            require(previousWorld.isClosed() && playing && game.getHealth() == 100, "Restart leaked or failed");
            require(toastTime == 0 && scoreText.equals("000000") && supplies.startsWith("GRENADES 4"), "Restart retained stale combat HUD state");
        }
        if (frames == 290) {
            require(glGetError() == GL_NO_ERROR, "FPS renderer GL error");
            capture("ready.png");
            System.out.println("FPS_INPUT_VALIDATED movement, jump, mouse look, aim, shooting, reload, grenades, flares, lighting/physics/color edits, pause/repeat isolation and restart; peak particles=" + maximumParticles + ", peak lights=" + maximumLights);
            glfwSetWindowShouldClose(Window.getAddress(), true);
        }
    }

    private void benchmarkFrame() {
        long actionStart = System.nanoTime();
        if (frames == 1) {game.setWavesEnabled(false); game.clearDrones(); game.spawnDrone(-7, 14, 1.7f); game.spawnDrone(7, 16, 1.7f);}
        yaw = (float) Math.sin(frames * .012) * .28f;
        pitch = -.03f;
        if (frames % 8 == 0 && game.fire(eye, forward)) {effects.muzzle(eye, muzzle, game.getPlayerBody()); recoil = .07f;}
        if (game.getAmmo() == 0) game.reload();
        if (frames % 45 == 0) effects.flare(eye, forward, game.getPlayerBody());
        if (frames == 100 || frames == 260) game.throwGrenade(eye, forward);
        // Include scripted firing, raycasts and effect births, which run here
        // instead of through real input callbacks in this repeatable workload.
        double actionMs = (System.nanoTime() - actionStart) * 1e-6;
        maximumParticles = Math.max(maximumParticles, effects.particleCount());
        maximumLights = Math.max(maximumLights, renderer.getPointLightCount());
        if (frames > 90 && frames <= 450) {
            renderSamples[frames - 91] = renderMs; simulationSamples[frames - 91] = simulationMs + actionMs;
        }
        if (frames == 450) {
            require(!game.isDead() && eye.isFinite() && glGetError() == GL_NO_ERROR, "Invalid FPS benchmark state");
            report("render", renderSamples); report("simulation", simulationSamples);
            capture("benchmark-" + physicalParticles + "-" + particleLights + ".png");
            System.out.println("FPS_BENCHMARK_VALIDATED physical=" + physicalParticles + ", particleLights=" + particleLights + ", peakParticles=" + maximumParticles + ", peakLights=" + maximumLights + ", bodies=" + game.getBodyCount() + ", cachedMeshes=" + renderer.getCachedMeshCount());
            glfwSetWindowShouldClose(Window.getAddress(), true);
        }
    }

    private static void report(String name, double[] samples) {
        Arrays.sort(samples);
        System.out.printf(Locale.ROOT, "%s mean %.3f ms / median %.3f ms / p95 %.3f ms; %s%n", name, Arrays.stream(samples).average().orElseThrow(), samples[samples.length / 2], samples[(int) (samples.length * .95)], glGetString(GL_RENDERER));
    }

    @Override public void dispose() {
        glfwSetInputMode(Window.getAddress(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        Keyboard.removeKeyListener(keys); Keyboard.removeKeyListener(keyEdges); Mouse.removeMouseListener(mouse);
        JGL.unsubscribe(EventTypes.WINDOW_FOCUS, focus);
        effects.close(); game.close(); renderer.close(); models.close(); ui.dispose(); theme.close();
    }
}
