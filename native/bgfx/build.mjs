import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync, copyFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const directory = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(directory, "../..");
const compiler = process.argv[2];
if (!compiler) throw new Error("Pass the extracted LLVM-MinGW 20261020 UCRT toolchain directory.");
const toolchain = path.resolve(compiler);
const sources = path.resolve(process.argv[3] || path.join(root, "build/bgfx-native-tools"));
const dependencies = [
    ["bgfx", "https://github.com/LWJGL-CI/bgfx.git", "780f7bf00a29573473b90dcfab9f92a7d8687702"],
    ["bx", "https://github.com/bkaradzic/bx.git", "f3a9adb603c5677731e7cf10622fa16e1dfe9ea5"],
    ["bimg", "https://github.com/bkaradzic/bimg.git", "9bfbd6493b48a15238f2d1374a718ec026524515"],
];
function run(command, args, cwd = root, capture = false, env = process.env) {
    const result = spawnSync(command, args, { cwd, env, stdio: capture ? "pipe" : "inherit", encoding: "utf8" });
    if (result.error) throw result.error;
    if (result.status !== 0) throw new Error(`${command} failed (${result.status}): ${result.stderr || ""}`);
    return result.stdout?.trim();
}
const suffix = process.platform === "win32" ? ".exe" : "";
const version = run(path.join(toolchain, "bin/clang++" + suffix), ["--version"], root, true);
if (!version.includes("clang version 23.1.4")) throw new Error("Use LLVM-MinGW 20261020 with Clang 23.1.4.");
mkdirSync(sources, { recursive: true });
for (const [name, url, revision] of dependencies) {
    const destination = path.join(sources, name);
    const fresh = !existsSync(path.join(destination, ".git"));
    if (fresh) run("git", ["clone", "--filter=blob:none", "--no-checkout", url, destination]);
    if (fresh || run("git", ["rev-parse", "HEAD"], destination, true) !== revision) {
        if (!fresh && run("git", ["status", "--porcelain"], destination, true)) throw new Error("Preserve local dependency changes: " + destination);
        run("git", ["fetch", url, revision, "--depth=1"], destination);
        run("git", ["checkout", "--detach", revision], destination);
    }
    if (run("git", ["status", "--porcelain"], destination, true)) throw new Error("Dependency sources must match their pinned commit: " + destination);
}

// The desktop DLL's own .git metadata identifies this bgfx revision in LWJGL 3.4.3.
// Screenshot callbacks run after submit and before presentation, so the current
// swapchain index identifies the image just rendered. Leave flip/fence code alone.
const generated = path.join(root, "build/bgfx-native/generated");
mkdirSync(generated, { recursive: true });
// Restore the NanoVG bridge included by LWJGL's shared-library build. The
// upstream revision recorded in its native metadata predates this CI patch.
const nanoDirectory = path.join(generated, "nanovg");
mkdirSync(nanoDirectory, { recursive: true });
for (const file of ["nanovg_bgfx.cpp", "nanovg_bgfx.h", "nanovg.h"])
    copyFileSync(path.join(sources, "bgfx/examples/common/nanovg", file), path.join(nanoDirectory, file));
run("git", ["apply", "--ignore-space-change", "--unidiff-zero", "--directory=build/bgfx-native/generated/nanovg", path.join(directory, "nanovg-lwjgl.patch")]);
let renderer = readFileSync(path.join(sources, "bgfx/src/renderer_d3d12.cpp"), "utf8");
const old = "uint32_t idx = (m_backBufferColorIdx-1) % m_scd.bufferCount;";
const start = renderer.indexOf("void requestScreenShot(");
const change = renderer.indexOf(old, start);
const nextMethod = renderer.indexOf("\n\t\tvoid ", start + 1);
if (start < 0 || change < start || change >= nextMethod || renderer.split(old).length !== 2)
    throw new Error("Review the pinned Direct3D 12 screenshot function before rebuilding.");
renderer = renderer.replace(old, "uint32_t idx = m_backBufferColorIdx;");
const rendererFile = path.join(generated, "renderer_d3d12.cpp");
writeFileSync(rendererFile, renderer);
// Match Vulkan's existing video-capture path: resolve multisampled swapchain
// color before reading it. Otherwise MSAA screenshots can also contain old pixels.
let vulkan = readFileSync(path.join(sources, "bgfx/src/renderer_vk.cpp"), "utf8");
const vkStart = vulkan.indexOf("void requestScreenShot(");
const vkEnd = vulkan.indexOf("\n\t\tvoid ", vkStart + 1);
let screenshot = vulkan.slice(vkStart, vkEnd);
const frameBuffer = "const FrameBufferVK& frameBuffer = isValid(_fbh)";
const readback = "readSwapChain(swapChain, stagingBuffer, stagingMemory, callback, _filePath);";
if (vkStart < 0 || vkEnd < vkStart || screenshot.split(frameBuffer).length !== 2 || screenshot.split(readback).length !== 2)
    throw new Error("Review the pinned Vulkan screenshot function before rebuilding.");
screenshot = screenshot.replace(frameBuffer, "FrameBufferVK& frameBuffer = isValid(_fbh)")
    .replace(readback, "frameBuffer.resolve();\n\t\t\t" + readback);
vulkan = vulkan.slice(0, vkStart) + screenshot + vulkan.slice(vkEnd);
const vulkanFile = path.join(generated, "renderer_vk.cpp");
writeFileSync(vulkanFile, vulkan);
let amalgamated = readFileSync(path.join(sources, "bgfx/src/amalgamated.cpp"), "utf8");
const include = '#include "renderer_d3d12.cpp"';
if (amalgamated.split(include).length !== 2) throw new Error("Review the pinned bgfx amalgamation.");
amalgamated = amalgamated.replace(include, '#include "' + rendererFile.replaceAll("\\", "/") + '"');
const vkInclude = '#include "renderer_vk.cpp"';
if (amalgamated.split(vkInclude).length !== 2) throw new Error("Review the pinned Vulkan amalgamation.");
amalgamated = amalgamated.replace(vkInclude, '#include "' + vulkanFile.replaceAll("\\", "/") + '"');
writeFileSync(path.join(generated, "amalgamated.cpp"), amalgamated);

const epoch = run("git", ["show", "-s", "--format=%ct", dependencies[0][2]], path.join(sources, "bgfx"), true);
const env = { ...process.env, SOURCE_DATE_EPOCH: epoch };
const cmake = process.env.CMAKE || "cmake";
const ninja = process.env.NINJA || (process.platform === "win32" ? path.join(sources, "bx/tools/bin/windows/ninja.exe") : "ninja");
const artifacts = {};
for (const [architecture, target, processor] of [["x64", "x86_64", "AMD64"], ["arm64", "aarch64", "ARM64"]]) {
    const output = path.join(root, "build/bgfx-native", architecture);
    run(cmake, ["-S", directory, "-B", output, "-G", "Ninja", "-DCMAKE_BUILD_TYPE=Release",
        "-DCMAKE_SYSTEM_NAME=Windows", "-DCMAKE_SYSTEM_PROCESSOR=" + processor,
        "-DCMAKE_CXX_COMPILER=" + path.join(toolchain, `bin/${target}-w64-mingw32-clang++${suffix}`),
        "-DCMAKE_MAKE_PROGRAM=" + ninja, "-DBGFX_SOURCES=" + sources.replaceAll("\\", "/"),
        "-DBGFX_GENERATED=" + generated.replaceAll("\\", "/")], root, false, env);
    run(cmake, ["--build", output, "--parallel", "6"], root, false, env);
    const exports = run(path.join(toolchain, "bin/llvm-readobj" + suffix), ["--coff-exports", path.join(output, "valthorne-bgfx.dll")], root, true).replaceAll("\r\n", "\n");
    for (const name of ["nvgCreate", "nvgDelete", "nvgSetViewId", "nvgGetViewId", "nvgluCreateFramebuffer",
        "nvgluBindFramebuffer", "nvgluDeleteFramebuffer", "nvgluSetViewFramebuffer", "nvgCreateBgfxTexture", "org_lwjgl_nanovg_setup"])
        if (!exports.includes("Name: " + name + "\n")) throw new Error("Missing NanoVG export: " + name);
    const resource = `windows/${architecture}/org/lwjgl/bgfx/valthorne-bgfx.dll`;
    const destination = path.join(root, "src/main/resources", resource);
    mkdirSync(path.dirname(destination), { recursive: true });
    copyFileSync(path.join(output, "valthorne-bgfx.dll"), destination);
    const binary = readFileSync(destination);
    artifacts[resource] = createHash("sha256").update(binary).digest("hex");
    const metadata = path.join(root, "src/main/resources/META-INF", resource);
    mkdirSync(path.dirname(metadata), { recursive: true });
    writeFileSync(metadata + ".sha1", createHash("sha1").update(binary).digest("hex"));
    writeFileSync(metadata + ".git", dependencies[0][2] + " with Valthorne current-buffer and MSAA screenshot corrections\n");
}
const licenses = path.join(root, "src/main/resources/META-INF/licenses");
mkdirSync(licenses, { recursive: true });
for (const [name, source] of [
    ["bgfx", path.join(sources, "bgfx/LICENSE")], ["bx", path.join(sources, "bx/LICENSE")],
    ["bimg", path.join(sources, "bimg/LICENSE")], ["astc-encoder", path.join(sources, "bimg/3rdparty/astc-encoder/LICENSE.txt")],
    ["llvm-mingw", path.join(toolchain, "LICENSE.TXT")],
]) writeFileSync(path.join(licenses, "bgfx-windows-" + name + ".txt"), readFileSync(source, "utf8").replaceAll("\r\n", "\n").trimEnd() + "\n");
const nanoSource = readFileSync(path.join(sources, "bgfx/examples/common/nanovg/nanovg_bgfx.cpp"), "utf8");
writeFileSync(path.join(licenses, "bgfx-windows-nanovg.txt"), nanoSource.slice(0, nanoSource.indexOf("#define NVG_ANTIALIAS")).replaceAll("\r\n", "\n").trimEnd() + "\n");
const provenance = JSON.stringify({
    lwjgl: "3.4.3", bgfxApi: 156, toolchain: "llvm-mingw-20261020-ucrt", clang: "23.1.4",
    toolchainArchiveSha256: "fd5462b7f5d93b2ff981fcd3f9c7e427e47f3203c64ea91c27bcc444eebd74cd",
    sources: Object.fromEntries(dependencies.map(([name, , revision]) => [name, revision])),
    patches: ["Direct3D 12 requestScreenShot reads m_backBufferColorIdx rather than the previous index",
        "Vulkan requestScreenShot resolves the selected framebuffer before readback",
        "NanoVG LWJGL bridge from LWJGL-CI/bgfx commit 26d1f09896f08a1b8fc8101b0752feed3e448ff5"], artifacts,
}, null, 2) + "\n";
writeFileSync(path.join(directory, "provenance.json"), provenance);
writeFileSync(path.join(root, "src/main/resources/META-INF/valthorne-bgfx.json"), provenance);
