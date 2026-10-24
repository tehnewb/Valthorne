import { spawnSync } from "node:child_process";
import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const root = path.dirname(fileURLToPath(import.meta.url));
const sourceRoot = path.resolve(root, "../build/browser-tools");
const dependencies = [
    ["bgfx", "https://github.com/LWJGL-CI/bgfx.git", "f564ae8bf92cd2ac27d95b12408bd68be3ddf955"],
    ["bx", "https://github.com/bkaradzic/bx.git", "f3a9adb603c5677731e7cf10622fa16e1dfe9ea5"],
    ["bimg", "https://github.com/bkaradzic/bimg.git", "9bfbd6493b48a15238f2d1374a718ec026524515"],
];
function run(command, args, cwd = root) {
    const result = spawnSync(command, args, {
        cwd,
        stdio: "inherit",
        shell: process.platform === "win32" && command.endsWith(".bat"),
    });
    if (result.error) throw result.error;
    if (result.status !== 0) throw new Error(`${command} failed (${result.status})`);
}
mkdirSync(sourceRoot, { recursive: true });
for (const [name, url, revision] of dependencies) {
    const destination = path.join(sourceRoot, name);
    if (!existsSync(path.join(destination, ".git")))
        run("git", ["clone", "--filter=blob:none", "--no-checkout", url, destination]);
    const current = spawnSync("git", ["rev-parse", "HEAD"], { cwd: destination, encoding: "utf8" }).stdout.trim();
    if (current !== revision || !existsSync(path.join(destination, "LICENSE"))) {
        run("git", ["fetch", "origin", revision, "--depth=1"], destination);
        run("git", ["checkout", "--detach", revision], destination);
    }
}
const sdkArgument = process.argv[2] || process.env.EMSDK;
if (!sdkArgument) throw new Error("Pass the Emscripten SDK path or set EMSDK.");
const sdk = path.resolve(sdkArgument);
if (JSON.parse(readFileSync(path.join(sdk, "upstream/emscripten/emscripten-version.txt"), "utf8")) !== "4.0.23")
    throw new Error("Rebuild this pinned browser runtime with Emscripten 4.0.23.");
const compiler = path.join(sdk, "upstream/emscripten", "em++.py");
const python =
    process.env.EMSDK_PYTHON ||
    (process.platform === "win32"
        ? path.join(
              sdk,
              "python",
              readdirSync(path.join(sdk, "python")).find((name) =>
                  existsSync(path.join(sdk, "python", name, "python.exe")),
              ),
              "python.exe",
          )
        : "python3");
const output = path.join(root, "src/main/resources/native");
mkdirSync(output, { recursive: true });
const licenses = path.join(root, "src/main/resources/META-INF/licenses");
mkdirSync(licenses, { recursive: true });
function writeLicense(name, contents) {
    const text = contents.toString().replaceAll("\r\n", "\n").replace(/[ \t]+$/gm, "").trimEnd();
    writeFileSync(path.join(licenses, name + ".txt"), text + "\n");
}
for (const name of ["bgfx", "bx", "bimg"])
    writeLicense(name, readFileSync(path.join(sourceRoot, name, "LICENSE")));
writeLicense("emscripten", readFileSync(path.join(sdk, "upstream/emscripten/LICENSE")));
for (const [name, file] of [
    ["stb-image", "bimg/3rdparty/stb/stb_image.h"],
    ["stb-truetype", "bgfx/3rdparty/stb/stb_truetype.h"],
    ["stb-image-resize", "bimg/3rdparty/stb/stb_image_resize2.h"],
]) {
    const source = readFileSync(path.join(sourceRoot, file), "utf8");
    const start = source.lastIndexOf("ALTERNATIVE A - MIT License");
    if (start < 0) throw new Error("Review the bundled STB license: " + file);
    writeLicense(name, source.slice(start, source.lastIndexOf("*/")).trim());
}
const nanoSource = readFileSync(path.join(sourceRoot, "bgfx/examples/common/nanovg/nanovg.cpp"), "utf8");
writeLicense("nanovg", nanoSource.slice(0, nanoSource.indexOf("#include")).trim());
// The pinned GLES backend assumes EXT_multisampled_render_to_texture for its
// default framebuffer. WebGL 2 exposes core multisample renderbuffers and blits
// instead. Keep this browser-only adaptation in generated sources, and reject
// upstream changes that alter the reviewed function boundaries.
const generated = path.join(sourceRoot, "generated");
mkdirSync(generated, { recursive: true });
let renderer = readFileSync(path.join(sourceRoot, "bgfx/src/renderer_gl.cpp"), "utf8");
for (const name of ["createMsaaFbo", "destroyMsaaFbo", "blitMsaaFbo"]) {
    const start = renderer.indexOf("void " + name + "(");
    if (start < 0) throw new Error("Review the browser MSAA adaptation: " + name);
    const body = renderer.indexOf("{", start);
    let depth = 1,
        end = body + 1;
    while (depth && end < renderer.length) {
        const value = renderer[end++];
        if (value === "{") depth++;
        else if (value === "}") depth--;
    }
    let method = renderer.slice(start, end);
    const branch = "if (BX_ENABLED(BGFX_CONFIG_RENDERER_OPENGLES))";
    if (method.split(branch).length !== 2) throw new Error("Review the browser MSAA branch: " + name);
    method = method.replace(branch, "if (BX_ENABLED(BGFX_CONFIG_RENDERER_OPENGLES) && !BX_PLATFORM_EMSCRIPTEN)");
    if (name === "blitMsaaFbo")
        method = method.replace(
            "BX_ENABLED(BGFX_CONFIG_RENDERER_OPENGL)",
            "(BX_ENABLED(BGFX_CONFIG_RENDERER_OPENGL) || BX_PLATFORM_EMSCRIPTEN)",
        );
    renderer = renderer.slice(0, start) + method + renderer.slice(end);
}
const rendererFile = path.join(generated, "renderer_gl.cpp");
writeFileSync(rendererFile, renderer);
let amalgamated = readFileSync(path.join(sourceRoot, "bgfx/src/amalgamated.cpp"), "utf8");
if (!amalgamated.includes('#include "renderer_gl.cpp"')) throw new Error("Review bgfx amalgamation includes.");
amalgamated = amalgamated.replace(
    '#include "renderer_gl.cpp"',
    '#include "' + rendererFile.replaceAll("\\", "/") + '"',
);
const amalgamatedFile = path.join(generated, "amalgamated.cpp");
writeFileSync(amalgamatedFile, amalgamated);
const args = [
    "src/main/cpp/runtime.cpp",
    path.join(sourceRoot, "bgfx/examples/common/nanovg/nanovg.cpp"),
    path.join(sourceRoot, "bgfx/examples/common/nanovg/nanovg_bgfx.cpp"),
    amalgamatedFile,
    path.join(sourceRoot, "bx/src/amalgamated.cpp"),
    path.join(sourceRoot, "bimg/src/image.cpp"),
    "-I" + path.join(sourceRoot, "bgfx/include"),
    "-I" + path.join(sourceRoot, "bgfx/src"),
    "-I" + path.join(sourceRoot, "bgfx/examples/common/nanovg"),
    "-I" + path.join(sourceRoot, "bgfx/3rdparty/stb"),
    "-I" + path.join(sourceRoot, "bgfx/3rdparty"),
    "-I" + path.join(sourceRoot, "bimg/3rdparty"),
    "-I" + path.join(sourceRoot, "bgfx/3rdparty/khronos"),
    "-I" + path.join(sourceRoot, "bx/include"),
    "-I" + path.join(sourceRoot, "bimg/include"),
    "-I" + path.join(sourceRoot, "bimg/3rdparty/stb"),
    "-DBX_CONFIG_DEBUG=0",
    "-DBIMG_CONFIG_DECODE_ASTC=0",
    "-DBGFX_CONFIG_MULTITHREADED=0",
    "-DBGFX_CONFIG_RENDERER_OPENGLES=30",
    "-DBGFX_CONFIG_RENDERER_OPENGL=0",
    "-std=c++20",
    "-include",
    "emscripten/version.h",
    "-D__EMSCRIPTEN_MAJOR__=__EMSCRIPTEN_major__",
    "-D__EMSCRIPTEN_MINOR__=__EMSCRIPTEN_minor__",
    "-D__EMSCRIPTEN_TINY__=__EMSCRIPTEN_tiny__",
    "-O2",
    "--no-entry",
    "-sMODULARIZE=1",
    "-sEXPORT_ES6=1",
    "-sEXPORT_NAME=createValthorneBgfx",
    "-sALLOW_MEMORY_GROWTH=1",
    "-sINITIAL_MEMORY=67108864",
    "-sMIN_WEBGL_VERSION=2",
    "-sMAX_WEBGL_VERSION=2",
    "-sFULL_ES3=1",
    "-sENVIRONMENT=web",
    "-sFILESYSTEM=0",
    '-sEXPORTED_FUNCTIONS=["_malloc","_free","_valthorne_command","_valthorne_font_free"]',
    '-sEXPORTED_RUNTIME_METHODS=["UTF8ToString","HEAPU8","HEAPU32","HEAPF32"]',
    "-o",
    path.join(output, "bgfx.mjs"),
];
if (process.argv.includes("--debug")) args.push("--profiling-funcs", "-sASSERTIONS=1");
run(python, [compiler, ...args]);
