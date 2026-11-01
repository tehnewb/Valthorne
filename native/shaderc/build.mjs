import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync, copyFileSync } from "node:fs";
import { gzipSync } from "node:zlib";
import { fileURLToPath } from "node:url";
import path from "node:path";

const directory = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(directory, "../..");
const target = process.argv[2];
const toolchain = path.resolve(process.argv[3] || ".");
const sources = path.resolve(process.argv[4] || path.join(root, "build/shaderc-sources"));
if (!["browser", "linux-arm64", "linux-arm32"].includes(target) || process.argv.length < 4)
    throw new Error("Use: node native/shaderc/build.mjs <browser|linux-arm64|linux-arm32> <Emscripten-4.0.23-or-Zig-0.15.2-directory> [dependency-directory]");

function run(command, args, options = {}) {
    const result = spawnSync(command, args, { cwd: root, stdio: "inherit", ...options });
    if (result.error) throw result.error;
    if (result.status !== 0) throw new Error(`${command} failed (${result.status})`);
}
mkdirSync(sources, { recursive: true });
for (const [name, url, revision] of [
    ["bgfx", "https://github.com/LWJGL-CI/bgfx.git", "f564ae8bf92cd2ac27d95b12408bd68be3ddf955"],
    ["bx", "https://github.com/bkaradzic/bx.git", "f3a9adb603c5677731e7cf10622fa16e1dfe9ea5"],
    ["bimg", "https://github.com/bkaradzic/bimg.git", "9bfbd6493b48a15238f2d1374a718ec026524515"],
]) {
    const checkout = path.join(sources, name);
    if (!existsSync(path.join(checkout, ".git"))) run("git", ["clone", "--filter=blob:none", "--no-checkout", url, checkout]);
    const current = spawnSync("git", ["rev-parse", "HEAD"], { cwd: checkout, encoding: "utf8" }).stdout.trim();
    if (spawnSync("git", ["status", "--porcelain"], { cwd: checkout, encoding: "utf8" }).stdout.trim())
        throw new Error("Refusing to build a modified dependency: " + checkout);
    if (current !== revision) {
        run("git", ["fetch", "origin", revision, "--depth=1"], { cwd: checkout });
        run("git", ["checkout", "--detach", revision], { cwd: checkout });
    }
}
const cmake = process.env.CMAKE || "cmake";
const ninja = process.env.NINJA || (process.platform === "win32" ? path.join(sources, "bx/tools/bin/windows/ninja.exe") : "ninja");
const build = path.join(root, "build/shaderc-" + target);
const arguments_ = ["-S", directory, "-B", build, "-G", "Ninja", "-DSOURCES=" + sources, "-DCMAKE_MAKE_PROGRAM=" + ninja, "-DCMAKE_BUILD_TYPE=Release"];
if (target === "browser") {
    if (JSON.parse(readFileSync(path.join(toolchain, "upstream/emscripten/emscripten-version.txt"), "utf8")) !== "4.0.23")
        throw new Error("Use Emscripten 4.0.23 for the pinned browser compiler");
    if (process.platform === "win32" && !process.env.EMSDK_PYTHON)
        process.env.EMSDK_PYTHON = path.join(toolchain, "python", readdirSync(path.join(toolchain, "python")).find((name) => existsSync(path.join(toolchain, "python", name, "python.exe"))), "python.exe");
    arguments_.push("-DCMAKE_TOOLCHAIN_FILE=" + path.join(toolchain, "upstream/emscripten/cmake/Modules/Platform/Emscripten.cmake"));
} else {
    const zig = path.join(toolchain, process.platform === "win32" ? "zig.exe" : "zig");
    const version = spawnSync(zig, ["version"], { encoding: "utf8" });
    if (version.status !== 0 || version.stdout.trim() !== "0.15.2") throw new Error("Use Zig 0.15.2 for the Linux ARM compilers");
    const arm64 = target === "linux-arm64";
    const flags = arm64 ? "-target aarch64-linux-musl" : "-target arm-linux-musleabihf -mcpu=cortex_a7";
    arguments_.push("-DCMAKE_SYSTEM_NAME=Linux", "-DCMAKE_SYSTEM_PROCESSOR=" + (arm64 ? "aarch64" : "arm"),
        "-DCMAKE_C_COMPILER=" + zig, "-DCMAKE_CXX_COMPILER=" + zig, "-DCMAKE_C_COMPILER_ARG1=cc", "-DCMAKE_CXX_COMPILER_ARG1=c++",
        "-DCMAKE_C_FLAGS=" + flags, "-DCMAKE_CXX_FLAGS=" + flags, "-DCMAKE_EXE_LINKER_FLAGS=-static -s");
}
run(cmake, arguments_);
run(cmake, ["--build", build, "--parallel", process.env.SHADERC_JOBS || "4"]);
const manifestPath = path.join(directory, "provenance.json");
const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");
if (target === "browser") {
    const output = path.join(root, "browser/src/main/resources/native");
    for (const file of ["shaderc.mjs", "shaderc.wasm"]) {
        const bytes = readFileSync(path.join(build, file));
        writeFileSync(path.join(output, file), file.endsWith(".mjs") ? bytes.toString("utf8").replaceAll("\r\n", "\n") : bytes);
    }
    mkdirSync(path.join(output, "shaderc-include"), { recursive: true });
    for (const header of ["bgfx_shader.sh", "bgfx_compute.sh"])
        copyFileSync(path.join(sources, "bgfx/src", header), path.join(output, "shaderc-include", header));
    manifest.browserArtifacts = Object.fromEntries(["shaderc.mjs", "shaderc.wasm"].map((file) => [file, sha256(readFileSync(path.join(output, file)))]));
} else {
    const bytes = readFileSync(path.join(build, "shaderc"));
    const archive = "prebuilt/" + target + ".gz";
    mkdirSync(path.join(directory, "prebuilt"), { recursive: true });
    writeFileSync(path.join(directory, archive), gzipSync(bytes, { level: 9 }));
    const resource = "valthorne/shaderc/linux/" + target.substring(6) + "/shaderc";
    const record = { path: resource, archive, sha256: sha256(bytes) };
    const previous = manifest.resources.findIndex((entry) => entry.path === resource);
    if (previous < 0) manifest.resources.push(record);
    else manifest.resources[previous] = record;
}
manifest.rebuild = { emscripten: "4.0.23", zig: "0.15.2", optimization: "O1", cppStandard: "C++20",
    bxRevision: "f3a9adb603c5677731e7cf10622fa16e1dfe9ea5", bimgRevision: "9bfbd6493b48a15238f2d1374a718ec026524515",
    linuxCallstack: "Use bx unwind support when musl has no execinfo.h" };
writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + "\n");
if (target === "browser") {
    const browserMetadata = { bgfxApi: manifest.bgfxApi, containerVersion: manifest.containerVersion,
        compilerVersion: manifest.compilerVersion, sourceRevision: manifest.sourceRevision,
        artifacts: manifest.browserArtifacts, rebuild: manifest.rebuild };
    writeFileSync(path.join(root, "browser/src/main/resources/META-INF/valthorne-browser-shaderc.json"), JSON.stringify(browserMetadata, null, 2) + "\n");
}
