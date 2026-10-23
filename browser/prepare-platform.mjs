import { readFile, writeFile, mkdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const output = path.join(root, "browser/build/generated/platform");
function mask(source) {
    return source.replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'/g, (value) =>
        value.replace(/[^\n]/g, " "),
    );
}
function closing(source, start, open = "(", close = ")") {
    const text = mask(source);
    let depth = 0;
    for (let i = start; i < text.length; i++) {
        if (text[i] === open) depth++;
        else if (text[i] === close && --depth === 0) return i;
    }
    throw new Error("Unbalanced platform adaptation");
}
function replace(source, before, after) {
    if (!source.includes(before)) throw new Error("Review browser adaptation: " + before);
    return source.replace(before, after);
}
function callbackInterfaces(source) {
    source = source.replaceAll("GLFWDropCallback.getName(", "GLFW.browserDropName(");
    for (const name of [
        "WindowFocus",
        "WindowClose",
        "Drop",
        "WindowPos",
        "WindowSize",
        "FramebufferSize",
        "Char",
        "Key",
        "CursorPos",
        "MouseButton",
        "Scroll",
    ]) {
        const type = "GLFW" + name + "Callback";
        const pattern = type + ".create(";
        while (source.includes(pattern)) {
            const start = source.indexOf(pattern),
                open = start + pattern.length - 1,
                end = closing(source, open);
            source = source.slice(0, start) + source.slice(open + 1, end) + source.slice(end + 1);
        }
        source = source.replace(new RegExp("\\b" + type + "\\b", "g"), type + "I");
    }
    return source.replaceAll("GLFWImage.Buffer", "GLFWImageBuffer");
}
for (const name of [
    "Audio",
    "AudioRuntime",
    "audio/SoundPlayer",
    "audio/SoundData",
    "graphics/shader/ComputeBuffer",
    "ui/behavior/TextEditing",
    "ui/behavior/TextEditModel",
    "ui/nodes/nano/NanoHyperlink",
    "ui/NanoUtility",
    "JGL",
    "PlatformTools",
    "JGLConfiguration",
    "Window",
    "Keyboard",
    "Mouse",
    "graphics/font/slug/SlugOutlines",
    "graphics/font/slug/SlugData",
    "graphics/texture/TextureData",
    "graphics/bgfx/BgfxGraphics",
    "graphics/bgfx/BgfxBatchRenderer",
    "graphics/bgfx/BgfxMesh",
    "graphics/bgfx/BgfxVertexBuffer",
    "graphics/bgfx/BgfxMeshRenderer",
]) {
    let source = await readFile(path.join(root, "src/main/java/valthorne", name + ".java"), "utf8");
    source = callbackInterfaces(source);
    if (name === "graphics/font/slug/SlugOutlines") {
        source = source
            .replace(
                "import org.lwjgl.stb.STBTTVertex;",
                "import org.lwjgl.stb.STBTTVertex;\nimport org.lwjgl.stb.STBTTVertexBuffer;",
            )
            .replaceAll("STBTTVertex.Buffer", "STBTTVertexBuffer");
    }
    if (name === "graphics/font/slug/SlugData" || name === "graphics/texture/TextureData") {
        source = source.replace("import java.nio.file.Files;", "import valthorne.browser.BrowserAssets;");
        source = source.replaceAll("Files.readAllBytes(Path.of(path))", "BrowserAssets.read(path)");
    }
    if (name === "graphics/texture/TextureData") {
        source = source.replaceAll("\r\n", "\n");
        source = source.replace(/^import java\.util\.(?:Collections|IdentityHashMap|Set);\s*$/gm, "");
        source = source.replace(/^import static org\.lwjgl\.system\.MemoryUtil\.memAddress0;\s*$/gm, "");
        const field = source.indexOf("    private static final Set<TextureData> nativeImages");
        if (field < 0) throw new Error("Review browser pixel-buffer ownership");
        const fieldEnd = source.indexOf("\n", field);
        source = source.slice(0, source.lastIndexOf("    /**", field)) + source.slice(fieldEnd + 1);
        source = replace(source,
            `        TextureData decoded = new TextureData(image, width, height);
        synchronized (STB_LOCK) {
            nativeImages.add(decoded);
        }
        return decoded;`,
            "        return new TextureData(image, width, height);");
        source = replace(source,
            `         * Track the owning record by identity so borrowed views of the same
         * buffer cannot release an allocation owned by a factory result.`,
            `         * The browser decoder copies pixels into Java-managed storage and
         * releases its native image allocation before returning.`);
        source = replace(source,
            ` * order. The loading factories own their native pixel allocations until
 * {@link #dispose()}. Explicitly supplied buffers are borrowed: their original
 * allocator or garbage collector controls their lifetime. Disposing such a view
 * never frees caller-owned storage.</p>`,
            ` * order. Browser decoders copy native pixels into Java-managed storage and release
 * their native image allocation before returning. Returned buffers follow JavaScript
 * garbage collection. Explicitly supplied buffers remain borrowed, and
 * {@link #dispose()} never frees caller-owned storage.</p>`);
        const start = source.indexOf("    public void dispose() {"),
            begin = source.indexOf("{", start),
            end = closing(source, begin, "{", "}");
        source =
            source.slice(0, source.lastIndexOf("    /**", start)) +
            `    /**
     * Completes decoded-data ownership. Native STB storage was already freed by
     * the browser decoder; the Java buffer is reclaimed when owners release it.
     * Repeated disposal is safe and never retains pixels in a global registry.
     */
    public void dispose() {
        // Decoded browser storage follows JavaScript garbage-collection ownership.
    }` +
            source.slice(end + 1);
    }
    if (name === "JGLConfiguration")
        source = replace(
            source,
            "GraphicsBackend graphicsBackend = GraphicsBackend.OPENGL",
            "GraphicsBackend graphicsBackend = GraphicsBackend.WEBGL",
        );
    if (name === "graphics/bgfx/BgfxGraphics")
        source = source.replaceAll("GraphicsBackend.OPENGL", "GraphicsBackend.WEBGL");
    if (name === "JGL") {
        source = source.replace(
            "import valthorne.graphics.bgfx.BgfxGraphics;",
            "import valthorne.graphics.bgfx.BgfxGraphics;\nimport valthorne.browser.BrowserRuntime;",
        );
        source = source
            .replaceAll("import java.util.concurrent.BlockingDeque;", "import java.util.ArrayDeque;")
            .replaceAll("import java.util.concurrent.LinkedBlockingDeque;", "")
            .replaceAll("BlockingDeque<Runnable>", "ArrayDeque<Runnable>")
            .replaceAll("new LinkedBlockingDeque<>()", "new ArrayDeque<>()");
        source = replace(
            source,
            "                double now = glfwGetTime();",
            "                glfwPollEvents();\n                double now = glfwGetTime();",
        );
        source = replace(
            source,
            "                glfwPollEvents();\n                Window.dispatchCloseRequest();",
            "                Window.dispatchCloseRequest();",
        );
    }
    if (name === "Audio") {
        source = source.replace(/^import (?:static )?org\.lwjgl\.[^;]+;\s*$/gm, "");
        source = source.replace("import java.nio.IntBuffer;", "import valthorne.browser.audio.BrowserAudio;");
        const begin = source.indexOf("    public static List<String> getDevices()");
        const end = source.lastIndexOf("\n}");
        if (begin < 0 || end < 0) throw new Error("Review browser audio endpoint API");
        // Keep the existing lifecycle and command API; browser endpoint operations
        // use Web Audio promises on the captured audio coroutine.
        source =
            source.slice(0, begin) +
            `    public static List<String> getDevices() {
        /*
         * Enumerate only outputs already exposed by the browser's permission policy.
         */
        return call(BrowserAudio::devices);
    }

    /**
     * Returns the browser's system-default output identifier.
     * @return the stable default endpoint token
     */
    public static String getDefaultDevice() {
        /*
         * The browser resolves this token against the current operating-system default.
         */
        return "default";
    }

    /**
     * Reads the active Web Audio sink without recreating players.
     * @return active endpoint identifier
     */
    public static String getCurrentDevice() {
        /*
         * Sink state belongs to the captured ready session.
         */
        return call(BrowserAudio::device);
    }

    /**
     * Checks whether this browser supports preserving resources while changing outputs.
     * @return whether AudioContext.setSinkId is available
     */
    public static boolean isDeviceSwitchingSupported() {
        /*
         * Capability detection does not request microphone or output-selection permission.
         */
        return call(BrowserAudio::supportsSwitching);
    }

    /**
     * Selects an already authorized output; unsupported or denied selection returns false.
     * @param deviceName endpoint identifier, or null for the system default
     * @return whether selection completed
     */
    public static boolean switchDevice(String deviceName) {
        /*
         * Await the actual browser promise before reporting success to callers.
         */
        if (deviceName != null && (deviceName.isBlank() || deviceName.indexOf('\\0') >= 0))
            throw new IllegalArgumentException("Device name must be nonblank and contain no NUL characters");
        return call(() -> BrowserAudio.switchDevice(deviceName));
    }
` +
            source.slice(end);
        source = source.replaceAll("OpenAL", "Web Audio").replaceAll("native audio", "browser audio");
    }
    if (name === "AudioRuntime") {
        source = source.replace(/^import (?:static )?org\.lwjgl\.[^;]+;\s*$/gm, "");
        source = source.replace(/^import java\.nio\.[^;]+;\s*$/gm, "");
        source = source.replace(
            "import java.util.concurrent.ConcurrentLinkedQueue;",
            "import java.util.ArrayDeque;\nimport valthorne.browser.audio.BrowserAudio;",
        );
        source = source.replace("import java.util.concurrent.locks.LockSupport;", "");
        source = source.replaceAll("ConcurrentLinkedQueue", "ArrayDeque");
        for (const field of ["device", "context", "threadContext", "contextCurrent"])
            source = source.replace(new RegExp("^    private (?:long|boolean) " + field + ";[^\\n]*\\n", "m"), "");
        let start = source.indexOf("    long device() {"),
            end = closing(source, source.indexOf("{", start), "{", "}");
        source = source.slice(0, source.lastIndexOf("    /**", start)) + source.slice(end + 1);
        start = source.indexOf("            device = alcOpenDevice");
        end = source.indexOf("            synchronized (this)", start);
        source = source.slice(0, start) + "            BrowserAudio.initialize();\n" + source.slice(end);
        source = source.replaceAll("LockSupport.unpark(thread);", "wake();");
        source = replace(
            source,
            "if (players.isEmpty() && commands.isEmpty()) LockSupport.park();\n                else LockSupport.parkNanos(5_000_000L);",
            "awaitWork();",
        );
        source = replace(source, "shutdownOpenAL();", "BrowserAudio.close();");
        start = source.indexOf("    private void shutdownOpenAL() {");
        end = closing(source, source.indexOf("{", start), "{", "}");
        source =
            source.slice(0, start) +
            `    private synchronized void awaitWork() throws InterruptedException {
        if (state == AudioState.READY && commands.isEmpty()) wait(players.isEmpty() ? 0 : 5);
    }

    /**
     * Resumes the cooperative owner when work arrives or shutdown closes admission.
     */
    private synchronized void wake() {
        notifyAll();
    }` +
            source.slice(end + 1);
        source = source.replace(
            "Releases context ownership, context, and device in nested cleanup scopes so\n     * later resources are attempted even if an earlier cleanup operation throws.",
            "Suspends an idle owner until a command arrives or a player update becomes due.\n     * @throws InterruptedException if the cooperative owner is interrupted",
        );
        source = source.replaceAll("OpenAL", "Web Audio");
    }
    if (name === "audio/SoundPlayer") {
        source = source.replace(
            "import static org.lwjgl.openal.AL10.*;",
            "import static valthorne.browser.audio.BrowserAL.*;",
        );
        source = source.replace("import static org.lwjgl.openal.AL11.AL_SEC_OFFSET;", "");
        source = source.replaceAll("OpenAL", "Web Audio");
    }
    if (name === "audio/SoundData") {
        source = source.replace(
            "import valthorne.Audio;",
            "import valthorne.Audio;\nimport valthorne.browser.audio.BrowserAudioStreams;",
        );
        for (const [signature, body] of [
            [
                "public static SoundData load(byte[] bytes)",
                "return BrowserAudioStreams.probe(new SoundSource.BytesSource(bytes));",
            ],
            [
                "public static SoundData load(String path)",
                "return BrowserAudioStreams.probe(new SoundSource.PathSource(path));",
            ],
            [
                "public SoundStream openStream()",
                'if (!streaming) throw new IllegalStateException("SoundData is not stream-backed");\n        return new BrowserPcmStream(this);',
            ],
        ]) {
            const start = source.indexOf(signature),
                begin = source.indexOf("{", start),
                end = closing(source, begin, "{", "}");
            if (start < 0) throw new Error("Review browser sound data API");
            source = source.slice(0, begin + 1) + "\n        " + body + "\n    " + source.slice(end);
        }
    }
    if (name === "Window") {
        const marker = source.indexOf("if (!config.isFullscreen() && !config.isMaximized()) {");
        if (marker < 0) throw new Error("Review browser startup centering");
        const begin = source.indexOf("{", marker),
            end = closing(source, begin, "{", "}");
        source = source.slice(0, marker) + source.slice(end + 1);
    }
    if (name === "ui/behavior/TextEditing") {
        source = source.replace(/^import java\.awt\.[^;]+;\s*$/gm, "");
        source = source.replace(
            "import java.io.IOException;",
            "import java.io.IOException;\nimport valthorne.browser.BrowserClipboard;",
        );
        for (const [method, body] of [
            [
                "copyText",
                "        if (text == null || text.isEmpty()) return false;\n        return BrowserClipboard.write(text);",
            ],
            [
                "paste",
                "        String value = BrowserClipboard.read();\n        if (value != null) model.insert(value);",
            ],
        ]) {
            const signature = new RegExp("public static [^\\n]+ " + method + "\\(").exec(source);
            if (!signature) throw new Error("Review browser clipboard adaptation");
            const begin = source.indexOf("{", signature.index),
                end = closing(source, begin, "{", "}");
            source = source.slice(0, begin + 1) + "\n" + body + "\n    " + source.slice(end);
        }
    }
    if (name === "ui/behavior/TextEditModel") {
        source = source.replaceAll("\r\n", "\n");
        source = replace(source, "import java.util.regex.Pattern;", "import valthorne.browser.BrowserGraphemes;");
        const field = source.indexOf("    private static final Pattern GRAPHEME");
        if (field < 0) throw new Error("Review browser grapheme segmentation");
        source = source.slice(0, source.lastIndexOf("    /**", field)) + source.slice(source.indexOf("\n", field) + 1);
        source = replace(source,
            `        boundaryCount = 1;
        boundaries[0] = 0;
        var matcher = GRAPHEME.matcher(text);
        while (matcher.find()) {
            if (boundaryCount == boundaries.length)
                boundaries = Arrays.copyOf(boundaries, boundaries.length * 2);
            boundaries[boundaryCount++] = matcher.end();
        }`,
            `        int count = BrowserGraphemes.fill(text, boundaries);
        if (count > boundaries.length) {
            boundaries = Arrays.copyOf(boundaries, Math.max(count, boundaries.length * 2));
            count = BrowserGraphemes.fill(text, boundaries);
        }
        boundaryCount = count;`);
        source = source.replace("non-ASCII text uses the cached matcher result.", "non-ASCII text uses the cached browser segmentation result.");
    }
    if (name === "ui/nodes/nano/NanoHyperlink") {
        source = source.replace(/^import java\.awt\.Desktop;\s*$/gm, "import valthorne.browser.BrowserRuntime;");
        source = source.replace(/^import java\.(?:io\.IOException|net\.URI|net\.URISyntaxException);\s*$/gm, "");
        const method = source.indexOf("    private boolean openInBrowser(String link) {");
        if (method < 0) throw new Error("Review browser hyperlink navigation");
        const begin = source.indexOf("{", method), end = closing(source, begin, "{", "}");
        source = source.slice(0, source.lastIndexOf("    /**", method)) + `    /**
     * Requests safe browser navigation during the current activation callback.
     * Pop-up policy remains controlled by the browser; no OS process is started.
     *
     * @param link destination URL relative to the hosting page
     * @return whether a supported navigation request was dispatched
     */
    private boolean openInBrowser(String link) {
        return BrowserRuntime.openLink(link);
    }` + source.slice(end + 1);
    }
    if (name === "ui/NanoUtility")
        source = replace(
            source,
            "return loadFont(vg, name, ValthorneFiles.extractToTempPath(resource));",
            "return loadFont(vg, name, resource);",
        );
    if (name === "Keyboard") {
        source = source.replace(/^import (?:static )?java\.awt\.[^;]+;\s*$/gm, "");
        source = replace(source, "Toolkit.getDefaultToolkit().getLockingKeyState(VK_CAPS_LOCK)", "browserCapsLock()");
    }
    if (name === "graphics/shader/ComputeBuffer") {
        source = source.replace(/^import static org\.lwjgl\.system\.MemoryUtil\.mem(?:Address|Copy|Set);\n/gm, "");
        source = replace(source, "            else memSet(memAddress(recovery), 0, size);", "");
        source = replace(
            source,
            "memCopy(memAddress(bytes), memAddress(recovery) + offset, bytes.remaining());",
            "recovery.put(offset, bytes, bytes.position(), bytes.remaining());",
        );
    }
    if (name === "graphics/bgfx/BgfxBatchRenderer") {
        const begin = source.indexOf("        long source = memAddress(records);");
        const end = source.indexOf("        shader.bgfx().apply(", begin);
        if (begin < 0 || end < 0) throw new Error("Review browser batch upload");
        source =
            source.slice(0, begin) +
            `        for (int i = 0; i < count; i++)
            staging.put(i * instanceStride, records, records.position() + i * sourceStride, sourceStride);
        staging.position(0).limit(count * instanceStride);
        bgfx_update_dynamic_vertex_buffer(chunks[chunkIndex], offset, bgfx_copy(staging));
        staging.clear();
` +
            source.slice(end);
    }
    if (name === "graphics/bgfx/BgfxMeshRenderer") {
        source = source.replace(
            "import org.lwjgl.bgfx.BGFXTransientVertexBuffer;",
            "import valthorne.browser.BrowserNative;\nimport static valthorne.browser.BrowserOperation.TRANSIENT_SET;",
        );
        const begin = source.indexOf("        try (MemoryStack stack = MemoryStack.stackPush()) {");
        const end = closing(source, source.indexOf("{", begin), "{", "}");
        source =
            source.slice(0, begin) +
            `        shader.bgfx().apply(BgfxGraphics.textures(), 16);
        BrowserNative.word(0, count);
        BrowserNative.word(1, (int) layout.address());
        BrowserNative.word(2, 0);
        if (BrowserNative.callFloats(TRANSIENT_SET, vertices) == 0)
            throw new IllegalStateException("Could not upload transient browser geometry");
        bgfx_set_state(state, 0);
        bgfx_submit(BgfxGraphics.view(), shader.bgfx().handle(), 0, BGFX_DISCARD_ALL);` +
            source.slice(end + 1);
    }
    if (name === "graphics/bgfx/BgfxMesh") {
        source = source.replace("import static org.lwjgl.system.MemoryUtil.memAddress;", "");
        source = replace(
            source,
            "long memory = nbgfx_copy(memAddress(data) - size, size);",
            "BGFXMemory memory = bgfx_copy(source);",
        );
        source = replace(source, "if (memory == 0)", "if (memory == null)");
        source = replace(
            source,
            "nbgfx_create_vertex_buffer(memory, layout.address(), (short) BGFX_BUFFER_NONE)",
            "bgfx_create_vertex_buffer(memory, layout, BGFX_BUFFER_NONE)",
        );
        source = source.replace(
            "import org.lwjgl.bgfx.BGFXVertexLayout;",
            "import org.lwjgl.bgfx.BGFXVertexLayout;\nimport org.lwjgl.bgfx.BGFXMemory;",
        );
    }
    if (name === "graphics/bgfx/BgfxVertexBuffer") {
        source = source.replace("import static org.lwjgl.system.MemoryUtil.memAddress;", "");
        source = source.replace(
            "import org.lwjgl.bgfx.BGFXVertexLayout;",
            "import org.lwjgl.bgfx.BGFXVertexLayout;\nimport org.lwjgl.bgfx.BGFXMemory;",
        );
        source = replace(
            source,
            "long memory = nbgfx_copy(memAddress(bytes) - size, size);",
            "bytes.flip();\n        BGFXMemory memory = bgfx_copy(bytes);\n        bytes.position(size).limit(bytes.capacity());",
        );
        source = replace(source, "if (memory == 0)", "if (memory == null)");
        source = replace(
            source,
            "nbgfx_update_dynamic_vertex_buffer(handle, 0, memory)",
            "bgfx_update_dynamic_vertex_buffer(handle, 0, memory)",
        );
    }
    const destination = path.join(output, "valthorne", name + ".java");
    await mkdir(path.dirname(destination), { recursive: true });
    await writeFile(destination, source);
}

await import("./prepare-physics.mjs");
