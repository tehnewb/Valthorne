import { readFileSync, writeFileSync, mkdirSync, readdirSync, existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { prepareAudio } from './prepare-audio.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const output = path.join(root, "android/build/generated/platform");
const javaOutput = path.join(output, "java");
const cppOutput = path.join(output, "cpp");
const files = [];
const compute = [
    ['short', 'bgfx_create_compute_program(short shader, boolean destroyShader)', ['shader', 'destroyShader ? 1 : 0'], 'return bgfx::createProgram(handle<bgfx::ShaderHandle>(w[0]), w[1] != 0).idx;'],
    ['short', 'bgfx_create_dynamic_index_buffer(int count, int flags)', ['count', 'flags'], 'return bgfx::createDynamicIndexBuffer(w[0], uint16_t(w[1])).idx;'],
    ['short', 'bgfx_create_dynamic_index_buffer_mem(BGFXMemory memory, int flags)', ['memory.pointer()', 'flags'], 'return bgfx::createDynamicIndexBuffer(memory(w[0]), uint16_t(w[1])).idx;'],
    ['void', 'bgfx_update_dynamic_index_buffer(short handle, int offset, BGFXMemory memory)', ['handle', 'offset', 'memory.pointer()'], 'bgfx::update(handle<bgfx::DynamicIndexBufferHandle>(w[0]), w[1], memory(w[2])); return 0;'],
    ['void', 'bgfx_destroy_dynamic_index_buffer(short handle)', ['handle'], 'bgfx::destroy(handle<bgfx::DynamicIndexBufferHandle>(w[0])); return 0;'],
    ['void', 'bgfx_set_compute_dynamic_index_buffer(int stage, short handle, int access)', ['stage', 'handle', 'access'], 'bgfx::setBuffer(uint8_t(w[0]), handle<bgfx::DynamicIndexBufferHandle>(w[1]), bgfx::Access::Enum(w[2])); return 0;'],
    ['void', 'bgfx_set_image(int stage, short handle, int mip, int access, int format)', ['stage', 'handle', 'mip', 'access', 'format'], 'bgfx::setImage(uint8_t(w[0]), handle<bgfx::TextureHandle>(w[1]), uint8_t(w[2]), bgfx::Access::Enum(w[3]), bgfx::TextureFormat::Enum(w[4])); return 0;'],
    ['void', 'bgfx_dispatch(int view, short program, int x, int y, int z, int flags)', ['view', 'program', 'x', 'y', 'z', 'flags'], 'bgfx::dispatch(uint16_t(w[0]), handle<bgfx::ProgramHandle>(w[1]), w[2], w[3], w[4], uint8_t(w[5])); return 0;'],
];
function walk(directory) {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
        const file = path.join(directory, entry.name);
        if (entry.isDirectory()) walk(file);
        else if (entry.name.endsWith(".java")) files.push(file);
    }
}
function write(file, source) {
    if (file.endsWith('.java')) {
        source = source.replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*/g, comment => comment.replaceAll('browser coroutine', 'Android render thread').replaceAll('application coroutine', 'application render thread').replaceAll('Application coroutine', 'Application render thread').replaceAll('WebAssembly', 'native').replaceAll('WebGL 2', 'OpenGL ES').replaceAll('WEBGL', 'OPENGLES').replaceAll('browser', 'Android').replaceAll('Browser', 'Android').replaceAll('canvas', 'surface').replaceAll('JavaScript', 'Android runtime').replaceAll('typed-array view', 'native memory view'));
        source = source.replace('A browser canvas is required', 'An Android presentation surface is required').replace('Use the application graphics coroutine', 'Use the Android application render thread');
        if (file.endsWith('/MemoryUtil.java') || file.endsWith('\\MemoryUtil.java'))
            source = source.replace('allocator, unsafe address, or JNI library is involved.', 'allocator or unsafe address is exposed; JNI consumes these direct buffers.').replaceAll('these backing arrays', 'these direct buffers');
    }
    mkdirSync(path.dirname(file), { recursive: true });
    writeFileSync(file, source);
}
function rename(source) {
    return source.replaceAll("valthorne.browser", "valthorne.android.internal")
        .replaceAll("BrowserNative", "AndroidNative").replaceAll("BrowserOperation", "AndroidOperation")
        .replaceAll("BrowserRuntime", "AndroidRuntime").replaceAll("BrowserAssets", "AndroidAssets")
        .replaceAll("BrowserFontOwner", "AndroidFontOwner").replaceAll("BrowserFontBaker", "AndroidFontBaker")
        .replaceAll("BrowserClipboard", "AndroidClipboard");
}
function body(source, declaration, replacement) {
    const start = source.indexOf(declaration), open = source.indexOf('{', start);
    if (start < 0) throw new Error('Missing Android platform method: ' + declaration);
    let depth = 1, end = open + 1;
    while (depth && end < source.length) { const character = source[end++]; if (character === '{') depth++; else if (character === '}') depth--; }
    if (depth) throw new Error('Unbalanced Android platform method');
    return source.slice(0, open + 1) + '\n' + replacement + '\n    ' + source.slice(end - 1);
}
walk(path.join(root, "browser/src/main/java/org/lwjgl"));
for (const file of files) {
    let source = rename(readFileSync(file, "utf8"));
    if (file.endsWith("/Yoga.java") || file.endsWith("\\Yoga.java")) continue;
    source = source.replace(/^import org\.teavm\.[^;]+;\s*$/gm, "");
    source = source.replace(/^\s*@JSBody\([^\n]*\)\r?\n/gm, "\n");
    if (file.endsWith("STBTTFontinfo.java"))
        source = source.replace("private static native AndroidFontOwner own(int pointer);", "private static AndroidFontOwner own(int pointer) { return new AndroidFontOwner(pointer); }");
    if (file.endsWith('MemoryStack.java')) {
        source = source.replace('return mallocInt(values.length)', 'return (IntBuffer) mallocInt(values.length)').replace('return mallocFloat(values.length)', 'return (FloatBuffer) mallocFloat(values.length)');
    }
    if (path.basename(file) === 'BGFXCaps.java')
        source = source.replace('new BGFXCapsLimits(AndroidNative.word(2), AndroidNative.word(3))', 'new BGFXCapsLimits(AndroidNative.word(2), AndroidNative.word(3), AndroidNative.word(8))');
    if (path.basename(file) === 'BGFXCapsLimits.java')
        source = source.replace('    private final int textures;', '    private final int computeBindings; // Maximum native compute binding slots.\n    private final int textures;').replace('BGFXCapsLimits(int textureSize, int textures)', 'BGFXCapsLimits(int textureSize, int textures, int computeBindings)').replace('        this.textures = textures;', '        this.textures = textures;\n        this.computeBindings = computeBindings;').replace('return 0;', 'return computeBindings;').replace('Reports no compute bindings for the WebGL 2 device.', 'Returns the native compute binding limit.').replace('zero compute bindings', 'maximum compute bindings');
    if (path.basename(file) === 'BGFX.java') {
        source = body(source, 'public static String bgfx_get_renderer_name(int type)', '        return switch (type) {\n            case BGFX_RENDERER_TYPE_OPENGLES -> "OpenGL ES (bgfx)";\n            case BGFX_RENDERER_TYPE_VULKAN -> "Vulkan (bgfx)";\n            default -> throw new IllegalArgumentException("Unsupported Android renderer: " + type);\n        };');
        for (let i = 0; i < compute.length; i++) {
            const [result, declaration, args] = compute[i];
            const argumentsBody = args.map((value, index) => `        AndroidNative.word(${index}, ${value});`).join('\n');
            source = body(source, `public static ${result} ${declaration}`, argumentsBody + `\n        ${result === 'void' ? '' : 'return (short) '}AndroidNative.call(${300 + i});`);
        }
        source = source.replaceAll('Rejects compute operations unavailable in the WebGL 2 specification.', 'Executes native Android compute work after device capability validation.').replaceAll('     * @throws UnsupportedOperationException because WebGL 2 has no compute pipeline\n', '');
    }
    const relative = path.relative(path.join(root, "browser/src/main/java"), file);
    if (relative.endsWith('NanoVG.java') && !source.includes('void nvgShapeAntiAlias(')) {
        const end = source.lastIndexOf('}');
        source = source.slice(0, end) + `    /**
     * Enables or disables native shape edge antialiasing.
     * @param context drawing context
     * @param enabled whether shape edges use antialiasing
     */
    public static void nvgShapeAntiAlias(long context, boolean enabled) {
        AndroidNative.word(0, (int) context);
        AndroidNative.word(1, enabled ? 1 : 0);
        AndroidNative.call(308);
    }
` + source.slice(end);
    }
    write(path.join(javaOutput, relative), source);
}

for (const relative of [
    "valthorne/graphics/bgfx/BgfxMipmaps.java", "valthorne/graphics/bgfx/BgfxNativeLibrary.java",
    "valthorne/graphics/font/adaptive/AdaptiveNativeFace.java",
    "valthorne/browser/BrowserFontBaker.java", "valthorne/browser/BrowserOperation.java",
]) {
    let source = rename(readFileSync(path.join(root, "browser/src/main/java", relative), "utf8"));
    if (relative.endsWith('BrowserFontBaker.java')) source = source.replace('metrics.duplicate().position(1)', '(IntBuffer) metrics.duplicate().position(1)').replace('metrics.duplicate().position(2)', '(IntBuffer) metrics.duplicate().position(2)').replace('AndroidNative.readBytes(address + pixels, packed, packed.capacity())', 'AndroidNative.readAtlasGlyphs(address, pixels, packed, packed.capacity())');
    const destination = relative.replace("valthorne/browser/", "valthorne/android/internal/").replace("Browser", "Android");
    write(path.join(javaOutput, destination), source);
}

let device = rename(readFileSync(path.join(root, 'browser/src/main/java/valthorne/graphics/bgfx/BgfxDesktopWindow.java'), 'utf8'));
device = device.replace('    private int width;', '    private GraphicsBackend backend; // Selected native Android graphics API.\n    private int width;');
device = device.replace('        this.handle = handle;', '        this.handle = handle;\n        this.backend = backend;').replaceAll('GraphicsBackend.WEBGL', 'GraphicsBackend.OPENGLES');
device = body(device, 'private void initialize()', '        AndroidRuntime.createDevice(backend);\n        initialized = true;\n        width = AndroidRuntime.pixelWidth();\n        height = AndroidRuntime.pixelHeight();\n        bgfx_reset(width, height, flags, BGFX_TEXTURE_FORMAT_COUNT);\n        BGFXCaps.invalidate();');
device = device.replace('return GraphicsBackend.OPENGLES;', 'return backend;').replaceAll('if (initialized) bgfx_shutdown();', 'if (initialized) AndroidRuntime.destroyDevice();');
device = device.replace('        initialize();\n    }', '        this.backend = backend;\n        initialize();\n    }');
write(path.join(javaOutput, 'valthorne/graphics/bgfx/BgfxDesktopWindow.java'), device);
let platform = rename(readFileSync(path.join(root, 'browser/src/main/java/valthorne/graphics/bgfx/BgfxPlatform.java'), 'utf8'));
platform = platform.replace('backend != GraphicsBackend.WEBGL', 'backend != GraphicsBackend.OPENGLES && backend != GraphicsBackend.VULKAN').replace('The browser runtime requires WEBGL; desktop APIs require the desktop runtime', 'Android requires OPENGLES or VULKAN');
write(path.join(javaOutput, 'valthorne/graphics/bgfx/BgfxPlatform.java'), platform);

// Use already-reviewed portable vertex transfers while retaining native JVM services.
for (const name of ["BgfxGraphics", "BgfxBatchRenderer", "BgfxMeshRenderer", "BgfxMesh", "BgfxVertexBuffer"]) {
    let source = rename(readFileSync(path.join(root, "browser/build/generated/platform/valthorne/graphics/bgfx", name + ".java"), "utf8")).replaceAll("GraphicsBackend.WEBGL", "GraphicsBackend.OPENGLES");
    source = source.replace('source.put(data.duplicate().position(0).limit(size))', 'source.put((ByteBuffer) data.duplicate().position(0).limit(size))');
    write(path.join(javaOutput, "valthorne/graphics/bgfx", name + ".java"), source);
}

for (const name of ["JGL", "JGLConfiguration", "graphics/GraphicsBackend", "Window", "Keyboard", "Mouse", "PlatformTools", "graphics/font/FontData", "graphics/font/adaptive/FontHinting", "graphics/texture/TextureData"]) {
    let source = rename(readFileSync(path.join(root, "browser/build/generated/platform/valthorne", name + ".java"), "utf8"));
    if (name === "JGL") source = source.replaceAll("java.util.ArrayDeque", "java.util.concurrent.LinkedBlockingDeque").replaceAll("ArrayDeque<Runnable>", "LinkedBlockingDeque<Runnable>").replaceAll("new ArrayDeque<>()", "new LinkedBlockingDeque<>()");
    source = source.replaceAll("GraphicsBackend.WEBGL", "GraphicsBackend.OPENGLES");
    if (name === "graphics/GraphicsBackend")
        source = source.replace("return WEBGL;", "return OPENGLES;");
    write(path.join(javaOutput, "valthorne", name + ".java"), source);
}

for (const name of ["asset/Assets", "graphics/font/adaptive/FontGlyphPreparation", "graphics/shader/ComputeBuffer", "ui/UIRoot", "ui/behavior/TextEditing"]) {
    let source = readFileSync(path.join(root, "src/main/java/valthorne", name + ".java"), "utf8");
    source = source.replaceAll("Executors.newVirtualThreadPerTaskExecutor()", "Executors.newFixedThreadPool(Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())))")
        .replaceAll('Thread.ofPlatform().daemon().name("font-preparation-", 0).factory()', 'task -> { Thread worker = new Thread(task, "font-preparation"); worker.setDaemon(true); return worker; }');
    if (name === 'graphics/shader/ComputeBuffer')
        source = source.replace(/^import static org\.lwjgl\.system\.MemoryUtil\.(memAddress|memCopy|memSet);\s*$/gm, '').replace('            else memSet(memAddress(recovery), 0, size);', '').replace('memCopy(memAddress(bytes), memAddress(recovery) + offset, bytes.remaining());', 'recovery.put(offset, bytes, bytes.position(), bytes.remaining());');
    if (name === "ui/UIRoot") {
        source = source.replaceAll("NanoVGBGFX.nvgCreate(true, BgfxGraphics.view(), 0)", "NanoVGBGFX.nvgCreate(true, 0, 0)");
        source = source.replace("import valthorne.graphics.bgfx.BgfxGraphics;", "import valthorne.graphics.bgfx.BgfxGraphics;\nimport valthorne.android.internal.AndroidRuntime;");
        source = source.replace("        if (focused != null) focused.setFocused(true);", "        if (focused != null) focused.setFocused(true);\n        AndroidRuntime.textFocus(focused);");
    }
    if (name === "ui/behavior/TextEditing") {
        source = source.replace(/^import java\.awt\.[^;]+;\s*$/gm, "");
        source = source.replace("import java.io.IOException;", "import java.io.IOException;\nimport valthorne.android.internal.AndroidClipboard;");
        for (const [method, body] of [["boolean copyText", "        return AndroidClipboard.copy(text);"], ["void paste", "        String text = AndroidClipboard.paste();\n        if (text != null) model.insert(text);"]]) {
            const start = source.indexOf("public static " + method + "("), begin = source.indexOf("{", start);
            if (start < 0 || begin < 0) throw new Error("Review Android clipboard method: " + method);
            let depth = 1, end = begin + 1;
            while (depth) { const character = source[end++]; if (character === "{") depth++; else if (character === "}") depth--; }
            source = source.slice(0, begin + 1) + "\n" + body + "\n    " + source.slice(end - 1);
        }
    }
    write(path.join(javaOutput, "valthorne", name + ".java"), source);
}

let yoga = readFileSync(path.join(root, "browser/src/main/java/org/lwjgl/util/yoga/Yoga.java"), "utf8").replaceAll('\r\n', '\n');
for (const entry of readdirSync(path.join(root, 'src/main/java/valthorne/graphics/map/tiled'))) {
    if (!entry.endsWith('.java')) continue;
    let source = readFileSync(path.join(root, 'src/main/java/valthorne/graphics/map/tiled', entry), 'utf8');
    if (!source.includes('javax.xml.stream')) continue;
    source = source.replaceAll('javax.xml.stream.', 'valthorne.android.internal.').replaceAll('XMLInputFactory', 'AndroidXmlFactory').replaceAll('XMLStreamException', 'AndroidXmlException').replaceAll('XMLStreamReader', 'AndroidXmlReader').replaceAll('XMLStreamConstants', 'AndroidXmlReader');
    source = source.replace(/(import valthorne\.android\.internal\.AndroidXmlReader;\s*){2}/g, '$1');
    write(path.join(javaOutput, 'valthorne/graphics/map/tiled', entry), source);
}
const yogaMethods = [...yoga.matchAll(/public static (void|long|float|boolean|int) (YG\w+)\(([^)]*)\) \{[\s\S]*?\n    \}/g)];
if (yogaMethods.length < 30) throw new Error("Review the native Yoga API surface");
const firstYogaMethod = yoga.indexOf("    /**\n     * Applies");
let yogaJava = yoga.slice(0, firstYogaMethod).replace('import org.teavm.jso.JSBody;', '') + '    static { System.loadLibrary("valthorne_android"); }\n';
let yogaCpp = '#include <jni.h>\n#include <yoga/Yoga.h>\n#include <cstdint>\n';
const jniTypes = {void: "void", long: "jlong", float: "jfloat", boolean: "jboolean", int: "jint"};
const enumTypes = {direction: "YGDirection", ownerDirection: "YGDirection", flexDirection: "YGFlexDirection", justifyContent: "YGJustify", alignContent: "YGAlign", alignItems: "YGAlign", alignSelf: "YGAlign", positionType: "YGPositionType", flexWrap: "YGWrap", overflow: "YGOverflow", display: "YGDisplay", edge: "YGEdge", gutter: "YGGutter"};
for (const [, result, name, parameters] of yogaMethods) {
    yogaJava += `\n    /**\n     * Executes ${name} on the owning UI thread.\n     */\n    public static native ${result} ${name}(${parameters});\n`;
    const parts = parameters ? parameters.split(',').map(parameter => parameter.trim().split(/\s+/)) : [];
    const args = parts.map(([type, parameter]) => type === "long" ? parameter === 'index' ? `static_cast<size_t>(${parameter})` : `reinterpret_cast<${parameter === "config" ? "YGConfigRef" : "YGNodeRef"}>(${parameter})` : enumTypes[parameter] ? `static_cast<${enumTypes[parameter]}>(${parameter})` : parameter).join(', ');
    const call = `${name}(${args})`;
    yogaCpp += `extern "C" JNIEXPORT ${jniTypes[result]} JNICALL Java_org_lwjgl_util_yoga_Yoga_${name}(JNIEnv*, jclass${parts.length ? ', ' : ''}${parts.map(([type, parameter]) => jniTypes[type] + ' ' + parameter).join(', ')}) { ${result === 'void' ? call + ';' : result === 'long' ? 'return reinterpret_cast<jlong>(' + call + ');' : 'return ' + call + ';'} }\n`;
}
write(path.join(javaOutput, "org/lwjgl/util/yoga/Yoga.java"), yogaJava + '}\n');
write(path.join(cppOutput, "yoga.cpp"), yogaCpp);

let cpp = readFileSync(path.join(root, "browser/src/main/cpp/runtime.cpp"), "utf8");
cpp = '#include "native_handles.h"\nusing valthorne::pointer;\nusing valthorne::storePointer;\n' + cpp.replace('#include <emscripten/emscripten.h>', '')
    .replaceAll("EMSCRIPTEN_KEEPALIVE ", "");
const start = cpp.indexOf("    case INITIALIZE: {");
const end = cpp.indexOf("    case ADAPTIVE_FONT_CREATE:", start);
cpp = cpp.slice(0, start) + '    case INITIALIZE: throw std::invalid_argument("Android surface owns device initialization");\n' + cpp.slice(end);
cpp = cpp.replace('reinterpret_cast<bgfx::VertexLayout*>(address)', 'static_cast<bgfx::VertexLayout*>(pointer(address))')
    .replace('reinterpret_cast<const bgfx::Memory*>(address)', 'static_cast<const bgfx::Memory*>(pointer(address, true))')
    .replace('reinterpret_cast<BrowserFont*>(pointer)', 'static_cast<BrowserFont*>(valthorne::pointer(pointer))')
    .replace('reinterpret_cast<NVGcontext*>(pointer)', 'static_cast<NVGcontext*>(valthorne::pointer(pointer))')
    .replace('delete layout(w[0])', 'delete static_cast<bgfx::VertexLayout*>(pointer(w[0], true))')
    .replace('auto* value = font(pointer);', 'auto* value = static_cast<BrowserFont*>(valthorne::pointer(pointer, true));')
    .replace('reinterpret_cast<void*>(w[0])', 'pointer(w[0], true)')
    .replace('reinterpret_cast<const stbtt_vertex*>(w[0])', 'static_cast<const stbtt_vertex*>(pointer(w[0]))')
    .replace('reinterpret_cast<stbtt_vertex*>(w[1])', 'static_cast<stbtt_vertex*>(pointer(w[1], true))')
    .replace('nvgDelete(context(w[0]))', 'nvgDelete(static_cast<NVGcontext*>(pointer(w[0], true)))');
cpp = cpp.replace(/reinterpret_cast<uintptr_t>\(([^;\n]+)\)/g, 'storePointer($1)');
cpp = cpp.replace('    switch (operation) {', '    switch (operation) {\n    case 308: nvgShapeAntiAlias(context(w[0]), w[1]); return 0;\n' + compute.map((entry, index) => `    case ${300 + index}: ${entry[3]}`).join('\n'));
cpp = cpp.replace('const auto* caps = bgfx::getCaps();', 'const auto* caps = bgfx::getCaps(); w[8] = caps->limits.maxComputeBindings;');
cpp = cpp.replace('storePointer(uniformInfo.name)', 'storePointer(uniformInfo.name, true)').replace('storePointer(stbi_failure_reason())', 'storePointer(stbi_failure_reason(), true)');
write(path.join(cppOutput, "runtime.cpp"), cpp);
let font = readFileSync(path.join(root, "browser/src/main/cpp/adaptive_font.h"), "utf8");
font = font.replace('reinterpret_cast<BrowserAdaptiveFont*>(words[0])', 'static_cast<BrowserAdaptiveFont*>(pointer(words[0]))')
    .replace('if (operation == ADAPTIVE_FONT_FREE) { delete value;', 'if (operation == ADAPTIVE_FONT_FREE) { pointer(words[0], true); delete value;')
    .replace('reinterpret_cast<uintptr_t>(value->shaped.data())', 'value->shaped.empty() ? 0 : storePointer(value->shaped.data(), true)')
    .replace(/reinterpret_cast<uintptr_t>\(([^;\n]+)\)/g, 'storePointer($1)');
write(path.join(cppOutput, "adaptive_font.h"), font);
for (const name of ["font_resource.h", "operations.h"])
    write(path.join(cppOutput, name), readFileSync(path.join(root, "browser/src/main/cpp", name), "utf8"));
const classes = [];
prepareAudio(root, output);
function collect(directory) {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
        const file = path.join(directory, entry.name);
        if (entry.isDirectory()) collect(file);
        else classes.push(path.relative(javaOutput, file).replaceAll("\\", "/").replace(/\.java$/, ""));
    }
}
collect(javaOutput);
write(path.join(output, "classes.json"), JSON.stringify(classes));
